// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.uploader.events;

import com.intellij.internal.statistic.config.StatisticsStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum ExternalSystemEventType {
  STARTED, FINISHED, SEND, ERROR;

  @Nullable
  static ExternalSystemEventType parse(@NotNull String event) {
    for (ExternalSystemEventType type : values()) {
      if (StatisticsStringUtil.equals(type.name(), event)) {
        return type;
      }
    }
    return null;
  }
}
