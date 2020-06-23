// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.uploader.events;

import com.intellij.internal.statistic.StatisticsStringUtil;
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
