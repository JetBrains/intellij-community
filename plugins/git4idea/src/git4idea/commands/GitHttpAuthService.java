/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.commands;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.git4idea.http.GitAskPassApp;
import org.jetbrains.git4idea.http.GitAskPassXmlRpcHandler;
import org.jetbrains.git4idea.ssh.GitXmlRpcHandlerService;
import org.jetbrains.git4idea.util.ScriptGenerator;

import java.util.Collection;

/**
 * Provides the authentication mechanism for Git HTTP connections.
 */
public abstract class GitHttpAuthService extends GitXmlRpcHandlerService<GitHttpAuthenticator> {

  protected GitHttpAuthService() {
    super("git-askpass-", GitAskPassXmlRpcHandler.HANDLER_NAME, GitAskPassApp.class);
  }

  @Override
  protected void customizeScriptGenerator(@NotNull ScriptGenerator generator) {
  }

  @NotNull
  @Override
  protected Object createRpcRequestHandlerDelegate() {
    return new InternalRequestHandlerDelegate();
  }

  /**
   * Creates new {@link GitHttpAuthenticator} that will be requested to handle username and password requests from Git.
   */
  @NotNull
  public abstract GitHttpAuthenticator createAuthenticator(@NotNull Project project,
                                                           @NotNull GitCommand command,
                                                           @NotNull Collection<String> urls);

  /**
   * Internal handler implementation class, it is made public to be accessible via XML RPC.
   */
  public class InternalRequestHandlerDelegate implements GitAskPassXmlRpcHandler {
    @NotNull
    @Override
    public String askUsername(int handler, @NotNull String url) {
      return getHandler(handler).askUsername(url);
    }

    @NotNull
    @Override
    public String askPassword(int handler, @NotNull String url) {
      return getHandler(handler).askPassword(url);
    }
  }

}
