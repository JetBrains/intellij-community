/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lifecycle;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ControlledAlarmFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lifecycle.ControlledAlarmFactory");
  private ControlledAlarmFactory() {
  }

  public static SlowlyClosingAlarm createOnOwnThread(@NotNull final Project project, @NotNull final String name) {
    return new SlowlyClosingAlarm(project, name);
  }

  public static SlowlyClosingAlarm createOnSharedThread(@NotNull final Project project, @NotNull final String name,
                                                        final @NotNull ExecutorService executor) {
    return new SlowlyClosingAlarm(project, name, createExecutorWrapper(executor), true);
  }

  public static ScheduledSlowlyClosingAlarm createScheduledOnSharedThread(@NotNull final Project project, @NotNull final String name,
                                                        final @NotNull ScheduledExecutorService executor) {
    return new ScheduledSlowlyClosingAlarm(project, name, new MyScheduledExecutorServiceWrapper(executor), true);
  }

  public static SlowlyClosingAlarm createOnApplicationPooledThread(@NotNull final Project project, @NotNull final String name) {
    return new SlowlyClosingAlarm(project, name, new MyApplicationPooledThreadExecutorWrapper(), true);
  }

  static MyExecutorWrapper createExecutorWrapper(final ExecutorService executorService) {
    return new MyExecutorServiceWrapper(executorService);
  }

  interface MyExecutorWrapper {
    Future<?> submit(final Runnable runnable);
    void shutdown();
    Future<?> schedule(final Runnable runnable, final long delay, final TimeUnit timeUnit);
    boolean supportsScheduling();
  }

  private static class MyExecutorServiceWrapper implements MyExecutorWrapper {
    private final ExecutorService myExecutorService;

    public MyExecutorServiceWrapper(final ExecutorService executorService) {
      myExecutorService = executorService;
    }

    public Future<?> submit(Runnable runnable) {
      return myExecutorService.submit(runnable);
    }

    public void shutdown() {
      myExecutorService.shutdown();
    }

    public Future<?> schedule(Runnable runnable, long delay, TimeUnit timeUnit) {
      throw new UnsupportedOperationException();
    }

    public boolean supportsScheduling() {
      return false;
    }
  }

  private static class MyScheduledExecutorServiceWrapper implements MyExecutorWrapper {
    private final ScheduledExecutorService myScheduledExecutorService;

    public MyScheduledExecutorServiceWrapper(final ScheduledExecutorService scheduledExecutorService) {
      myScheduledExecutorService = scheduledExecutorService;
    }

    public Future<?> submit(Runnable runnable) {
      return myScheduledExecutorService.submit(runnable);
    }

    public void shutdown() {
      myScheduledExecutorService.shutdown();
    }

    public Future<?> schedule(Runnable runnable, long delay, TimeUnit timeUnit) {
      return myScheduledExecutorService.schedule(runnable, delay, timeUnit);
    }

    public boolean supportsScheduling() {
      return true;
    }
  }

  private static class MyApplicationPooledThreadExecutorWrapper implements MyExecutorWrapper {
    private final Application myApplication;

    private MyApplicationPooledThreadExecutorWrapper() {
      myApplication = ApplicationManager.getApplication();
    }

    public Future<?> submit(Runnable runnable) {
      return myApplication.executeOnPooledThread(runnable);
    }

    public void shutdown() {
      //do not kill shared
    }

    public Future<?> schedule(final Runnable runnable, long delay, TimeUnit timeUnit) {
      throw new UnsupportedOperationException();
    }

    public boolean supportsScheduling() {
      return false;
    }
  }

  public static ProgressIndicator createProgressIndicator(final AtomicSectionsAware atomicSectionsAware) {
    return new EmptyProgressIndicator() {
      @Override
      public boolean isCanceled() {
        return atomicSectionsAware.shouldExitAsap();
      }

      @Override
      public void checkCanceled() {
        atomicSectionsAware.checkShouldExit();
      }
    };
  }
}
