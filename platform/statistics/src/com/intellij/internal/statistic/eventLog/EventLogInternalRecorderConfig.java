// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class EventLogInternalRecorderConfig implements EventLogRecorderConfig {
  private final String myRecorderId;
  private final boolean myFilterActiveFile;

  public EventLogInternalRecorderConfig(@NotNull String recorderId, boolean filterActiveFile) {
    myRecorderId = recorderId;
    myFilterActiveFile = filterActiveFile;
  }

  public EventLogInternalRecorderConfig(@NotNull String recorderId) {
    this(recorderId, true);
  }

  @NotNull
  @Override
  public String getRecorderId() {
    return myRecorderId;
  }

  @Override
  public boolean isSendEnabled() {
    return StatisticsEventLogProviderUtil.getEventLogProvider(myRecorderId).isSendEnabled();
  }

  @NotNull
  @Override
  public FilesToSendProvider getFilesToSendProvider() {
    int maxFilesToSend = EventLogConfiguration.getInstance().getOrCreate(myRecorderId).getMaxFilesToSend();
    EventLogFilesProvider logFilesProvider = StatisticsEventLogProviderUtil.getEventLogProvider(myRecorderId).getLogFilesProvider();
    return new DefaultFilesToSendProvider(logFilesProvider, maxFilesToSend, myFilterActiveFile);
  }

  @NotNull
  @Override
  public String getTemplateUrl() {
    ExternalEventLogSettings externalEventLogSettings = StatisticsEventLogProviderUtil.getExternalEventLogSettings();
    if (externalEventLogSettings != null) {
      String result = externalEventLogSettings.getTemplateUrl(myRecorderId);
      return result == null ? getDefaultTemplateUrl() : result;
    } else if (ApplicationManager.getApplication().getExtensionArea().hasExtensionPoint(EventLogEndpointSubstitutor.EP_NAME.getName())) {
      EventLogEndpointSubstitutor validSubstitutor = EventLogEndpointSubstitutor.EP_NAME.findFirstSafe(substitutor -> {
        return PluginInfoDetectorKt.getPluginInfo(substitutor.getClass()).isAllowedToInjectIntoFUS();
      });

      String result = validSubstitutor == null ? null : validSubstitutor.getTemplateUrl(myRecorderId);
      return result == null ? getDefaultTemplateUrl() : result;
    }
    return getDefaultTemplateUrl();
  }

  private static String getDefaultTemplateUrl() {
    return ((ApplicationInfoImpl)ApplicationInfoImpl.getShadowInstance()).getEventLogSettingsUrl();
  }
}
