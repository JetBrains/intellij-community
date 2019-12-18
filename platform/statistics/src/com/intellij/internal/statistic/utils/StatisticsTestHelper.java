// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils;

import com.intellij.openapi.util.text.StringUtil;

public class StatisticsTestHelper {
  private static final String ENABLE_LOCAL_STATISTICS_WITHOUT_REPORT = "idea.local.statistics.without.report";

  public static boolean isTestStatisticsEnabled() {
    return Boolean.getBoolean(ENABLE_LOCAL_STATISTICS_WITHOUT_REPORT) || StringUtil.isNotEmpty(System.getenv("TEAMCITY_VERSION"));
  }
}
