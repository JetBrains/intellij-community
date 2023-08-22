// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.runtime.deployment;

import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.runtime.log.LoggingHandler;
import com.intellij.remoteServer.runtime.log.TerminalHandler;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.OutputStream;

public interface DeploymentLogManager {
  @NotNull
  Project getProject();

  @NotNull
  LoggingHandler getMainLoggingHandler();

  @NotNull
  LoggingHandler addAdditionalLog(@NotNull String presentableName);

  void removeAdditionalLog(@NotNull String presentableName);

  boolean isTtySupported();

  @Nullable
  TerminalHandler addTerminal(@NotNull @Nls String presentableName, InputStream terminalOutput, OutputStream terminalInput);
}
