// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.runtime.deployment.debug;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

public abstract class JavaDebuggerLauncher implements DebuggerLauncher<JavaDebugConnectionData> {
  public static @NotNull JavaDebuggerLauncher getInstance() {
    return ApplicationManager.getApplication().getService(JavaDebuggerLauncher.class);
  }
}
