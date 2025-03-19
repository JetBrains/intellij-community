// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.concurrency.JobScheduler;
import com.intellij.diagnostic.PluginException;
import com.intellij.internal.statistic.eventLog.*;
import com.intellij.internal.statistic.eventLog.events.EventId;
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import kotlin.Unit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.intellij.internal.statistic.service.fus.collectors.UsageCollectors.COUNTER_EP_NAME;

/**
 * Please do not implement any new collectors using this API directly.
 * Please refer to <a href="https://youtrack.jetbrains.com/articles/IJPL-A-153/Fus-Collectors">FUS Collectors</a> and {@link EventLogGroup#registerEvent} doc comments for the new collector API.
 *
 * @see CounterUsagesCollector
 * @see ApplicationUsagesCollector
 * @see ProjectUsagesCollector
 */
@ApiStatus.Internal
@Service
public final class FUCounterUsageLogger {
  private static final int LOG_REGISTERED_DELAY_MIN = 24 * 60;
  private static final int LOG_REGISTERED_INITIAL_DELAY_MIN = StatisticsUploadAssistant.isUseTestStatisticsSendEndpoint() ? 1 : 5;

  private static final Logger LOG = Logger.getInstance(FUCounterUsageLogger.class);

  public static @NotNull FUCounterUsageLogger getInstance() {
    return ApplicationManager.getApplication().getService(FUCounterUsageLogger.class);
  }

  private final Map<String, EventLogGroup> myGroups = new HashMap<>();

