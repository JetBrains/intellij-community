// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
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

  public EventLogInternalApplicationInfo(@NotNull String recorderId, boolean isTest) {
    myIsTest = isTest;
    myEventLogger = new DataCollectorSystemEventLogger() {
      @Override
      public void logErrorEvent(@NotNull String eventId, @NotNull Throwable exception) {
        EventLogSystemLogger.logSystemError(recorderId, eventId, exception.getClass().getName(), -1);
      }
    };
  }

  @NotNull
  @Override
  public String getTemplateUrl() {
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
  public String getUserAgent() {
    Application app = ApplicationManager.getApplication();
    if (app != null && !app.isDisposed()) {
      String productName = ApplicationNamesInfo.getInstance().getFullProductName();
      String version = ApplicationInfo.getInstance().getBuild().asStringWithoutProductCode();
      return productName + '/' + version;
    }
    return "IntelliJ";
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
