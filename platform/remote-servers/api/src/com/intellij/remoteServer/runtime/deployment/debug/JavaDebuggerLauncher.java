package com.intellij.remoteServer.runtime.deployment.debug;

import com.intellij.openapi.components.ServiceManager;

/**
 * @author nik
 */
public abstract class JavaDebuggerLauncher implements DebuggerLauncher<JavaDebugConnectionData> {
  public static JavaDebuggerLauncher getInstance() {
    return ServiceManager.getService(JavaDebuggerLauncher.class);
  }
}
