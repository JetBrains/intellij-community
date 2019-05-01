// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.concurrency.JobScheduler;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.eventLog.FeatureUsageGroup;
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class FUCounterUsageLogger {
  private static final Logger LOG = Logger.getInstance("com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger");

  private static final int LOG_REGISTERED_DELAY_MIN = 24 * 60;
  private static final int LOG_REGISTERED_INITIAL_DELAY_MIN = 5;

  /**
   * System event which indicates that the counter collector is enabled in current IDE build, can be used to calculate the base line
   */
  private static final String REGISTERED = "registered";
  private static final String[] GENERAL_GROUPS = new String[]{
    "lifecycle", "performance", "actions", "ui.dialogs", "toolwindow", "intentions", "toolbar", "run.configuration.exec",
    "file.types.usage", "productivity", "live.templates", "completion.postfix"
  };

  private static final FUCounterUsageLogger INSTANCE = new FUCounterUsageLogger();

  @NotNull
  public static FUCounterUsageLogger getInstance() {
    return INSTANCE;
  }

  private final Map<String, EventLogGroup> myGroups = new HashMap<>();

  public FUCounterUsageLogger() {
    int version = FeatureUsageLogger.INSTANCE.getConfig().getVersion();
    for (String group : GENERAL_GROUPS) {
      // platform groups which record events for all languages,
      // have the same version as a recorder to simplify further data analysis
      register(new EventLogGroup(group, version));
    }

    for (CounterUsageCollectorEP ep : CounterUsageCollectorEP.EP_NAME.getExtensionList()) {
      final String id = ep.getGroupId();
      if (StringUtil.isNotEmpty(id)) {
        register(new EventLogGroup(id, ep.version));
      }
    }

    JobScheduler.getScheduler().scheduleWithFixedDelay(
      () -> logRegisteredGroups(), LOG_REGISTERED_INITIAL_DELAY_MIN, LOG_REGISTERED_DELAY_MIN, TimeUnit.MINUTES
    );
  }

  /**
   * Don't call this method directly, register counter group in XML as
   * <statistics.counterUsagesCollector groupId="ID" version="VERSION"/>
   */
  @Deprecated
  public void register(@NotNull FeatureUsageGroup group) {
  }

  private void register(@NotNull EventLogGroup group) {
    myGroups.put(group.getId(), group);
  }

  public void logRegisteredGroups() {
    for (EventLogGroup group : myGroups.values()) {
      FeatureUsageLogger.INSTANCE.log(group, REGISTERED);
    }
  }

  /**
   * Records new event in project dependent counter with group (e.g. 'dialogs', 'intentions')
   */
  public void logEvent(@NotNull Project project,
                       @NotNull String groupId,
                       @NotNull String event) {
    final EventLogGroup group = findRegisteredGroupById(groupId);
    if (group != null) {
      final Map<String, Object> data = new FeatureUsageData().addProject(project).build();
      FeatureUsageLogger.INSTANCE.log(group, event, data);
    }
  }

  /**
   * Records new event in project dependent counter with group (e.g. 'dialogs', 'intentions').
   * Adds context information to the event, e.g. source and shortcut for an action.
   */
  public void logEvent(@NotNull Project project,
                       @NotNull String groupId,
                       @NotNull String event,
                       @NotNull FeatureUsageData data) {
    final EventLogGroup group = findRegisteredGroupById(groupId);
    if (group != null) {
      FeatureUsageLogger.INSTANCE.log(group, event, data.addProject(project).build());
    }
  }

  /**
   * Records new event in application counter with group (e.g. 'dialogs', 'intentions')
   */
  public void logEvent(@NotNull String groupId,
                       @NotNull String event) {
    final EventLogGroup group = findRegisteredGroupById(groupId);
    if (group != null) {
      FeatureUsageLogger.INSTANCE.log(group, event);
    }
  }

  /**
   * Records new event in application counter with group (e.g. 'dialogs', 'intentions').
   * Adds context information to the event, e.g. source and shortcut for an action.
   */
  public void logEvent(@NotNull String groupId,
                       @NotNull String event,
                       @NotNull FeatureUsageData data) {
    final EventLogGroup group = findRegisteredGroupById(groupId);
    if (group != null) {
      FeatureUsageLogger.INSTANCE.log(group, event, data.build());
    }
  }

  @Nullable
  private EventLogGroup findRegisteredGroupById(@NotNull String groupId) {
    if (!myGroups.containsKey(groupId)) {
      LOG.warn("Cannot record event because group '" + groupId + "' is not registered.");
      return null;
    }
    return myGroups.get(groupId);
  }
}
