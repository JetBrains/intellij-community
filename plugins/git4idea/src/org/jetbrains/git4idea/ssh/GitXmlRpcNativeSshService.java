// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.git4idea.ssh;

import com.intellij.openapi.util.NlsSafe;
import git4idea.commands.GitNativeSshAuthenticator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.git4idea.GitAppUtil;
import org.jetbrains.git4idea.nativessh.GitNativeSshAskPassApp;
import org.jetbrains.git4idea.nativessh.GitNativeSshAskPassXmlRpcHandler;

import java.util.UUID;

public final class GitXmlRpcNativeSshService extends GitXmlRpcHandlerService<GitNativeSshAuthenticator> {
  private GitXmlRpcNativeSshService() {
    super("intellij-ssh-askpass", GitNativeSshAskPassXmlRpcHandler.HANDLER_NAME, GitNativeSshAskPassApp.class);
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
    @NotNull
    @Override
    public String handleInput(@NotNull @NonNls String handlerNo, @NotNull @NlsSafe String description) {
      GitNativeSshAuthenticator g = getHandler(UUID.fromString(handlerNo));
      String answer = g.handleInput(description);
      return GitAppUtil.adjustNullTo(answer);
    }
  }
}
