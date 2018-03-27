/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.reporting;

import com.intellij.diagnostic.ThreadDumper;
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

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;

public class FreezeLoggerImpl extends FreezeLogger {
  private static final Logger LOG = Logger.getInstance(FreezeLoggerImpl.class);
  private static final Alarm ALARM = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, ApplicationManager.getApplication());
  private static final int MAX_ALLOWED_TIME = 500;

  @Override
  public void runUnderPerformanceMonitor(@Nullable Project project, @NotNull Runnable action) {
    if (!shouldReport() || isUnderDebug() || ApplicationManager.getApplication().isUnitTestMode()) {
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

  private static boolean isUnderDebug() {
    return ManagementFactory.getRuntimeMXBean().getInputArguments().toString().contains("jdwp");
  }
}

class ThreadDumpInfo {
  public final ThreadInfo[] threadInfos;
  public final String version;
  public final String product;
  public final String buildNumber;
  public final boolean isEAP;
  public final boolean isInDumbMode;

  public ThreadDumpInfo(ThreadInfo[] threadInfos, boolean isInDumbMode) {
    this.threadInfos = threadInfos;
    this.product = ApplicationNamesInfo.getInstance().getFullProductName();
    this.version = ApplicationInfo.getInstance().getFullVersion();
    this.buildNumber = ApplicationInfo.getInstance().getBuild().toString();
    this.isEAP = ApplicationManager.getApplication().isEAP();
    this.isInDumbMode = isInDumbMode;
  }
}