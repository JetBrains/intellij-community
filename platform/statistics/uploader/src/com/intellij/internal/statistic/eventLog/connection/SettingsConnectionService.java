// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.connection;

import com.intellij.internal.statistic.config.EventLogConfigParserException;
import com.intellij.internal.statistic.config.EventLogExternalSendSettings;
import com.intellij.internal.statistic.config.EventLogExternalSettings;
import com.intellij.internal.statistic.config.bean.EventLogSendConfiguration;
import com.intellij.internal.statistic.eventLog.EventLogApplicationInfo;
import com.intellij.internal.statistic.eventLog.EventLogBuildType;
import com.intellij.internal.statistic.eventLog.connection.request.StatsHttpRequests;
import com.intellij.internal.statistic.eventLog.connection.request.StatsResponseException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

public abstract class SettingsConnectionService {
  @Nullable
  private final String myConfigUrl;

  @NotNull
  private final EventLogApplicationInfo myApplicationInfo;

  @NotNull
  private final Supplier<EventLogExternalSendSettings> myCachedExternalSettings;

  protected SettingsConnectionService(@Nullable String settingsUrl, @NotNull EventLogApplicationInfo appInfo, long settingsCacheTimeoutMs) {
    myConfigUrl = settingsUrl;
    myApplicationInfo = appInfo;
    myCachedExternalSettings = new StatisticsCachingSupplier<>(
      () -> myConfigUrl != null ? loadSettings(myConfigUrl, myApplicationInfo.getProductVersion()) : null,
      settingsCacheTimeoutMs
    );
  }

  @Nullable
  protected EventLogSendConfiguration getConfiguration(@NotNull EventLogBuildType type) {
    EventLogExternalSendSettings settings = getExternalSettings();
    return settings != null ? settings.getConfiguration(type) : null;
  }

  @Nullable
  protected String getEndpointValue(@NotNull String attribute) {
    EventLogExternalSendSettings settings = getExternalSettings();
    return settings != null ? settings.getEndpoint(attribute) : null;
  }

  @Nullable
  public String getOptionValue(@NotNull String attribute) {
    EventLogExternalSendSettings settings = getExternalSettings();
    return settings != null ? settings.getOption(attribute) : null;
  }

  @Nullable
  protected synchronized EventLogExternalSendSettings getExternalSettings() {
    return myCachedExternalSettings.get();
  }

  @Nullable
  public EventLogExternalSendSettings loadSettings(@NotNull String configUrl, @NotNull String appVersion) {
    try {
      return StatsHttpRequests.request(configUrl, myApplicationInfo.getConnectionSettings()).send(r -> {
        try {
          InputStream content = r.read();
          if (content != null) {
            InputStreamReader reader = new InputStreamReader(content, StandardCharsets.UTF_8);
            return EventLogExternalSettings.parseSendSettings(reader, appVersion);
          }
          return null;
        }
        catch (EventLogConfigParserException e) {
          throw new StatsResponseException(e);
        }
      }).getResult();
    }
    catch (StatsResponseException | IOException e) {
      logError(e);
    }
    return null;
  }

  private void logError(Exception e) {
    final String message = e.getMessage();
    myApplicationInfo.getLogger().warn(message != null ? message : "", e);
    myApplicationInfo.getEventLogger().logErrorEvent("loading.config.failed", e);
  }
}