  public FUCounterUsageLogger() {
    for (CounterUsageCollectorEP ep : COUNTER_EP_NAME.getExtensionList()) {
      registerGroupFromEP(ep);
    }

    ApplicationManager.getApplication().getExtensionArea().getExtensionPoint(COUNTER_EP_NAME).addExtensionPointListener(
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

  /**
   * Event log counter-system collectors aren't registered in EP,
   * so we log 'registered' event for every StatisticsEventLoggerProvider event log collector.
   *
   * @see StatisticsEventLoggerProvider#getEventLogSystemLogger$intellij_platform_statistics()
   */
  private static List<CompletableFuture<Void>> eventLogSystemCollectorsRegisteredEvents() {
    List<CompletableFuture<Void>> futures = new ArrayList<>();
    for (StatisticsEventLoggerProvider statisticsEventLoggerProvider: StatisticsEventLogProviderUtil.getEventLogProviders()) {
      EventLogGroup group = statisticsEventLoggerProvider.getEventLogSystemLogger$intellij_platform_statistics().getGroup();
      StatisticsEventLogger logger = StatisticsEventLogProviderUtil.getEventLogProvider(group.getRecorder()).getLogger();
      futures.add(logger.logAsync(group, EventLogSystemEvents.COLLECTOR_REGISTERED, false));
    }
    return futures;
  }

  public static @NotNull List<FeatureUsagesCollector> instantiateCounterCollectors() {
    List<FeatureUsagesCollector> result = new ArrayList<>(COUNTER_EP_NAME.getPoint().size());
    COUNTER_EP_NAME.processWithPluginDescriptor((ep, pluginDescriptor) -> {
      if (ep.implementationClass != null) {
        result.add(createCounterCollector(ep, pluginDescriptor));
      }
      return Unit.INSTANCE;
    });
    return result;
  }

  private static @NotNull FeatureUsagesCollector createCounterCollector(
    @NotNull CounterUsageCollectorEP ep,
    @NotNull PluginDescriptor pluginDescriptor
  ) {
    Class<Object> aClass;
    try {
      aClass = ApplicationManager.getApplication().loadClass(ep.implementationClass, pluginDescriptor);
    }
    catch (ClassNotFoundException e) {
      throw new PluginException(e, pluginDescriptor.getPluginId());
    }

    Field instanceField;
    try {
      instanceField = aClass.getDeclaredField("INSTANCE");
    }
    catch (NoSuchFieldException e) {
      return ApplicationManager.getApplication().instantiateClass(ep.implementationClass, pluginDescriptor);
    }

    instanceField.setAccessible(true);
    try {
      return (FeatureUsagesCollector)instanceField.get(null);
    }
    catch (IllegalAccessException e) {
      throw new PluginException(e, pluginDescriptor.getPluginId());
    }
  }

  private void register(@NotNull EventLogGroup group) {
    myGroups.put(group.getId(), group);
  }

  public CompletableFuture<Void> logRegisteredGroups() {
    List<CompletableFuture<Void>> futures = new ArrayList<>();
    for (EventLogGroup group : myGroups.values()) {
      futures.add(FeatureUsageLogger.getInstance().log(group, EventLogSystemEvents.COLLECTOR_REGISTERED));
    }
    futures.addAll(eventLogSystemCollectorsRegisteredEvents());
    Map<String, StatisticsEventLogger> recorderLoggers = new HashMap<>();
    for (FeatureUsagesCollector collector : instantiateCounterCollectors()) {
      EventLogGroup group = collector.getGroup();
      if (group != null) {
        String recorder = group.getRecorder();
        StatisticsEventLogger logger = recorderLoggers.get(recorder);
        if (logger == null) {
          logger = StatisticsEventLogProviderUtil.getEventLogProvider(recorder).getLogger();
          recorderLoggers.put(recorder, logger);
        }
        futures.add(logger.logAsync(group, EventLogSystemEvents.COLLECTOR_REGISTERED, false));
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
   * Records new <strong>project-wide</strong> event with context.
   * <br/><br/>
   * <i>Example:</i><br/>
   * "eventId": "action.called"<br/>
   * "data": {"id":"ShowIntentionsAction", "input_event":"Alt+Enter"}
   *
   * @param project shows in which project event was invoked, useful to separate events from two simultaneously opened projects.
   * @param groupId is used to simplify access to events, e.g. 'dialogs', 'intentions'.
   * @param eventId should be a <strong>verb</strong> because it shows which action happened, e.g. 'dialog.shown', 'project.opened'.
   * @param data    information about event context or related "items", e.g. "input_event":"Alt+Enter", "place":"MainMenu".
   * @deprecated Please use {@link EventLogGroup#registerEvent} and {@link EventId#log}
   */
  @Deprecated
  public void logEvent(@Nullable Project project,
                       @NonNls @NotNull String groupId,
                       @NonNls @NotNull String eventId,
                       @NotNull FeatureUsageData data) {
    final EventLogGroup group = findRegisteredGroupById(groupId);
    if (group != null) {
      FeatureUsageLogger.getInstance().log(group, eventId, data.addProject(project).build());
    }
  }

  /**
   * Records new <strong>application-wide</strong> event without context.
   * <br/><br/>
   * For events with context use {@link FUCounterUsageLogger#logEvent(String, String, FeatureUsageData)},
   * useful to report structured events.<br/><br/>
   *
   * <i>Example:</i><br/>
   * "eventId": "hector.clicked"
   *
   * @param groupId is used to simplify access to events, e.g. 'dialogs', 'intentions'.
   * @param eventId should be a <strong>verb</strong> because it shows which action happened, e.g. 'dialog.shown', 'project.opened'.
   * @see FUCounterUsageLogger#logEvent(String, String, FeatureUsageData)
   * @see FUCounterUsageLogger#logEvent(Project, String, String, FeatureUsageData)
   * @deprecated Please use {@link EventLogGroup#registerEvent} and {@link EventId#log}
   */
  @Deprecated(forRemoval = true)
  public void logEvent(@NonNls @NotNull String groupId,
                       @NonNls @NotNull String eventId) {
    final EventLogGroup group = findRegisteredGroupById(groupId);
    if (group != null) {
      FeatureUsageLogger.getInstance().log(group, eventId);
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
   * @param data    information about event context or related "items", e.g. "input_event":"Alt+Enter", "place":"MainMenu".
   * @see FUCounterUsageLogger#logEvent(Project, String, String, FeatureUsageData)
   * @deprecated Please use {@link EventLogGroup#registerEvent} and {@link EventId#log}
   */
  @Deprecated
  public void logEvent(@NonNls @NotNull String groupId,
                       @NonNls @NotNull String eventId,
                       @NotNull FeatureUsageData data) {
    final EventLogGroup group = findRegisteredGroupById(groupId);
    if (group != null) {
      FeatureUsageLogger.getInstance().log(group, eventId, data.build());
    }
  }

  private @Nullable EventLogGroup findRegisteredGroupById(@NotNull String groupId) {
    if (!myGroups.containsKey(groupId)) {
      LOG.error(
        "Cannot record event because group '" + groupId + "' is not registered. " +
        "To fix it add '<statistics.counterUsagesCollector groupId=\"" + groupId + "\" version=\"1\"/>' in plugin.xml");
      return null;
    }
    return myGroups.get(groupId);
  }
}
