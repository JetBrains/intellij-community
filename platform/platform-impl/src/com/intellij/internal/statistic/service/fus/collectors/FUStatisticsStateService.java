// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.service.fus.beans.FSContent;
import com.intellij.internal.statistic.service.fus.beans.FSGroup;
import com.intellij.internal.statistic.service.fus.beans.FSSession;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class FUStatisticsStateService implements UsagesCollectorConsumer {
  public static FUStatisticsStateService create() {
    return new FUStatisticsStateService();
  }

  /**
   * Returns data in JSON format. For collectors implementing {@link FUStatisticsDifferenceSender} the difference between the actual and
   * persisted data is included.
   */
  @Nullable
  public String getMergedDataToSend(@NotNull String actualDataFromCollectors, @NotNull Set<String> approvedGroups) {
    @NotNull FSContent allDataFromCollectors = FSContent.fromJson(actualDataFromCollectors);
    Set<String> differenceSenders = getFUStatisticsDifferenceSenders(approvedGroups);
    if (!differenceSenders.isEmpty()) {
      FSContent previousStateContent = loadContent();
      if (previousStateContent != null) {
        Set<FSSession> allDataFromCollectorsSessions = allDataFromCollectors.getSessions();
        if (allDataFromCollectorsSessions != null) {
          for (FSSession actualSession : allDataFromCollectorsSessions) {
            for (FSGroup actualGroup : getActualGroupsToMerge(differenceSenders, actualSession.getGroups())) {
              Map<String, Integer> persistedMetrics = getPersistedMetrics(getPreviousSession(previousStateContent, actualSession),
                                                                          actualGroup.id);
              if (!persistedMetrics.isEmpty()) {
                updateDifferenceSenderMetricsData(actualGroup.getMetrics(), persistedMetrics);
              }
            }
          }
        }
      }
    }
    allDataFromCollectors.removeEmptyData();
    if (allDataFromCollectors.sessions == null || allDataFromCollectors.sessions.isEmpty()) {
      return null;
    }

    return allDataFromCollectors.asJsonString();
  }

  @NotNull
  public Set<FSGroup> getActualGroupsToMerge(@NotNull Set<String> differenceSenders, @Nullable List<FSGroup> groups) {
    if (groups != null) {
      return groups.stream().filter(actualGroup -> differenceSenders.contains(actualGroup.id)).collect(Collectors.toSet());
    }
    return Collections.emptySet();
  }

  private static void updateDifferenceSenderMetricsData(@NotNull Map<String, Integer> actualMetrics,
                                                        @NotNull Map<String, Integer> persistedMetrics) {
    Set<String> keysToRemove = ContainerUtil.newHashSet();
    for (Map.Entry<String, Integer> entry : actualMetrics.entrySet()) {
      Integer persistedValue = persistedMetrics.get(entry.getKey());
      if (persistedValue != null) {
        Integer actualValue = entry.getValue();
        if (actualValue > persistedValue) {
          entry.setValue(actualValue - persistedValue);
        }
        else if (actualValue.intValue() == persistedValue.intValue()) {
          keysToRemove.add(entry.getKey());
        }
      }
    }

    if (!keysToRemove.isEmpty()) actualMetrics.keySet().removeAll(keysToRemove);
  }

  public Set<String> getFUStatisticsDifferenceSenders(@NotNull Set<String> approvedGroups) {
    Set<String> senders = ContainerUtil.newHashSet();
    for (ProjectUsagesCollector collector : ProjectUsagesCollector.getExtensions(this)) {
      if (collector instanceof FUStatisticsDifferenceSender) {
        senders.add(collector.getGroupId());
      }
    }
    for (ApplicationUsagesCollector collector : ApplicationUsagesCollector.getExtensions(this)) {
      if (collector instanceof FUStatisticsDifferenceSender) {
        senders.add(collector.getGroupId());
      }
    }
    return senders.stream().map(s -> {
      if (!approvedGroups.contains(s) && ApplicationManagerEx.getApplicationEx().isInternal()) {
        return FUStatisticsAggregator.createDebugModeId(s);
      } return s;
    }).collect(Collectors.toSet());
  }

  @Nullable
  public static FSContent loadContent() {
    String content = FUStatisticsPersistence.getPreviousStateContent();
    if (content == null) return null;
    return FSContent.fromJson(content);
  }

  @Nullable
  public static FSSession getPreviousSession(@Nullable FSContent persistedContent,
                                             @NotNull FSSession actualSession) {
    if (persistedContent == null) return null;
    Set<FSSession> sessions = persistedContent.getSessions();
    if (sessions == null) return null;
    for (FSSession previousSession : sessions) {
      if (previousSession.equals(actualSession)) return previousSession;
    }
    return null;
  }

  @NotNull
  public static Map<String, Integer> getPersistedMetrics(@Nullable FSSession persistedSession, @NotNull String groupId) {
    if (persistedSession != null) {
      List<FSGroup> persistedGroups = persistedSession.getGroups();
      if (persistedGroups != null) {
        for (FSGroup group : persistedGroups) {
          if (groupId.equals(group.id)) return group.getMetrics();
        }
      }
    }
    return Collections.emptyMap();
  }
}
