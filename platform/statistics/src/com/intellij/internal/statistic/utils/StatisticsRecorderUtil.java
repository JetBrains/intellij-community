// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.utils;

import com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.List;

public final class StatisticsRecorderUtil {
  private static final String[] BUILT_IN_RECORDERS = new String[] {"FUS", "MLSE"};
  private static final String IDEA_FUS_RECORDER_INTERNAL_MODE = "fus.internal.test.mode";
  private static final String IDEA_RECORDER_INTERNAL_MODE = "fus.recorder.internal.test.mode";

  public static boolean isBuildInRecorder(@NotNull String recorderId) {
    return ContainerUtil.find(BUILT_IN_RECORDERS, it -> it.equals(recorderId)) != null;
  }

  public static boolean isAnyTestModeEnabled() {
    if (ApplicationManager.getApplication().isInternal()) {
      return isFusInternalTestMode() || !StringUtil.isEmptyOrSpaces(System.getProperty(IDEA_RECORDER_INTERNAL_MODE));
    }
    return false;
  }

  public static @NotNull @Unmodifiable List<String> getRecordersInTestMode() {
    if (isAnyTestModeEnabled()) {
      if (isFusInternalTestMode()) {
        return ContainerUtil.map(StatisticsEventLogProviderUtil.getEventLogProviders(), it -> it.getRecorderId());
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

  public static boolean isCharsEscapingRequired(@NotNull String recorderId) {
    return StatisticsEventLogProviderUtil.getEventLogProvider(recorderId).isCharsEscapingRequired();
  }

  private static @Unmodifiable @NotNull List<String> getCustomTestModeRecorders() {
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
