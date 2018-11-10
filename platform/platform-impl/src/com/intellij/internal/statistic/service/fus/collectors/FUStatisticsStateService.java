// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.service.fus.beans.*;
import com.intellij.internal.statistic.service.fus.beans.legacy.FSLegacyContent;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class FUStatisticsStateService implements UsagesCollectorConsumer {
  public static FUStatisticsStateService create() {
    return new FUStatisticsStateService();
  }

  // some FeatureUsagesCollector can implement markup interface FUStatisticsDifferenceSender.
  // such collectors post "difference" value metrics.
  // for such collectors we persist sent data(between send sessions)
  // and merge metrics (actualValue = actualValueFromCollector - persistedValue)
  // "difference" value example: we want to know "how many times MyAction was invoked".
  //   1. my.foo.MyCollector(implements FUStatisticsDifferenceSender) calculates
  //      common invocations and returns "myAction.invokes"=N where N is total invocations count.
  //   2. first send: action was totally invoked 17 times. my.foo.MyCollector returns 17 .
  //      we send "myAction.invokes"=17
  //   3. second send: action was totally invoked 30 times. my.foo.MyCollector returns 30.
  //      action was invoked 13 times from the previous send.
  //      we send "myAction.invokes"=13
  //   4. third send: action was totally invoked 30 times. my.foo.MyCollector returns 30.
  //      action was not invoked from the previous send. we send NOTHING.
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
              Set<FSMetric> persistedMetrics = getPersistedMetrics(getPreviousSession(previousStateContent, actualSession),
                                                                          actualGroup);
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

  private static void updateDifferenceSenderMetricsData(@NotNull Set<FSMetric> actualMetrics,
                                                        @NotNull Set<FSMetric> persistedMetrics) {
    Set<FSMetric> toRemove = ContainerUtil.newHashSet();
    for (FSMetric actualMetric : actualMetrics) {
      FSMetric persistedValue = findMetric(persistedMetrics, actualMetric.id, actualMetric);
      if (persistedValue != null) {
        int actualValue = actualMetric.value;
        if (actualValue > persistedValue.value) {
          actualMetric.value = actualValue - persistedValue.value;
        }
        else if (actualValue == persistedValue.value) {
          toRemove.add(actualMetric);
        }
      }
    }

    if (!toRemove.isEmpty()) actualMetrics.removeAll(toRemove);
  }

  @Nullable
  private static FSMetric findMetric(@NotNull Set<FSMetric> persistedMetrics, @NotNull String id, @NotNull FSContextProvider contextProvider) {
    for (FSMetric metric : persistedMetrics) {
      if ( id.equals(metric.id) && Objects.equals(contextProvider.context, metric.context)) return metric;
    }
    return null;
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
    String legacyContent = FUStatisticsPersistence.getLegacyStateContent();
    if (legacyContent != null) {
      return FSLegacyContent.migrate(legacyContent);
    }
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
  public static Set<FSMetric> getPersistedMetrics(@Nullable FSSession persistedSession, @NotNull FSGroup actualGroup) {
    if (persistedSession != null) {
      List<FSGroup> persistedGroups = persistedSession.getGroups();
      if (persistedGroups != null) {
        for (FSGroup group : persistedGroups) {
          if (actualGroup.id.equals(group.id) && Objects.equals(actualGroup.context, group.context)) return group.getMetrics();
        }
      }
    }
    return Collections.emptySet();
  }
}
