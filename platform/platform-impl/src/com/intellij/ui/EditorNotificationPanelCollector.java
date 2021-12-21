package com.intellij.ui;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class EditorNotificationPanelCollector extends CounterUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("editor.notification.panel", 2);

  private static final StringEventField KEY_FIELD = EventFields.StringValidatedByCustomRule("key", "editor_notification_panel_key");
  private static final ClassEventField CLASSNAME_FIELD = EventFields.Class("class_name");

  private static final EventId3<String, Class<?>, PluginInfo>
    ACTION_INVOKED_EVENT = GROUP.registerEvent("actionInvoked", KEY_FIELD, CLASSNAME_FIELD, EventFields.PluginInfo);
  private static final EventId2<String, PluginInfo> SHOWN_EVENT = GROUP.registerEvent("shown", KEY_FIELD, EventFields.PluginInfo);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  public static void logActionInvoked(@NotNull Project project,
                                      @NotNull String key,
                                      @NotNull Class<?> clazz,
                                      @NotNull PluginInfo pluginInfo) {
    ACTION_INVOKED_EVENT.log(project, key, clazz, pluginInfo);
  }

  public static void logShown(@NotNull Project project, @NotNull String key, @NotNull PluginInfo pluginInfo) {
    SHOWN_EVENT.log(project, key, pluginInfo);
  }
}
