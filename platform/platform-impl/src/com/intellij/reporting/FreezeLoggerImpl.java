// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.reporting;

import com.intellij.diagnostic.ThreadDumper;
import com.intellij.internal.DebugAttachDetector;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.management.ThreadInfo;

public final class FreezeLoggerImpl extends FreezeLogger {
  private static final Logger LOG = Logger.getInstance(FreezeLoggerImpl.class);
  private static final Alarm ALARM = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, ApplicationManager.getApplication());
  private static final int MAX_ALLOWED_TIME = 500;

  @Override
  public void runUnderPerformanceMonitor(@Nullable Project project, @NotNull Runnable action) {
    if (!shouldReport() || DebugAttachDetector.isDebugEnabled() || ApplicationManager.getApplication().isUnitTestMode()) {
      action.run();
      return;
    }

    final ModalityState initial = ModalityState.current();
    ALARM.cancelAllRequests();
    ALARM.addRequest(() -> dumpThreads(project, initial), MAX_ALLOWED_TIME);

    try {
      action.run();
    }
    finally {
      ALARM.cancelAllRequests();
    }
  }

  private static boolean shouldReport() {
    return Registry.is("typing.freeze.report.dumps");
  }

  private static void dumpThreads(@Nullable Project project, @NotNull ModalityState initialState) {
    final ThreadInfo[] infos = ThreadDumper.getThreadInfos();
    final String edtTrace = ThreadDumper.dumpEdtStackTrace(infos);
    if (edtTrace.contains("java.lang.ClassLoader.loadClass")) {
      return;
    }

    final boolean isInDumbMode = project != null && !project.isDisposed() && DumbService.isDumb(project);

    ApplicationManager.getApplication().invokeLater(() -> {
      if (!initialState.equals(ModalityState.current())) return;
      sendDumpsInBackground(infos, isInDumbMode);
    }, ModalityState.any());
  }

  private static void sendDumpsInBackground(ThreadInfo[] infos, boolean isInDumbMode) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      ThreadDumpInfo info = new ThreadDumpInfo(infos, isInDumbMode);
      String report = ReporterKt.createReportLine("typing-freeze-dumps", "random_session_id", info);
      if (!StatsSender.INSTANCE.send(report, true)) {
        LOG.debug("Error while reporting thread dump");
      }
    });
  }
}

final class ThreadDumpInfo {
  public final ThreadInfo[] threadInfos;
  public final String version;
  public final String product;
  public final String buildNumber;
  public final boolean isEAP;
  public final boolean isInDumbMode;

  ThreadDumpInfo(ThreadInfo[] threadInfos, boolean isInDumbMode) {
    this.threadInfos = threadInfos;
    this.product = ApplicationNamesInfo.getInstance().getFullProductName();
    this.version = ApplicationInfo.getInstance().getFullVersion();
    this.buildNumber = ApplicationInfo.getInstance().getBuild().toString();
    this.isEAP = ApplicationManager.getApplication().isEAP();
    this.isInDumbMode = isInDumbMode;
  }
}