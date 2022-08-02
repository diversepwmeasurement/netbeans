/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.netbeans.modules.nbcode.integration.commands;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectInformation;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.modules.cloud.oracle.adm.ProjectVulnerability;
import org.netbeans.modules.java.lsp.server.protocol.CodeActionsProvider;
import org.netbeans.modules.java.lsp.server.protocol.NbCodeLanguageClient;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.URLMapper;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author sdedic
 */
@ServiceProvider(service = CodeActionsProvider.class)
public class ProjectAuditCommand extends CodeActionsProvider {
    /**
     * Force executes the project audit using the supplied compartment and knowledgebase IDs.
     */
    private static final String COMMAND_EXECUTE_AUDIT = "nbls.gcn.projectAudit.execute"; // NOI18N
    
    /**
     * Displays the audit from the Knowledgebase and compartment.
     */
    private static final String COMMAND_LOAD_AUDIT = "nbls.gcn.projectAudit.display"; // NOI18N
    
    public static final Set<String> COMMANDS = new HashSet<>(Arrays.asList(
            COMMAND_EXECUTE_AUDIT,
            COMMAND_LOAD_AUDIT
    ));
    
    @Override
    public List<CodeAction> getCodeActions(ResultIterator resultIterator, CodeActionParams params) throws Exception {
        return Collections.emptyList();
    }
    
    private final Gson gson = new Gson();

    @Override
    public CompletableFuture<Object> processCommand(NbCodeLanguageClient client, String command, List<Object> arguments) {
        if (arguments.size() < 3) {
            throw new IllegalArgumentException("Expected 3 parameters: resource, compartment, knowledgebase");
        }
        
        FileObject f = null;
        String s = "";
        if (arguments.get(0) instanceof JsonPrimitive) {
            s = ((JsonPrimitive)arguments.get(0)).getAsString();
            try {
                URI uri = new URI(s);
                f = URLMapper.findFileObject(uri.toURL());
            } catch (URISyntaxException | MalformedURLException ex) {
                f = FileUtil.toFileObject(new File(s));
            }
        } else {
            // accept something that looks like vscode.Uri structure
            JsonObject executeOn = gson.fromJson(gson.toJson(arguments.get(0)), JsonObject.class);
            if (executeOn.has("fsPath")) {
                s = executeOn.get("fsPath").getAsString();
                f = FileUtil.toFileObject(new File(s));
            }
        }
        if (f == null) {
            throw new IllegalArgumentException("Invalid path specified: " + s);
        }
        Project p = FileOwnerQuery.getOwner(f);
        if (p == null) {
            throw new IllegalArgumentException("Not part of a project " + s);
        }
        ProjectVulnerability v = p.getLookup().lookup(ProjectVulnerability.class);
        ProjectInformation pi = ProjectUtils.getInformation(p);
        String n = pi.getDisplayName();
        if (n == null) {
            n = pi.getName();
        }
        if (n == null) {
            n = p.getProjectDirectory().getName();
        }
        if (v == null) {
            throw new IllegalArgumentException("Project " + n + " does not support vulnerability audits");
        }
        if (arguments.size() < 3) {
            throw new IllegalArgumentException("Expected 3 parameters: resource, compartment, knowledgebase");
        }
        String compartment = ((JsonPrimitive) arguments.get(2)).getAsString();
        String knowledgeBase = ((JsonPrimitive) arguments.get(1)).getAsString();
        
        return v.findKnowledgeBase(compartment, knowledgeBase).thenCompose((kb) -> {
            if (kb == null) {
                throw new IllegalArgumentException("Unknown Knowledgebase " + knowledgeBase);
            }

            switch (command) {
                case COMMAND_EXECUTE_AUDIT:
                    v.runProjectAudit(kb, true);
                    break;
                case COMMAND_LOAD_AUDIT:
                    v.runProjectAudit(kb, false);
                    break;
                default:
                    
            }
            return CompletableFuture.completedFuture(null);
        });
    } 

    @Override
    public Set<String> getCommands() {
        return COMMANDS;
    }
}
