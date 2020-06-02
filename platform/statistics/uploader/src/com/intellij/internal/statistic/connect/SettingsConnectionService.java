// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.connect;

import com.intellij.internal.statistic.StatisticsEventLogUtil;
import com.intellij.internal.statistic.eventLog.DataCollectorDebugLogger;
import com.intellij.internal.statistic.eventLog.DataCollectorSystemEventLogger;
import com.intellij.internal.statistic.service.request.StatsHttpRequests;
import com.intellij.internal.statistic.service.request.StatsResponseException;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.intellij.internal.statistic.StatisticsStringUtil.isNotEmpty;

public abstract class SettingsConnectionService {
  protected static final String SERVICE_URL_ATTR_NAME = "url";

  private Map<String, String> myAttributesMap;

  protected String @NotNull [] getAttributeNames() {
    return new String[]{SERVICE_URL_ATTR_NAME};
  }

  @Nullable
  private final String mySettingsUrl;
  @Nullable
  private final String myDefaultServiceUrl;

  @NotNull
  private final String myUserAgent;

  @Nullable
  private final DataCollectorDebugLogger myLogger;

  @NotNull
  private final DataCollectorSystemEventLogger myEventLogger;

  protected SettingsConnectionService(@Nullable String settingsUrl,
                                      @Nullable String defaultServiceUrl,
                                      @NotNull String userAgent,
                                      @Nullable DataCollectorDebugLogger logger,
                                      @NotNull DataCollectorSystemEventLogger eventLogger) {
    mySettingsUrl = settingsUrl;
    myDefaultServiceUrl = defaultServiceUrl;
    myUserAgent = userAgent;
    myLogger = logger;
    myEventLogger = eventLogger;
  }

  @SuppressWarnings("unused")
  @Deprecated
  @Nullable
  public String getSettingsUrl() {
    return mySettingsUrl;
  }

  @Nullable
  public String getDefaultServiceUrl() {
    return myDefaultServiceUrl;
  }

  @NotNull
  private Map<String, String> readSettings(final String... attributes) {
    if (mySettingsUrl == null) return Collections.emptyMap();

    try {
      Element result = StatsHttpRequests.request(mySettingsUrl, myUserAgent).send(r -> {
        try {
          InputStream content = r.read();
          return content != null ? StatisticsEventLogUtil.parseXml(content) : null;
        }
        catch (JDOMException e) {
          throw new StatsResponseException(e);
        }
      }).getResult();

      Map<String, String> settings = new LinkedHashMap<>();
      if (result != null) {
        for (String s : attributes) {
          String attributeValue = result.getAttributeValue(s);
          if (isNotEmpty(attributeValue)) {
            settings.put(s, attributeValue);
          }
        }
      }
      return settings;
    }
    catch (StatsResponseException | IOException e) {
      logError(e);
    }
    return Collections.emptyMap();
  }

  private void logError(Exception e) {
    if (myLogger != null) {
      final String message = e.getMessage();
      myLogger.warn(message != null ? message : "", e);
    }

    myEventLogger.logErrorEvent("loading.config.failed", e);
  }

  @Nullable
  public String getServiceUrl() {
    final String serviceUrl = getSettingValue(SERVICE_URL_ATTR_NAME);
    return serviceUrl == null ? getDefaultServiceUrl() : serviceUrl;
  }

  @Nullable
  protected String getSettingValue(@NotNull String attributeValue) {
    if (myAttributesMap == null || myAttributesMap.isEmpty()) {
      myAttributesMap = readSettings(getAttributeNames());
    }
    return myAttributesMap.get(attributeValue);
  }
}