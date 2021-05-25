// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import static com.intellij.internal.statistic.eventLog.StatisticsEventLoggerKt.getEventLogProviders;

public class StatisticsRecorderUtil {
  private static final String IDEA_FUS_RECORDER_INTERNAL_MODE = "fus.internal.test.mode";
  private static final String IDEA_RECORDER_INTERNAL_MODE = "fus.recorder.internal.test.mode";

  public static boolean isAnyTestModeEnabled() {
    if (ApplicationManager.getApplication().isInternal()) {
      return isFusInternalTestMode() || !StringUtil.isEmptyOrSpaces(System.getProperty(IDEA_RECORDER_INTERNAL_MODE));
    }
    return false;
  }

  @NotNull
  public static List<String> getRecordersInTestMode() {
    if (isAnyTestModeEnabled()) {
      if (isFusInternalTestMode()) {
        return ContainerUtil.map(getEventLogProviders(), it -> it.getRecorderId());
      }

      List<String> custom = getCustomTestModeRecorders();
      if (!custom.isEmpty()) {
        return custom;
      }
    }
    return Collections.emptyList();
  }

  public static boolean isTestModeEnabled(@NotNull String recorderId) {
    if (isAnyTestModeEnabled()) {
      if (isFusInternalTestMode()) {
        return true;
      }
      return getCustomTestModeRecorders().contains(recorderId);
    }
    return false;
  }

  private static @NotNull List<String> getCustomTestModeRecorders() {
    String additional = System.getProperty(IDEA_RECORDER_INTERNAL_MODE);
    if (!StringUtil.isEmptyOrSpaces(additional)) {
      String[] split = additional.split(";");
      return ContainerUtil.mapNotNull(split, item -> StringUtil.nullize(item.trim()));
    }
    return Collections.emptyList();
  }

  private static boolean isFusInternalTestMode() {
    return Boolean.getBoolean(IDEA_FUS_RECORDER_INTERNAL_MODE);
  }
}
