// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import com.intellij.internal.statistic.StatisticsStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.internal.statistic.StatisticsStringUtil.isNotEmpty;

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
