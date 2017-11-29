/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.remoteServer.impl.runtime;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.remoteServer.runtime.RemoteOperationCallback;
import com.intellij.remoteServer.runtime.ServerTaskExecutor;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;

/**
 * @author nik
 */
public class ServerTaskExecutorImpl implements ServerTaskExecutor {
  private static final Logger LOG = Logger.getInstance(ServerTaskExecutorImpl.class);
  private final ExecutorService myTaskExecutor;

  public ServerTaskExecutorImpl() {
    myTaskExecutor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("ServerTaskExecutorImpl pool");
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
  public void submit(@NotNull final ThrowableRunnable<?> command, @NotNull final RemoteOperationCallback callback) {
    execute(() -> {
      try {
        command.run();
      }
      catch (Throwable e) {
        LOG.info(e);
        String message = e.getMessage();
        callback.errorOccurred(message != null ? message : e.getClass().getName());
      }
    });
  }
}
