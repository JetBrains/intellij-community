// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public final class EventLogOptions {
  public static final String DATA_THRESHOLD = "dataThreshold";
  public static final String GROUP_THRESHOLD = "groupDataThreshold";
  public static final String GROUP_ALERT_THRESHOLD = "groupAlertThreshold";
  public static final String MACHINE_ID_SALT = "id_salt";
  public static final String MACHINE_ID_SALT_REVISION = "id_salt_revision";
  public static final String MACHINE_ID_DISABLED = "disabled";
  public static final String MACHINE_ID_UNKNOWN = "unknown";
  public static final int DEFAULT_ID_REVISION = 0;
  private final Map<String, String> myOptions;

  public EventLogOptions(Map<String, String> options) { myOptions = options; }

  public int getThreshold() {
    return getOptionAsInt(DATA_THRESHOLD);
  }

  public int getGroupThreshold() {
    return getOptionAsInt(GROUP_THRESHOLD);
  }

  public int getGroupAlertThreshold() {
    return getOptionAsInt(GROUP_ALERT_THRESHOLD);
  }

  public int getMachineIdRevision() {
    return getOptionAsInt(MACHINE_ID_SALT_REVISION);
  }

  public String getMachineIdSalt() {
    return myOptions.get(MACHINE_ID_SALT);
  }

  private int getOptionAsInt(@NotNull String name) {
    return tryParseInt(myOptions.get(name));
  }

  public static int tryParseInt(@Nullable String value) {
    try {
      if (StatisticsStringUtil.isNotEmpty(value)) {
        return Integer.parseInt(value);
      }
    }
    catch (NumberFormatException e) {
      //ignore
    }
    return -1;
  }
}
