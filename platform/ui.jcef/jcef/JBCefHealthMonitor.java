// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.util.messages.Topic;
import org.cef.CefSettings;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

@ApiStatus.Experimental
final class JBCefHealthMonitor {
  enum Status {
    UNKNOWN,
    OK,
    UNPRIVILEGED_USER_NS_DISABLED,
    RUN_UNDER_SUPER_USER,
    GPU_PROCESS_FAILED,
  }

  private static final Logger LOG = Logger.getInstance(JBCefHealthMonitor.class);

  interface JBCefHealthCheckTopic {
    Topic<JBCefHealthCheckTopic> TOPIC = Topic.create("JBCefHealthCheckTopic", JBCefHealthCheckTopic.class);
    void onHealthHealthStatusChanged(@NotNull Status status);
  }

  private static final JBCefHealthMonitor ourInstance = new JBCefHealthMonitor();

  private final @NotNull AtomicReference<Status> myStatus = new AtomicReference<>(Status.UNKNOWN);

  static JBCefHealthMonitor getInstance() {
    return ourInstance;
  }

  @NotNull Status getStatus() {
    return myStatus.get();
  }

  boolean isReady() {
    return myStatus.get() != Status.UNKNOWN;
  }

  void performHealthCheckAsync(CefSettings settings, Runnable onHealthCheckCompleted) {
    assert getStatus() == Status.UNKNOWN; // this function shall be called only once
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      performHealthCheckImpl(settings);

      assert getStatus() != Status.UNKNOWN;
      if (getStatus() != Status.OK) {
        ApplicationManager.getApplication().getMessageBus().syncPublisher(JBCefHealthCheckTopic.TOPIC).onHealthHealthStatusChanged(getStatus());

        if (myStatus.get() == Status.UNPRIVILEGED_USER_NS_DISABLED) {
          JBCefNotifications.showAppArmorNotification();
        }
        return;
      }

      onHealthCheckCompleted.run();
    });
  }

  void onGpuProcessFailed() {
    if (myStatus.compareAndSet(Status.UNKNOWN, Status.GPU_PROCESS_FAILED)) {
      ApplicationManager.getApplication().getMessageBus().syncPublisher(JBCefHealthCheckTopic.TOPIC).onHealthHealthStatusChanged(getStatus());
    }
  }

  private void performHealthCheckImpl(CefSettings settings) {
    if (SystemInfoRt.isLinux) {
      if (!settings.no_sandbox && JBCefAppArmorUtils.areUnprivilegedUserNamespacesRestricted()) {
        myStatus.compareAndSet(Status.UNKNOWN, Status.UNPRIVILEGED_USER_NS_DISABLED);
        return;
      }

      if (isRunUnderUnixSuperuser()) {
        myStatus.compareAndSet(Status.UNKNOWN, Status.RUN_UNDER_SUPER_USER);
        return;
      }
    }

    myStatus.compareAndSet(Status.UNKNOWN, Status.OK);
  }

  private static boolean isRunUnderUnixSuperuser() {
    if (!SystemInfoRt.isUnix) {
      return false;
    }

    GeneralCommandLine commandLine = new GeneralCommandLine()
      .withExePath("id")
      .withParameters("-u");

    try {
      CapturingProcessHandler handler = new CapturingProcessHandler(commandLine);
      ProcessOutput output = handler.runProcess();
      if (output.getExitCode() != 0) {
        LOG.warn("Failed to run 'id -u': " + output.getStderr());
        return false;
      }

      if (output.getStdout().strip().equals("0")) {
        LOG.warn("The IDE is run under superuser. CEF is suspended.");
        return true;
      }

      return false;
    }
    catch (ExecutionException ex) {
      LOG.warn("Failed to check the user id: " + ex.getMessage());
      return false;
    }
  }
}
