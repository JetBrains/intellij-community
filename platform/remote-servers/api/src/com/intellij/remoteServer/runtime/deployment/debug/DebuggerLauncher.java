package com.intellij.remoteServer.runtime.deployment.debug;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.remoteServer.configuration.RemoteServer;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface DebuggerLauncher<D extends DebugConnectionData> {
  void startDebugSession(@NotNull D info, @NotNull ExecutionEnvironment executionEnvironment, @NotNull RemoteServer<?> server) throws ExecutionException;
}
