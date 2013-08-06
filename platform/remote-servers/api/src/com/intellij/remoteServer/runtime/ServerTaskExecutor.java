package com.intellij.remoteServer.runtime;

import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;

/**
 * @author nik
 */
public interface ServerTaskExecutor extends Executor {
  void submit(@NotNull Runnable command);
  void submit(@NotNull ThrowableRunnable<?> command, @NotNull RemoteOperationCallback callback);
}
