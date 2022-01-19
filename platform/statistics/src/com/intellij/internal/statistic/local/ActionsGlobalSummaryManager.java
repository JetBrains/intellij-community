// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.local;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.CharFilter;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service(Service.Level.APP)
public final class ActionsGlobalSummaryManager {
  private static final Logger LOG = Logger.getInstance(ActionsGlobalSummaryManager.class);
  private static final @NotNull CharFilter QUOTE_FILTER = ch -> ch != '"';

  private final int myUpdatedStatisticsVersion;
  private final Map<String, ActionGlobalUsageInfo> myStatisticsMap;
  private final Map<String, ActionGlobalUsageInfo> myUpdatedStatisticsMap;
  private final ActionsGlobalTotalSummary mySummary;
  private final ActionsGlobalTotalSummary myUpdatedSummary;

  public ActionsGlobalSummaryManager() {
    myUpdatedStatisticsVersion = 1;
    myStatisticsMap = loadStatistics("/statistics/actionsUsages.csv");
    myUpdatedStatisticsMap = loadStatistics("/statistics/actionsUsagesV1.csv");
    mySummary = calculateTotalSummary(myStatisticsMap);
    myUpdatedSummary = calculateTotalSummary(myUpdatedStatisticsMap);
  }

  public int getUpdatedStatisticsVersion() { return myUpdatedStatisticsVersion; }

  @Nullable
  public ActionGlobalUsageInfo getActionStatistics(String actionID) {
    return myStatisticsMap.get(actionID);
  }

  @Nullable
  public ActionGlobalUsageInfo getUpdatedActionStatistics(String actionID) {
    return myUpdatedStatisticsMap.get(actionID);
  }

  @NotNull
  public ActionsGlobalTotalSummary getTotalSummary() {
    return mySummary;
  }

  @NotNull
  public ActionsGlobalTotalSummary getUpdatedTotalSummary() {
    return myUpdatedSummary;
  }

  private final static String DEFAULT_SEPARATOR = ",";

  private static ActionsGlobalTotalSummary calculateTotalSummary(Map<String, ActionGlobalUsageInfo> statistics) {
    long maxCount = 0;
    long minCount = Long.MAX_VALUE;
    for (ActionGlobalUsageInfo value : statistics.values()) {
      long count = value.getUsagesCount();
      maxCount = Math.max(count, maxCount);
      minCount = Math.min(count, minCount);
    }
    return new ActionsGlobalTotalSummary(maxCount, minCount);
  }

  private Map<String, ActionGlobalUsageInfo> loadStatistics(String filename) {
    Map<String, ActionGlobalUsageInfo> res = new HashMap<>();
    try (InputStream stream = getClass().getResourceAsStream(filename);
         BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String line = reader.readLine();
      while (line != null) {
        String[] items = line.split(DEFAULT_SEPARATOR);

        String id = StringUtil.trim(items[0], QUOTE_FILTER);
        long users = Long.parseLong(StringUtil.trim(items[3], QUOTE_FILTER));
        long allUsers = Long.parseLong(StringUtil.trim(items[5], QUOTE_FILTER));
        long usages = Long.parseLong(StringUtil.trim(items[4], QUOTE_FILTER));
        res.put(id, new ActionGlobalUsageInfo(users, allUsers, usages));

        line = reader.readLine();
      }
    }
    catch (IOException e) {
      LOG.error("Cannot parse statistics file", e);
    }

    return res;
  }

  public static class ActionsGlobalTotalSummary {
    private final long maxUsageCount;
    private final long minUsageCount;

    public ActionsGlobalTotalSummary(long maxUsageCount, long minUsageCount) {
      this.maxUsageCount = maxUsageCount;
      this.minUsageCount = minUsageCount;
    }

    public long getMaxUsageCount() {
      return maxUsageCount;
    }

    public long getMinUsageCount() {
      return minUsageCount;
    }
  }
}
