// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.runtime.deployment.debug;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

public abstract class JavaDebuggerLauncher implements DebuggerLauncher<JavaDebugConnectionData> {
  @NotNull
  public static JavaDebuggerLauncher getInstance() {
    return ApplicationManager.getApplication().getService(JavaDebuggerLauncher.class);
  }
}
