/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.git4idea.ssh;

import com.trilead.ssh2.KnownHosts;
import git4idea.commands.GitSSHGUIHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.git4idea.util.ScriptGenerator;

import java.util.UUID;
import java.util.Vector;

/**
 * @author Kirill Likhodedov
 */
public class GitXmlRpcSshService extends GitXmlRpcHandlerService<GitSSHGUIHandler> {

  private GitXmlRpcSshService() {
    super(GitSSHHandler.GIT_SSH_PREFIX, GitSSHHandler.HANDLER_NAME, SSHMain.class);
  }

  @Override
  protected void customizeScriptGenerator(@NotNull ScriptGenerator generator) {
    generator.addClasses(KnownHosts.class);
    generator.addResource(SSHMainBundle.class, "/org/jetbrains/git4idea/ssh/SSHMainBundle.properties");
  }

  @NotNull
  @Override
  protected Object createRpcRequestHandlerDelegate() {
    return new InternalRequestHandler();
  }

  /**
   * Internal handler implementation class, do not use it.
   */
  public class InternalRequestHandler implements GitSSHHandler {

    @Override
    public boolean verifyServerHostKey(String handler, String hostname, int port, String serverHostKeyAlgorithm, String serverHostKey,
                                       boolean isNew) {
      return getHandler(UUID.fromString(handler)).verifyServerHostKey(hostname, port, serverHostKeyAlgorithm, serverHostKey, isNew);
    }

    @Override
    public String askPassphrase(String handler, String username, String keyPath, boolean resetPassword, String lastError) {
      return adjustNull(getHandler(UUID.fromString(handler)).askPassphrase(username, keyPath, resetPassword, lastError));
    }

    @Override
    @SuppressWarnings({"UseOfObsoleteCollectionType"})
    public Vector<String> replyToChallenge(String token, String username, String name, String instruction, int numPrompts,
                                           Vector<String> prompt, Vector<Boolean> echo, String lastError) {
      return adjustNull(getHandler(UUID.fromString(token)).replyToChallenge(username, name, instruction, numPrompts, prompt, echo, lastError));
    }

    @Override
    public String askPassword(String token, String username, boolean resetPassword, String lastError) {
      return adjustNull(getHandler(UUID.fromString(token)).askPassword(username, resetPassword, lastError));
    }

    @Override
    public String setLastSuccessful(String token, String userName, String method, String error) {
      getHandler(UUID.fromString(token)).setLastSuccessful(userName, method, error);
      return "";
    }

    @Override
    public String getLastSuccessful(String token, String userName) {
      return getHandler(UUID.fromString(token)).getLastSuccessful(userName);
    }

    /**
     * Adjust null value ({@code "-"} if null, {@code "+"+s} if non-null)
     *
     * @param s a value to adjust
     * @return adjusted string
     */
    private String adjustNull(final String s) {
      return s == null ? "-" : "+" + s;
    }

    /**
     * Adjust null value (returns empty array)
     *
     * @param s if null return empty array
     * @return s if not null, empty array otherwise
     */
    @SuppressWarnings({"UseOfObsoleteCollectionType"})
    private <T> Vector<T> adjustNull(final Vector<T> s) {
      return s == null ? new Vector<>() : s;
    }
  }
}
