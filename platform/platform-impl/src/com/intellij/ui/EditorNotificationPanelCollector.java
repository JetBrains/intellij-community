package com.intellij.ui;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId2;
import com.intellij.internal.statistic.eventLog.events.EventId3;
import com.intellij.internal.statistic.eventLog.events.StringEventField;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class EditorNotificationPanelCollector extends CounterUsagesCollector {
  public static final EventLogGroup GROUP = new EventLogGroup("editor.notification.panel", 1);

  private static final StringEventField KEY_FIELD = EventFields.StringValidatedByCustomRule("key", "editor_notification_panel_key");
  private static final StringEventField CLASSNAME_FIELD = EventFields.StringValidatedByCustomRule("class_name", "class_name");

  public static final EventId3<String, String, PluginInfo>
    ACTION_INVOKED_EVENT = GROUP.registerEvent("actionInvoked", KEY_FIELD, CLASSNAME_FIELD, EventFields.PluginInfo);
  public static final EventId2<String, PluginInfo> SHOWN_EVENT = GROUP.registerEvent("shown", KEY_FIELD, EventFields.PluginInfo);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  public static void logActionInvoked(Project project, String key, String className, @NotNull PluginInfo pluginInfo) {
    ACTION_INVOKED_EVENT.log(project, key, className, pluginInfo);
  }

  public static void logShown(Project project, String key, @NotNull PluginInfo pluginInfo) {
    SHOWN_EVENT.log(project, key, pluginInfo);
  }
}
