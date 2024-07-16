// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.impl.runtime.log;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.io.OutputStream;

@ApiStatus.Internal
public abstract class CloudTerminalProvider {
  public static @NotNull CloudTerminalProvider getInstance() {
    CloudTerminalProvider contributed = ApplicationManager.getApplication().getService(CloudTerminalProvider.class);
    return contributed == null ? DummyInstanceHolder.INSTANCE : contributed;
  }

  public abstract @NotNull TerminalHandlerBase createTerminal(@NotNull @Nls String presentableName,
                                                              @NotNull Project project,
                                                              @NotNull InputStream terminalOutput,
                                                              @NotNull OutputStream terminalInput);

  public abstract boolean isTtySupported();

  private static final class DummyInstanceHolder {
    static final CloudTerminalProvider INSTANCE = new ConsoleTerminalProvider();
  }
}
