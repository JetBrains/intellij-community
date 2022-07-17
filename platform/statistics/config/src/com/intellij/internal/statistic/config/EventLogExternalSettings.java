// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.config;

import com.google.gson.GsonBuilder;
import com.intellij.internal.statistic.config.bean.EventLogBucketRange;
import com.intellij.internal.statistic.config.bean.EventLogConfigVersions;
import com.intellij.internal.statistic.config.bean.EventLogConfigVersions.EventLogConfigFilterCondition;
import com.intellij.internal.statistic.config.bean.EventLogSendConfiguration;
import com.intellij.internal.statistic.config.eventLog.EventLogBuildType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyMap;

public class EventLogExternalSettings {
  public static final EventLogExternalSendSettings EMPTY = new EventLogExternalSendSettings(emptyMap(), emptyMap(), emptyMap());

  public String productCode;
  public List<EventLogConfigVersions> versions;

  @NotNull
  public static EventLogExternalSendSettings parseSendSettings(@NotNull Reader reader, @NotNull String version)
    throws EventLogConfigParserException {
    try {
      final EventLogExternalSettings parsed = new GsonBuilder().create().fromJson(reader, EventLogExternalSettings.class);
      if (parsed != null) {
        return parsed.toSendSettings(version);
      }
    }
    catch (Exception e) {
      throw new EventLogConfigParserException(e);
    }
    return EMPTY;
  }


  @NotNull
  private EventLogExternalSendSettings toSendSettings(@NotNull String productVersion) {
    EventLogConfigVersions version = findMajorVersion(productVersion);
    if (version == null) {
      return EMPTY;
    }

    Map<EventLogBuildType, EventLogSendConfiguration> configurations = new EnumMap<>(EventLogBuildType.class);
    for (EventLogConfigFilterCondition filter : version.getFilters()) {
      EventLogBuildType[] types = EventLogBuildType.getTypesByString(filter.releaseType);
      for (EventLogBuildType type : types) {
        EventLogBucketRange range = filter.getBucketRange();
        if (range != null) {
          if (!configurations.containsKey(type)) {
            configurations.put(type, new EventLogSendConfiguration(new ArrayList<>()));
          }
          configurations.get(type).addBucketRange(range);
        }
      }
    }
    return new EventLogExternalSendSettings(version.getEndpoints(), version.getOptions(), configurations);
  }

  @Nullable
  private EventLogConfigVersions findMajorVersion(@NotNull String productVersion) {
    if (versions == null || versions.isEmpty()) {
      return null;
    }
    return versions.stream()
      .filter(v -> v.majorBuildVersionBorders != null && v.majorBuildVersionBorders.accept(productVersion))
      .findFirst().orElse(null);
  }
}
