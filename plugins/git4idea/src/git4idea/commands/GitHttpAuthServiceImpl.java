// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands;

import com.intellij.externalProcessAuthHelper.AuthenticationGate;
import com.intellij.externalProcessAuthHelper.AuthenticationMode;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;

class GitHttpAuthServiceImpl extends GitHttpAuthService {

  @Override
  public @NotNull GitHttpGuiAuthenticator createAuthenticator(@NotNull Project project,
                                                              @NotNull Collection<String> urls,
                                                              @NotNull File workingDirectory,
                                                              @NotNull AuthenticationGate authenticationGate,
                                                              @NotNull AuthenticationMode authenticationMode) {
    return new GitHttpGuiAuthenticator(project, urls, workingDirectory, authenticationGate, authenticationMode);
  }
}
