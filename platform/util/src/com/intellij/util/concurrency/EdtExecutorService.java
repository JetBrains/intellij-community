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
package com.intellij.util.concurrency;

import com.intellij.util.ui.EdtInvocationManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ExecutorService implementation which delegates tasks for execution to the SwingUtilities.invokeLater(task)
 */
public class EdtExecutorService extends AbstractExecutorService {
  @NotNull
  public static ExecutorService getInstance() {
    return INSTANCE;
  }

  @NotNull
  public static ScheduledExecutorService getScheduledExecutorInstance() {
    return SCHEDULED_INSTANCE;
  }

  @Override
  public void execute(@NotNull Runnable command) {
    EdtInvocationManager.getInstance().invokeLater(command);
  }

  @Override
  public void shutdown() {
    AppScheduledExecutorService.error();
  }

  @NotNull
  @Override
  public List<Runnable> shutdownNow() {
    return AppScheduledExecutorService.error();
  }

  @Override
  public boolean isShutdown() {
    return false;
  }

  @Override
  public boolean isTerminated() {
    return false;
  }

  @Override
  public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) throws InterruptedException {
    AppScheduledExecutorService.error();
    return false;
  }

  private static final ExecutorService INSTANCE = new EdtExecutorService();
  private static final ScheduledExecutorService SCHEDULED_INSTANCE = new SchedulingWrapper(INSTANCE, ((AppScheduledExecutorService)AppExecutorUtil.getAppScheduledExecutorService()).delayQueue){
    @NotNull
    @Override
    public List<Runnable> shutdownNow() {
      return AppScheduledExecutorService.error();
    }

    @Override
    public void shutdown() {
      AppScheduledExecutorService.error();
    }

    @Override
    public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) throws InterruptedException {
      AppScheduledExecutorService.error();
      return false;
    }
  };
}
