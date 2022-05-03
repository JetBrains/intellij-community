// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil;
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider;
import com.intellij.internal.statistic.eventLog.uploader.EventLogExternalUploader;
import com.intellij.internal.statistic.utils.StatisticsRecorderUtil;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

final class EventLogApplicationLifecycleListener implements AppLifecycleListener {
  @Override
  public void appWillBeClosed(boolean isRestart) {
    if (!isRestart && !PluginManagerCore.isRunningFromSources() && isSendingOnExitEnabled()) {
      List<StatisticsEventLoggerProvider> enabledBuiltInLoggerProviders = getEnabledLoggerProviders();
      if (!enabledBuiltInLoggerProviders.isEmpty() && !isUpdateInProgress()) {
        ProgressManager.getInstance().run(new Task.Modal(null, "Starting External Log Uploader", false) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            List<String> recorderIds = ContainerUtil.map(enabledBuiltInLoggerProviders, recorder -> recorder.getRecorderId());
            EventLogExternalUploader.INSTANCE.startExternalUpload(recorderIds, StatisticsUploadAssistant.isUseTestStatisticsConfig());
          }
        });
      }
    }
  }

  @NotNull
  private static List<StatisticsEventLoggerProvider> getEnabledLoggerProviders() {
    List<StatisticsEventLoggerProvider> providers = new ArrayList<>();
    for (String recorderId : StatisticsRecorderUtil.BUILT_IN_RECORDERS) {
      StatisticsEventLoggerProvider provider = StatisticsEventLogProviderUtil.getEventLogProvider(recorderId);
      if (provider.isSendEnabled()) {
        providers.add(provider);
      }
    }
    return providers;
  }

  private static boolean isSendingOnExitEnabled() {
    return Registry.is("feature.usage.event.log.send.on.ide.close");
  }

  private static boolean isUpdateInProgress() {
    return ApplicationInfo.getInstance().getBuild().asString().
      equals(PropertiesComponent.getInstance().getValue("ide.self.update.started.for.build"));
  }
}
