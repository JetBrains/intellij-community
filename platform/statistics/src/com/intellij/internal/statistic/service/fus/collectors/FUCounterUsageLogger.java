// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.concurrency.JobScheduler;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.EventLogSystemEvents;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.eventLog.events.EventId;
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Please do not implement any new collectors using this API directly.
 * Please refer to "fus-collectors.md" dev-guide and {@link EventLogGroup#registerEvent} doc comments for the new collector API.
 *
 * @see CounterUsagesCollector
 * @see ApplicationUsagesCollector
 * @see ProjectUsagesCollector
 */
@ApiStatus.Internal
public final class FUCounterUsageLogger {
  private static final ExtensionPointName<CounterUsageCollectorEP> EP_NAME =
    new ExtensionPointName<>("com.intellij.statistics.counterUsagesCollector");

  private static final int LOG_REGISTERED_DELAY_MIN = 24 * 60;
  private static final int LOG_REGISTERED_INITIAL_DELAY_MIN = 5;

  private static final Logger LOG = Logger.getInstance(FUCounterUsageLogger.class);

  private static final FUCounterUsageLogger INSTANCE = new FUCounterUsageLogger();

  @NotNull
  public static FUCounterUsageLogger getInstance() {
    return INSTANCE;
  }

  private final Map<String, EventLogGroup> myGroups = new HashMap<>();

  public FUCounterUsageLogger() {
    for (CounterUsageCollectorEP ep : EP_NAME.getExtensionList()) {
      registerGroupFromEP(ep);
    }
    ApplicationManager.getApplication().getExtensionArea().getExtensionPoint(EP_NAME).addExtensionPointListener(
      new ExtensionPointListener<>() {
        @Override
        public void extensionAdded(@NotNull CounterUsageCollectorEP extension, @NotNull PluginDescriptor pluginDescriptor) {
          registerGroupFromEP(extension);
        }

        // Not unregistering groups when a plugin is unloaded is harmless
      }, true, null);

    JobScheduler.getScheduler().scheduleWithFixedDelay(
      () -> logRegisteredGroups(), LOG_REGISTERED_INITIAL_DELAY_MIN, LOG_REGISTERED_DELAY_MIN, TimeUnit.MINUTES
    );
  }

  private void registerGroupFromEP(CounterUsageCollectorEP ep) {
    if (ep.implementationClass == null) {
      final String id = ep.getGroupId();
      if (StringUtil.isNotEmpty(id)) {
        register(new EventLogGroup(id, ep.version));
      }
    }
  }

  public static @NotNull List<FeatureUsagesCollector> instantiateCounterCollectors() {
    return instantiateCounterCollectors(null);
  }

  public static @NotNull List<FeatureUsagesCollector> instantiateCounterCollectors(String pluginId) {
    List<FeatureUsagesCollector> result = new ArrayList<>(EP_NAME.getPoint().size());
    EP_NAME.processWithPluginDescriptor((ep, pluginDescriptor) -> {
      if (pluginId == null || pluginId.equals(pluginDescriptor.getPluginId().getIdString())) {
        if (ep.implementationClass != null) {
          result.add(ApplicationManager.getApplication().instantiateClass(ep.implementationClass, pluginDescriptor));
        }
      }
    });
    return result;
  }

  private void register(@NotNull EventLogGroup group) {
    myGroups.put(group.getId(), group);
  }

  public CompletableFuture<Void> logRegisteredGroups() {
    List<CompletableFuture<Void>> futures = new ArrayList<>();
    for (EventLogGroup group : myGroups.values()) {
      futures.add(FeatureUsageLogger.INSTANCE.log(group, EventLogSystemEvents.COLLECTOR_REGISTERED));
    }
    for (FeatureUsagesCollector collector : instantiateCounterCollectors()) {
      EventLogGroup group = collector.getGroup();
      if (group != null) {
        futures.add(FeatureUsageLogger.INSTANCE.log(group, EventLogSystemEvents.COLLECTOR_REGISTERED));
      }
      else {
        try {
          // get group id to check that either group or group id is overridden
          if (StringUtil.isEmpty(collector.getGroupId())) {
            LOG.error("Please override either getGroupId() or getGroup() with not empty string in " + collector.getClass().getName());
          }
        }
        catch (IllegalStateException e) {
          LOG.error(e.getMessage() + " in " + collector.getClass().getName());
        }
      }
    }
    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
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
   * @deprecated Please use {@link EventLogGroup#registerEvent} and {@link EventId#log}
   */
  @Deprecated(forRemoval = true)
  public void logEvent(@Nullable Project project,
                       @NonNls @NotNull String groupId,
                       @NonNls @NotNull String eventId) {
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
   * @deprecated Please use {@link EventLogGroup#registerEvent} and {@link EventId#log}
   */
  @Deprecated
  public void logEvent(@Nullable Project project,
                       @NonNls @NotNull String groupId,
                       @NonNls @NotNull String eventId,
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
   * @deprecated Please use {@link EventLogGroup#registerEvent} and {@link EventId#log}
   */
  @Deprecated(forRemoval = true)
  public void logEvent(@NonNls @NotNull String groupId,
                       @NonNls @NotNull String eventId) {
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
   * @deprecated Please use {@link EventLogGroup#registerEvent} and {@link EventId#log}
   */
  @Deprecated
  public void logEvent(@NonNls @NotNull String groupId,
                       @NonNls @NotNull String eventId,
                       @NotNull FeatureUsageData data) {
    final EventLogGroup group = findRegisteredGroupById(groupId);
    if (group != null) {
      FeatureUsageLogger.INSTANCE.log(group, eventId, data.build());
    }
  }

  @Nullable
  private EventLogGroup findRegisteredGroupById(@NotNull String groupId) {
    if (!myGroups.containsKey(groupId)) {
      LOG.error(
        "Cannot record event because group '" + groupId + "' is not registered. " +
        "To fix it add '<statistics.counterUsagesCollector groupId=\"" + groupId + "\" version=\"1\"/>' in plugin.xml");
      return null;
    }
    return myGroups.get(groupId);
  }
}
