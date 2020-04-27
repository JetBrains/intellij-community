// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.uploader.events;

import com.intellij.internal.statistic.StatisticsEventLogUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum ExternalUploadEventType {
  STARTED, FINISHED, SEND;

  @Nullable
  static ExternalUploadEventType parse(@NotNull String event) {
    for (ExternalUploadEventType type : values()) {
      if (StatisticsEventLogUtil.equals(type.name(), event)) {
        return type;
      }
    }
    return null;
  }
}
