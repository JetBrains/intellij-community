// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider;
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger;
import com.intellij.internal.statistic.eventLog.uploader.EventLogExternalUploader;
import com.intellij.openapi.application.ApplicationInfo;

public class EventLogApplicationLifecycleListener implements AppLifecycleListener {

  @Override
  public void appWillBeClosed(boolean isRestart) {
    if (!isRestart && !PluginManagerCore.isRunningFromSources()) {
      StatisticsEventLoggerProvider config = FeatureUsageLogger.INSTANCE.getConfig();
      if (config.isSendEnabled()) {
        boolean isUpdateInProgress = isUpdateInProgress();
        EventLogExternalUploader.INSTANCE.startExternalUpload(config.getRecorderId(), false, isUpdateInProgress);
      }
    }
  }

  private static boolean isUpdateInProgress() {
    return ApplicationInfo.getInstance().getBuild().asString().
      equals(PropertiesComponent.getInstance().getValue("ide.self.update.started.for.build"));
  }
}
