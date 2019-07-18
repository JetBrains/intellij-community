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
    "lifecycle", "performance", "actions", "ui.dialogs", "ui.settings",
    "toolwindow", "intentions", "toolbar", "run.configuration.exec",
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
   * Records new <strong>project-wide</strong> event without context.
   * <br/><br/>
   * For events with context use {@link FUCounterUsageLogger#logEvent(Project, String, String, FeatureUsageData)},
   * useful to report structured events.<br/><br/>
   * <i>Example:</i><br/>
   * "eventId": "tooltip.shown"
   *
   * @param project shows in which project event was invoked, useful to separate events from two simultaneously opened projects.
   * @param groupId is used to simplify access to events, e.g. 'dialogs', 'intentions'.
   * @param eventId should be a <strong>verb</strong> because it shows which action happened, e.g. 'dialog.shown', 'project.opened'.
   *
   * @see FUCounterUsageLogger#logEvent(Project, String, String, FeatureUsageData)
   */
  public void logEvent(@NotNull Project project,
                       @NotNull String groupId,
                       @NotNull String eventId) {
    final EventLogGroup group = findRegisteredGroupById(groupId);
    if (group != null) {
      final Map<String, Object> data = new FeatureUsageData().addProject(project).build();
      FeatureUsageLogger.INSTANCE.log(group, eventId, data);
    }
  }

  /**
   * Records new <strong>project-wide</strong> event with context.
   * <br/><br/>
   * <i>Example:</i><br/>
   * "eventId": "action.called"<br/>
   * "data": {"id":"ShowIntentionsAction", "input_event":"Alt+Enter"}
   *
   * @param project shows in which project event was invoked, useful to separate events from two simultaneously opened projects.
   * @param groupId is used to simplify access to events, e.g. 'dialogs', 'intentions'.
   * @param eventId should be a <strong>verb</strong> because it shows which action happened, e.g. 'dialog.shown', 'project.opened'.
   * @param data information about event context or related "items", e.g. "input_event":"Alt+Enter", "place":"MainMenu".
   */
  public void logEvent(@NotNull Project project,
                       @NotNull String groupId,
                       @NotNull String eventId,
                       @NotNull FeatureUsageData data) {
    final EventLogGroup group = findRegisteredGroupById(groupId);
    if (group != null) {
      FeatureUsageLogger.INSTANCE.log(group, eventId, data.addProject(project).build());
    }
  }

  /**
   * Records new <strong>application-wide</strong> event without context.
   * <br/><br/>
   * For events with context use {@link FUCounterUsageLogger#logEvent(String, String, FeatureUsageData)},
   * useful to report structured events.<br/>
   * For project-wide events use {@link FUCounterUsageLogger#logEvent(Project, String, String)},
   * useful to separate events from two simultaneously opened projects.<br/><br/>
   *
   * <i>Example:</i><br/>
   * "eventId": "hector.clicked"
   *
   * @param groupId is used to simplify access to events, e.g. 'dialogs', 'intentions'.
   * @param eventId should be a <strong>verb</strong> because it shows which action happened, e.g. 'dialog.shown', 'project.opened'.
   *
   * @see FUCounterUsageLogger#logEvent(String, String, FeatureUsageData)
   * @see FUCounterUsageLogger#logEvent(Project, String, String, FeatureUsageData)
   */
  public void logEvent(@NotNull String groupId,
                       @NotNull String eventId) {
    final EventLogGroup group = findRegisteredGroupById(groupId);
    if (group != null) {
      FeatureUsageLogger.INSTANCE.log(group, eventId);
    }
  }

  /**
   * Records new <strong>application-wide</strong> event with context information.
   * <br/><br/>
   * For project-wide events use {@link FUCounterUsageLogger#logEvent(Project, String, String, FeatureUsageData)},
   * useful to separate events from two simultaneously opened projects.<br/><br/>
   * <i>Example:</i><br/>
   * "eventId": "ide.started"<br/>
   * "data": {"eap":true, "internal":false}
   *
   * @param groupId is used to simplify access to events, e.g. 'dialogs', 'intentions'.
   * @param eventId should be a <strong>verb</strong> because it shows which action happened, e.g. 'dialog.shown', 'project.opened'.
   * @param data information about event context or related "items", e.g. "input_event":"Alt+Enter", "place":"MainMenu".
   *
   * @see FUCounterUsageLogger#logEvent(Project, String, String, FeatureUsageData)
   */
  public void logEvent(@NotNull String groupId,
                       @NotNull String eventId,
                       @NotNull FeatureUsageData data) {
    final EventLogGroup group = findRegisteredGroupById(groupId);
    if (group != null) {
      FeatureUsageLogger.INSTANCE.log(group, eventId, data.build());
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
