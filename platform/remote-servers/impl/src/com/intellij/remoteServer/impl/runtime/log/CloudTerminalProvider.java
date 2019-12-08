// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.runtime.log;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.io.OutputStream;

@ApiStatus.Internal
public abstract class CloudTerminalProvider {

  public static CloudTerminalProvider getInstance() {
    return ServiceManager.getService(CloudTerminalProvider.class);
  }

  public TerminalHandlerBase createTerminal(@NotNull String presentableName,
                                            @NotNull Project project,
                                            @NotNull InputStream terminalOutput,
                                            @NotNull OutputStream terminalInput) {

    return createTerminal(presentableName, project, terminalOutput, terminalInput, false);
  }

  public abstract TerminalHandlerBase createTerminal(@NotNull String presentableName,
                                                     @NotNull Project project,
                                                     @NotNull InputStream terminalOutput,
                                                     @NotNull OutputStream terminalInput,
                                                     boolean deferTerminalSessionUntilFirstShown);

  public abstract boolean isTtySupported();
}
