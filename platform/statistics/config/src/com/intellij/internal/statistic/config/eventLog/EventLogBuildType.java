// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.config.eventLog;

import com.intellij.internal.statistic.config.StatisticsStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.internal.statistic.config.StatisticsStringUtil.isNotEmpty;

public enum EventLogBuildType {
  EAP("eap"), RELEASE("release"), UNKNOWN("unknown");

  private static final String ALL = "all";
  public final String text;

  EventLogBuildType(String text) {
    this.text = text;
  }

  public static EventLogBuildType @NotNull [] getTypesByString(@Nullable String type) {
    if (isNotEmpty(type)) {
      String unifiedType = StatisticsStringUtil.toLowerCase(type);
      if (ALL.equals(unifiedType)) {
        return values();
      }

      for (EventLogBuildType value : values()) {
        if (unifiedType.equals(value.text)) {
          return new EventLogBuildType[]{value};
        }
      }
    }
    return new EventLogBuildType[]{};
  }
}
