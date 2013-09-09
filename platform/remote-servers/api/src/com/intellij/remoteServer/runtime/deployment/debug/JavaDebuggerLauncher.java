package com.intellij.remoteServer.runtime.deployment.debug;

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class JavaDebuggerLauncher implements DebuggerLauncher<JavaDebugConnectionData> {
  @NotNull
  public static JavaDebuggerLauncher getInstance() {
    return ServiceManager.getService(JavaDebuggerLauncher.class);
  }
}
