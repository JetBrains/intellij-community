// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.config;

import com.intellij.internal.statistic.eventLog.DataCollectorDebugLogger;
import com.intellij.internal.statistic.eventLog.DataCollectorSystemEventLogger;
import com.intellij.internal.statistic.eventLog.EventLogApplicationInfo;
import com.jetbrains.fus.reporting.model.http.StatsBasicConnectionSettings;
import com.jetbrains.fus.reporting.model.http.StatsConnectionSettings;
import com.jetbrains.fus.reporting.model.http.StatsProxyInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.Proxy;
import java.util.Map;

/**
 * External version of the EventLogApplicationInfo, which is provided to an 'external' uploader process (not IDEA) via arguments
 */
public class EventLogExternalApplicationInfo implements EventLogApplicationInfo {
  private final DataCollectorDebugLogger myLogger;
  private final DataCollectorSystemEventLogger myEventLogger;

  private final String myRegionalCode;
  private final String myProductCode;
  private final String myProductVersion;
  private final int myBaselineVersion;
  private final StatsBasicConnectionSettings myConnectionSettings;

  private final boolean myIsInternal;
  private final boolean myIsTestConfig;
  private final boolean myIsTestSendEndpoint;

  private final boolean myIsEAP;

  public EventLogExternalApplicationInfo(@NotNull String regionalCode, @NotNull String productCode,
                                         @NotNull String productVersion, @Nullable String userAgent,
                                         boolean isInternal, boolean isTestConfig, boolean isTestSendEndpoint, boolean isEAP,
                                         @NotNull Map<String, String> extraHeaders,
                                         @NotNull DataCollectorDebugLogger logger,
                                         @NotNull DataCollectorSystemEventLogger eventLogger,
                                         int baselineVersion) {
    myRegionalCode = regionalCode;
    myProductCode = productCode;
    myProductVersion = productVersion;
    myBaselineVersion = baselineVersion;
    String externalUserAgent = (userAgent == null ? "IntelliJ" : userAgent) + "(External)";
    myConnectionSettings = new StatsBasicConnectionSettings(externalUserAgent,
                                                            extraHeaders,
                                                            null,
                                                            new StatsProxyInfo(Proxy.NO_PROXY, null));
    myIsInternal = isInternal;
    myIsTestConfig = isTestConfig;
    myIsTestSendEndpoint = isTestSendEndpoint;
    myIsEAP = isEAP;
    myLogger = logger;
    myEventLogger = eventLogger;
  }

  @Override
  public @NotNull String getRegionalCode() {
    return myRegionalCode;
  }

  @Override
  public @NotNull String getProductCode() {
    return myProductCode;
  }

  @Override
  public @NotNull String getProductVersion() {
    return myProductVersion;
  }

  @Override
  public int getBaselineVersion() {
    return myBaselineVersion;
  }

  @Override
  public @NotNull StatsConnectionSettings getConnectionSettings() {
    return myConnectionSettings;
  }

  @Override
  public boolean isInternal() {
    return myIsInternal;
  }

  @Override
  public boolean isTestConfig() {
    return myIsTestConfig;
  }

  @Override
  public boolean isTestSendEndpoint() {
    return myIsTestSendEndpoint;
  }

  @Override
  public boolean isEAP() {
    return myIsEAP;
  }

  @Override
  public @NotNull DataCollectorDebugLogger getLogger() {
    return myLogger;
  }

  @Override
  public @NotNull DataCollectorSystemEventLogger getEventLogger() {
    return myEventLogger;
  }
}
