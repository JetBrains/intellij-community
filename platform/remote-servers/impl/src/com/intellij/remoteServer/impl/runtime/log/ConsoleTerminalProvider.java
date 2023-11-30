// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.runtime.log;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.io.OutputStream;

class ConsoleTerminalProvider extends CloudTerminalProvider {

  @Override
  public @NotNull TerminalHandlerBase createTerminal(@NotNull String presentableName,
                                            @NotNull Project project,
                                            @NotNull InputStream terminalOutput,
                                            @NotNull OutputStream terminalInput) {
    return new ConsoleTerminalHandlerImpl(presentableName, project, terminalOutput, terminalInput);
  }

  @Override
  public boolean isTtySupported() {
    return false;
  }
}
