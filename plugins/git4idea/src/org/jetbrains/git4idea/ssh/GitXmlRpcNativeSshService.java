// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.git4idea.ssh;

import git4idea.commands.GitNativeSshAuthenticator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.git4idea.nativessh.GitNativeSshAskPassApp;
import org.jetbrains.git4idea.nativessh.GitNativeSshAskPassXmlRpcHandler;
import org.jetbrains.git4idea.util.ScriptGenerator;

import java.util.UUID;

public class GitXmlRpcNativeSshService extends GitXmlRpcHandlerService<GitNativeSshAuthenticator> {
  private GitXmlRpcNativeSshService() {
    super("intellij-ssh-askpass", GitNativeSshAskPassXmlRpcHandler.HANDLER_NAME, GitNativeSshAskPassApp.class);
  }

  @Override
  protected void customizeScriptGenerator(@NotNull ScriptGenerator generator) {
  }

  @NotNull
  @Override
  protected Object createRpcRequestHandlerDelegate() {
    return new InternalRequestHandler();
  }

  /**
   * Internal handler implementation class, do not use it.
   */
  public class InternalRequestHandler implements GitNativeSshAskPassXmlRpcHandler {
    @Nullable
    @Override
    public String askPassphrase(String handler, @NotNull String description) {
      return adjustNull(getHandler(UUID.fromString(handler)).askPassphrase(description));
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
  }
}
