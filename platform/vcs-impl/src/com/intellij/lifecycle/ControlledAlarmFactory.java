package com.intellij.lifecycle;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
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

  public static SlowlyClosingAlarm createOnApplicationPooledThread(@NotNull final Project project) {
    return new SlowlyClosingAlarm(project, "Application pooled thread controlled alarm", new MyApplicationPooledThreadExecutorWrapper(), true);
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
    private Application myApplication;

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
}
