// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.config;

import com.intellij.internal.statistic.eventLog.DataCollectorDebugLogger;
import com.intellij.internal.statistic.eventLog.DataCollectorSystemEventLogger;
import com.intellij.internal.statistic.eventLog.EventLogApplicationInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EventLogExternalApplicationInfo implements EventLogApplicationInfo {
  private final DataCollectorDebugLogger myLogger;
  private final DataCollectorSystemEventLogger myEventLogger;

  private final String myTemplateUrl;
  private final String myProductCode;
  private final String myProductVersion;
  private final String myUserAgent;

  private final boolean myIsInternal;
  private final boolean myIsTest;
  private final boolean myIsEAP;

  public EventLogExternalApplicationInfo(@NotNull String templateUrl, @NotNull String productCode,
                                         @NotNull String productVersion, @Nullable String userAgent,
                                         boolean isInternal, boolean isTest, boolean isEAP,
                                         @NotNull DataCollectorDebugLogger logger,
                                         @NotNull DataCollectorSystemEventLogger eventLogger) {
    myTemplateUrl = templateUrl;
    myProductCode = productCode;
    myProductVersion = productVersion;
    myUserAgent = (userAgent == null ? "IntelliJ": userAgent) + "(External)";
    myIsInternal = isInternal;
    myIsTest = isTest;
    myIsEAP = isEAP;
    myLogger = logger;
    myEventLogger = eventLogger;
  }

  @NotNull
  @Override
  public String getTemplateUrl() {
    return myTemplateUrl;
  }

  @NotNull
  @Override
  public String getProductCode() {
    return myProductCode;
  }

  @Override
  public @NotNull String getProductVersion() {
    return myProductVersion;
  }

  @NotNull
  @Override
  public String getUserAgent() {
    return myUserAgent;
  }

  @Override
  public boolean isInternal() {
    return myIsInternal;
  }

  @Override
  public boolean isTest() {
    return myIsTest;
  }

  @Override
  public boolean isEAP() {
    return myIsEAP;
  }

  @NotNull
  @Override
  public DataCollectorDebugLogger getLogger() {
    return myLogger;
  }

  @Override
  public @NotNull DataCollectorSystemEventLogger getEventLogger() {
    return myEventLogger;
  }
}
