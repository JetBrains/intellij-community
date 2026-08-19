// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.messages.Topic;
import org.cef.CefApp;
import org.cef.CefSettings;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

@ApiStatus.Experimental
public final class JBCefHealthMonitor {
  public enum Status {
    UNKNOWN,
    OK,
    UNPRIVILEGED_USER_NS_DISABLED,
    RUN_UNDER_SUPER_USER,
    GPU_PROCESS_FAILED,
    STARTUP_TEST_FAILED,
    CEF_SERVER_DISCONNECTED
  }

  private static final Logger LOG = Logger.getInstance(JBCefHealthMonitor.class);
  private static final int GPUCrashLimit = Integer.getInteger("ide.browser.jcef.gpu.infinitecrash.internallimit", 3);

  public interface JBCefHealthCheckTopic {
    Topic<JBCefHealthCheckTopic> TOPIC = Topic.create("JBCefHealthCheckTopic", JBCefHealthCheckTopic.class);
    void onHealthHealthStatusChanged(@NotNull Status status);
  }

  private static final JBCefHealthMonitor ourInstance = new JBCefHealthMonitor();

  private final @NotNull AtomicReference<Status> myStatus = new AtomicReference<>(Status.UNKNOWN);
  private @Nullable JBCefApp.JcefStarter myJcefStarter = null;
  private @Nullable InternalJcefTest myInternalJcefTest = null;
  private boolean myTrackGPUCrashes = false;
  private int myGPUCrashCounter = 0;
  private int myCefServerCrashCounter = 0;

  public static JBCefHealthMonitor getInstance() {
    return ourInstance;
  }

  void configure(@Nullable JBCefApp.JcefStarter jcefStarter, boolean trackGPUCrashes) {
    myTrackGPUCrashes = trackGPUCrashes;
    myJcefStarter = jcefStarter;
  }

  public @NotNull Status getStatus() {
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

  void performStartupTestAsync(boolean isRemoteEnabled) {
    myStatus.set(Status.OK);
    myGPUCrashCounter = 0;

    if (!isRemoteEnabled)
      return;

    myInternalJcefTest = new InternalJcefTest();
    myInternalJcefTest.setOnFailed(errText -> {
      if (myJcefStarter != null)
        myJcefStarter.onInternalJcefTestFailed();

      JBCefNotifications.showInternalJcefTestFailed(errText, myJcefStarter, myGPUCrashCounter, myCefServerCrashCounter);

      if (myStatus.compareAndSet(Status.OK, Status.STARTUP_TEST_FAILED)) {
        ApplicationManager.getApplication().getMessageBus().syncPublisher(JBCefHealthCheckTopic.TOPIC).onHealthHealthStatusChanged(getStatus());
      }
    });
    myInternalJcefTest.setOnSuccess(()-> {
      if (myJcefStarter != null)
        myJcefStarter.onInternalJcefTestOk();
    });
    myInternalJcefTest.start();
  }

  void onGpuProcessFailed() {
    ++myGPUCrashCounter;

    if (myInternalJcefTest != null && !myInternalJcefTest.isTestFinished()) // Do nothing (all possible actions will be performed at the end of InternalJcefTest)
      return;

    if (!myTrackGPUCrashes)
      return;

    if (myGPUCrashCounter < GPUCrashLimit)
      return;

    JBCefNotifications.showGPUCrashes(myJcefStarter);

    if (myStatus.compareAndSet(Status.OK, Status.GPU_PROCESS_FAILED)) {
      ApplicationManager.getApplication().getMessageBus().syncPublisher(JBCefHealthCheckTopic.TOPIC).onHealthHealthStatusChanged(Status.GPU_PROCESS_FAILED);
    }
  }

  void setupDisconnectionNotification(@NotNull CefApp cefApp) {
    cefApp.setDisconnectionCallback(()->{
      ++myCefServerCrashCounter;

      if (myInternalJcefTest != null && !myInternalJcefTest.isTestFinished()) // Do nothing (all possible actions will be performed at the end of InternalJcefTest)
        return;

      cefApp.dispose();
      CefApp.setDefaultInstance(null);

      final Application app = ApplicationManager.getApplication();
      app.executeOnPooledThread(() -> {
        LOG.warn("JCEF process was disconnected.");
        Path flog = SettingsHelper.findCrashStacktrace();
        if (flog != null) {
          String content = null;
          try {
            content = Files.readString(flog);
          } catch (IOException e) {
            LOG.info("Can't read crash stacktrace file '" + flog + "'.");
          } catch (InvalidPathException e) {
            LOG.info("Invalid path '" + flog + "'.");
          }
          LOG.info("JCEF crash stacktrace was saved to file '" + flog.toAbsolutePath() + "'. Stacktrace:\n" + content);
        }

        JBCefNotifications.showDisconnection(myJcefStarter);

        if (myStatus.compareAndSet(Status.OK, Status.CEF_SERVER_DISCONNECTED)) {
          ApplicationManager.getApplication().getMessageBus().syncPublisher(JBCefHealthCheckTopic.TOPIC).onHealthHealthStatusChanged(Status.CEF_SERVER_DISCONNECTED);
        }
      });
    });
  }

  private void performHealthCheckImpl(CefSettings settings) {
    if (SystemInfoRt.isLinux) {
      if (!settings.no_sandbox && JBCefAppArmorUtils.areUnprivilegedUserNamespacesRestricted()) {
        myStatus.compareAndSet(Status.UNKNOWN, Status.UNPRIVILEGED_USER_NS_DISABLED);
        return;
      }

      if (isRunUnderUnixSuperuser() && !Registry.is("ide.browser.jcef.run-under-superuser.allowed")) {
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
        LOG.warn("The IDE is run under superuser.");
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
