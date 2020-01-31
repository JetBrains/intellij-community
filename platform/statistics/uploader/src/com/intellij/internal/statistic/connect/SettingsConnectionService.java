// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.connect;

import com.intellij.internal.statistic.StatisticsEventLogUtil;
import com.intellij.internal.statistic.eventLog.DataCollectorDebugLogger;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpGet;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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

  protected SettingsConnectionService(@Nullable String settingsUrl, @Nullable String defaultServiceUrl,
                                      @NotNull String userAgent, @Nullable DataCollectorDebugLogger logger) {
    mySettingsUrl = settingsUrl;
    myDefaultServiceUrl = defaultServiceUrl;
    myUserAgent = userAgent;
    myLogger = logger;
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
      HttpEntity entity = StatisticsEventLogUtil.create(myUserAgent).execute(new HttpGet(mySettingsUrl)).getEntity();
      InputStream content = entity != null ? entity.getContent() : null;
      if (content != null) {
        Map<String, String> settings = new LinkedHashMap<>();
        try {
          Element root = StatisticsEventLogUtil.parseXml(content);
          for (String s : attributes) {
            String attributeValue = root.getAttributeValue(s);
            if (StatisticsEventLogUtil.isNotEmpty(attributeValue)) {
              settings.put(s, attributeValue);
            }
          }
        }
        catch (JDOMException e) {
          if (myLogger != null) {
            final String message = e.getMessage();
            myLogger.info(message != null ? message : "");
          }
        }
        return settings;
      }
    }
    catch (Exception e) {
      if (myLogger != null) {
        final String message = e.getMessage();
        myLogger.warn(message != null ? message : "", e);
      }
    }
    return Collections.emptyMap();
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