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
package org.jetbrains.git4idea.ssh;

import com.trilead.ssh2.KnownHosts;
import git4idea.commands.GitSSHGUIHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.git4idea.util.ScriptGenerator;

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
    public boolean verifyServerHostKey(int handler, String hostname, int port, String serverHostKeyAlgorithm, String serverHostKey,
                                       boolean isNew) {
      return getHandler(handler).verifyServerHostKey(hostname, port, serverHostKeyAlgorithm, serverHostKey, isNew);
    }

    @Override
    public String askPassphrase(int handler, String username, String keyPath, boolean resetPassword, String lastError) {
      return adjustNull(getHandler(handler).askPassphrase(username, keyPath, resetPassword, lastError));
    }

    @Override
    @SuppressWarnings({"UseOfObsoleteCollectionType"})
    public Vector<String> replyToChallenge(int handlerNo, String username, String name, String instruction, int numPrompts,
                                           Vector<String> prompt, Vector<Boolean> echo, String lastError) {
      return adjustNull(getHandler(handlerNo).replyToChallenge(username, name, instruction, numPrompts, prompt, echo, lastError));
    }

    @Override
    public String askPassword(int handlerNo, String username, boolean resetPassword, String lastError) {
      return adjustNull(getHandler(handlerNo).askPassword(username, resetPassword, lastError));
    }

    @Override
    public String setLastSuccessful(int handlerNo, String userName, String method, String error) {
      getHandler(handlerNo).setLastSuccessful(userName, method, error);
      return "";
    }

    @Override
    public String getLastSuccessful(int handlerNo, String userName) {
      return getHandler(handlerNo).getLastSuccessful(userName);
    }

    /**
     * Adjust null value ({@code "-"} if null, {@code "+"+s) if non-null)
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
      return s == null ? new Vector<T>() : s;
    }
  }
}
