// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.impl.runtime;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.remoteServer.runtime.RemoteOperationCallback;
import com.intellij.remoteServer.runtime.ServerTaskExecutor;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.ExecutorService;

public class ServerTaskExecutorImpl implements ServerTaskExecutor {
  private static final Logger LOG = Logger.getInstance(ServerTaskExecutorImpl.class);
  private final ExecutorService myTaskExecutor;

  public ServerTaskExecutorImpl() {
    myTaskExecutor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("ServerTaskExecutorImpl Pool");
  }

  @Override
  public void execute(@NotNull Runnable command) {
    myTaskExecutor.execute(command);
  }

  @Override
  public void submit(@NotNull Runnable command) {
    execute(command);
  }

  @Override
  public void submit(final @NotNull ThrowableRunnable<?> command, final @NotNull RemoteOperationCallback callback) {
    execute(() -> {
      try {
        command.run();
      }
      catch (Throwable e) {
        LOG.info(e);
        @NlsSafe String message = Optional.ofNullable(e.getMessage()).orElseGet(() -> e.getClass().getName());
        callback.errorOccurred(message);
      }
    });
  }
}
