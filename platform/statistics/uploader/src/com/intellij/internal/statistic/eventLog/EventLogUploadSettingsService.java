// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import com.intellij.internal.statistic.config.EventLogExternalSendSettings;
import com.intellij.internal.statistic.config.bean.EventLogSendConfiguration;
import com.intellij.internal.statistic.connect.SettingsConnectionService;
import com.intellij.internal.statistic.eventLog.filters.*;
import com.intellij.internal.statistic.service.fus.StatisticsWhitelistConditions;
import com.intellij.internal.statistic.service.fus.StatisticsWhitelistLoader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class EventLogUploadSettingsService extends SettingsConnectionService implements EventLogSettingsService {
  private static final String SEND = "send";
  private static final String WHITELIST = "whitelist";
  private static final String DICTIONARY = "dictionary";

  @NotNull
  private final EventLogApplicationInfo myApplicationInfo;

  public EventLogUploadSettingsService(@NotNull String recorderId, @NotNull EventLogApplicationInfo appInfo) {
    super(getConfigUrl(recorderId, appInfo.getProductCode(), appInfo.getTemplateUrl(), appInfo.isTest()), appInfo);
    myApplicationInfo = appInfo;
  }

  @NotNull
  private static String getConfigUrl(@NotNull String recorderId, @NotNull String productCode, @NotNull String templateUrl, boolean isTest) {
    return String.format(templateUrl, recorderId, productCode);
  }

  @Nullable
  @Override
  public String getServiceUrl() {
    return getEndpointValue(SEND);
  }

  @Override
  @Nullable
  public String getDictionaryServiceUrl() {
    return getEndpointValue(DICTIONARY);
  }

  @Override
  public boolean isSettingsReachable() {
    return getExternalSettings() != null;
  }

  @Override
  public boolean isSendEnabled() {
    final EventLogExternalSendSettings settings = getExternalSettings();
    return settings != null && settings.isSendEnabled();
  }

  @Override
  @NotNull
  public LogEventFilter getBaseEventFilter() {
    return new LogEventWhitelistFilter(notNull(getWhitelistedGroups(), StatisticsWhitelistConditions.empty()));
  }

  @Override
  @NotNull
  public LogEventFilter getEventFilter(@NotNull LogEventFilter base, @NotNull EventLogBuildType type) {
    final EventLogSendConfiguration configuration = getConfiguration(type);
    if (configuration == null) {
      DataCollectorDebugLogger logger = myApplicationInfo.getLogger();
      if (logger.isTraceEnabled()) {
        logger.trace("Cannot find send configuration for '" + type + "' -> clean up log file");
      }
      return LogEventFalseFilter.INSTANCE;
    }

    return new LogEventCompositeFilter(
      new LogEventBucketsFilter(configuration.getBuckets()),
      base, LogEventSnapshotBuildFilter.INSTANCE
    );
  }

  private static StatisticsWhitelistConditions notNull(@Nullable StatisticsWhitelistConditions whitelist, @NotNull StatisticsWhitelistConditions defaultValue) {
    return whitelist != null ? whitelist : defaultValue;
  }

  @Override
  public @NotNull EventLogApplicationInfo getApplicationInfo() {
    return myApplicationInfo;
  }

  @Nullable
  protected StatisticsWhitelistConditions getWhitelistedGroups() {
    final String productUrl = getWhiteListProductUrl();
    if (productUrl == null) return null;
    String userAgent = myApplicationInfo.getUserAgent();
    return StatisticsWhitelistLoader.getApprovedGroups(productUrl, userAgent);
  }

  @NonNls
  @Nullable
  public String getWhiteListProductUrl() {
    String baseWhitelistUrl = getEndpointValue(WHITELIST);
    if (baseWhitelistUrl == null) return null;
    return baseWhitelistUrl + myApplicationInfo.getProductCode() + ".json";
  }
}
