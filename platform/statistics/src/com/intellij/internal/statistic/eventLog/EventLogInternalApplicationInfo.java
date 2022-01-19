// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog;

import com.intellij.internal.statistic.eventLog.connection.EventLogConnectionSettings;
import com.intellij.internal.statistic.eventLog.connection.EventLogStatisticsService;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class EventLogInternalApplicationInfo implements EventLogApplicationInfo {
  private static final DataCollectorDebugLogger LOG =
    new InternalDataCollectorDebugLogger(Logger.getInstance(EventLogStatisticsService.class));

  private final boolean myIsTest;
  private final DataCollectorSystemEventLogger myEventLogger;
  private final EventLogAppConnectionSettings myConnectionSettings;
  private final String myRecorderId;

  public EventLogInternalApplicationInfo(@NotNull String recorderId, boolean isTest) {
    myIsTest = isTest;
    myRecorderId = recorderId;
    myConnectionSettings = new EventLogAppConnectionSettings();
    myEventLogger = new DataCollectorSystemEventLogger() {
      @Override
      public void logErrorEvent(@NotNull String eventId, @NotNull Throwable exception) {
        EventLogSystemLogger.logSystemError(recorderId, eventId, exception.getClass().getName(), -1);
      }
    };
  }

  @NotNull
  @Override
  @SuppressWarnings("deprecation") // remove together with EventLogEndpointSubstitutor
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

  @NotNull
  @Override
  public String getProductCode() {
    return ApplicationInfo.getInstance().getBuild().getProductCode();
  }

  @Override
  public @NotNull String getProductVersion() {
    final ApplicationInfo info = ApplicationInfo.getInstance();
    return info.getMajorVersion() + "." + info.getMinorVersion();
  }

  @NotNull
  @Override
  public EventLogConnectionSettings getConnectionSettings() {
    return myConnectionSettings;
  }

  @Override
  public boolean isInternal() {
    return StatisticsUploadAssistant.isTestStatisticsEnabled();
  }

  @Override
  public boolean isTest() {
    return myIsTest;
  }

  @Override
  public boolean isEAP() {
    return ApplicationManager.getApplication().isEAP();
  }

  @NotNull
  @Override
  public DataCollectorDebugLogger getLogger() {
    return LOG;
  }

  @Override
  public @NotNull DataCollectorSystemEventLogger getEventLogger() {
    return myEventLogger;
  }
}
