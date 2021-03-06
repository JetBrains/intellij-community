// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl;

import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsCollectorImpl;
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsEventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventPair;
import com.intellij.internal.statistic.eventLog.events.ObjectEventData;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.intellij.internal.statistic.utils.PluginInfoDetectorKt.getPluginInfoById;
import static com.intellij.internal.statistic.utils.PluginInfoDetectorKt.getUnknownPlugin;
import static com.intellij.notification.impl.NotificationsEventLogGroup.*;

@SuppressWarnings("deprecation")
public final class NotificationCollector {
  private static final Logger LOG = Logger.getInstance(NotificationCollector.class);
  private static final Map<String, PluginInfo> ourNotificationGroupsWhitelist = new ConcurrentHashMap<>();
  private static final Set<String> ourNotificationsWhitelist = new HashSet<>();
  public static final String UNKNOWN = "unknown";

  private NotificationCollector() {
    NotificationWhitelistEP.EP_NAME.getExtensionList().forEach(NotificationCollector::addNotificationsToWhitelist);
    NotificationAllowlistEP.EP_NAME.getExtensionList().forEach(NotificationCollector::addNotificationsToWhitelist);

    ExtensionPointListener<NotificationAllowlistEP> extensionPointListener = new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull NotificationAllowlistEP extension, @NotNull PluginDescriptor pluginDescriptor) {
        addNotificationsToWhitelist(extension);
      }

      @Override
      public void extensionRemoved(@NotNull NotificationAllowlistEP extension, @NotNull PluginDescriptor pluginDescriptor) {
        removeNotificationsFromWhitelist(extension);
      }
    };
    NotificationWhitelistEP.EP_NAME.addExtensionPointListener(extensionPointListener, null);
    NotificationAllowlistEP.EP_NAME.addExtensionPointListener(extensionPointListener, null);
  }

  public void logBalloonShown(@Nullable Project project,
                              @NotNull NotificationDisplayType displayType,
                              @NotNull Notification notification,
                              boolean isExpandable) {
    List<EventPair<?>> data = createNotificationData(notification.getGroupId(), notification.id, notification.displayId);
    data.add(DISPLAY_TYPE.with(displayType));
    NotificationSeverity severity = getType(notification);
    if (severity != null) {
      data.add(SEVERITY.with(severity));
    }
    data.add(IS_EXPANDABLE.with(isExpandable));
    SHOWN.log(project, data);
  }

  public void logToolWindowNotificationShown(@Nullable Project project,
                                             @NotNull Notification notification) {
    List<EventPair<?>> data = createNotificationData(notification.getGroupId(), notification.id, notification.displayId);
    data.add(DISPLAY_TYPE.with(NotificationDisplayType.TOOL_WINDOW));
    NotificationSeverity severity = getType(notification);
    if (severity != null) {
      data.add(SEVERITY.with(severity));
    }
    SHOWN.log(project, data);
  }

  public void logNotificationLoggedInEventLog(@NotNull Project project, @NotNull Notification notification) {
    List<EventPair<?>> data = createNotificationData(notification.getGroupId(), notification.id, notification.displayId);
    NotificationSeverity severity = getType(notification);
    if (severity != null) {
      data.add(SEVERITY.with(severity));
    }
    LOGGED.log(project, data);
  }

  private static @Nullable NotificationSeverity getType(@NotNull Notification notification) {
    NotificationType type = notification.getType();
    switch (type) {
      case ERROR:
        return NotificationSeverity.ERROR;
      case WARNING:
        return NotificationSeverity.WARNING;
      case INFORMATION:
      case IDE_UPDATE:
        return NotificationSeverity.INFORMATION;
    }
    return null;
  }

  public void logNotificationBalloonClosedByUser(@Nullable Project project,
                                                 @Nullable String notificationId,
                                                 @Nullable String notificationDisplayId,
                                                 @Nullable String groupId) {
    if (notificationId == null) return;
    CLOSED_BY_USER.log(project, createNotificationData(groupId, notificationId, notificationDisplayId));
  }

  public void logNotificationActionInvoked(@Nullable Project project,
                                           @NotNull Notification notification,
                                           @NotNull AnAction action,
                                           @NotNull NotificationPlace notificationPlace) {
    List<EventPair<?>> data = createNotificationData(notification.getGroupId(), notification.id, notification.displayId);
    data.add(NOTIFICATION_PLACE.with(notificationPlace));
    if (action instanceof NotificationAction.Simple) {
      Object actionInstance = ((NotificationAction.Simple)action).getActionInstance();
      PluginInfo info = PluginInfoDetectorKt.getPluginInfo(actionInstance.getClass());
      String actionId = info.isSafeToReport() ? actionInstance.getClass().getName() : ActionsCollectorImpl.DEFAULT_ID;
      data.add(ActionsEventLogGroup.ACTION_ID.with(actionId));
    }
    else {
      ActionsCollectorImpl.addActionClass(data, action, PluginInfoDetectorKt.getPluginInfo(action.getClass()));
    }
    ACTION_INVOKED.log(project, data);
  }

  public void logHyperlinkClicked(@NotNull Notification notification) {
    HYPERLINK_CLICKED.log(createNotificationData(notification.getGroupId(), notification.id, notification.displayId));
  }

  public void logBalloonShownFromEventLog(@Nullable Project project, @NotNull Notification notification) {
    EVENT_LOG_BALLOON_SHOWN.log(project, createNotificationData(notification.getGroupId(), notification.id, notification.displayId));
  }

  public void logNotificationSettingsClicked(@NotNull String notificationId,
                                             @Nullable String notificationDisplayId,
                                             @Nullable String groupId) {
    SETTINGS_CLICKED.log(createNotificationData(groupId, notificationId, notificationDisplayId));
  }

  public void logNotificationBalloonExpanded(@Nullable Project project, @NotNull Notification notification) {
    BALLOON_EXPANDED.log(project, createNotificationData(notification.getGroupId(), notification.id, notification.displayId));
  }

  public void logNotificationBalloonCollapsed(@Nullable Project project, @NotNull Notification notification) {
    BALLOON_COLLAPSED.log(project, createNotificationData(notification.getGroupId(), notification.id, notification.displayId));
  }

  private static @NotNull List<EventPair<?>> createNotificationData(@Nullable String groupId,
                                                                    @NotNull String id,
                                                                    @Nullable String displayId) {
    ArrayList<EventPair<?>> data = new ArrayList<>();
    data.add(ID.with(id));
    data.add(ADDITIONAL.with(new ObjectEventData(NOTIFICATION_ID.with(Strings.isNotEmpty(displayId) ? displayId : UNKNOWN))));
    data.add(NOTIFICATION_GROUP_ID.with(Strings.isNotEmpty(groupId) ? groupId : UNKNOWN));
    PluginInfo pluginInfo = getPluginInfo(groupId);
    if (pluginInfo != null) {
      data.add(EventFields.PluginInfo.with(pluginInfo));
    }
    return data;
  }

  public static NotificationCollector getInstance() {
    return ApplicationManager.getApplication().getService(NotificationCollector.class);
  }

  private static void removeNotificationsFromWhitelist(@NotNull NotificationAllowlistEP extension) {
    PluginDescriptor pluginDescriptor = extension.getPluginDescriptor();
    if (pluginDescriptor == null) return;
    PluginInfo info = PluginInfoDetectorKt.getPluginInfoByDescriptor(pluginDescriptor);
    if (!info.isDevelopedByJetBrains()) return;

    List<String> notificationGroups = parseIds(extension.groupIds);
    for (String notificationGroup : notificationGroups) {
      ourNotificationGroupsWhitelist.remove(notificationGroup, info);
    }
  }

  private static @Nullable PluginInfo getPluginInfo(@Nullable String groupId) {
    if (groupId == null) return null;
    PluginInfo pluginInfo = ourNotificationGroupsWhitelist.get(groupId);
    if (pluginInfo != null) {
      return pluginInfo;
    }
    NotificationGroup group = NotificationGroup.findRegisteredGroup(groupId);
    if (group == null) return null;
    return getPluginInfoById(group.getPluginId());
  }

  private static void addNotificationsToWhitelist(@NotNull NotificationAllowlistEP extension) {
    PluginDescriptor pluginDescriptor = extension.getPluginDescriptor();
    if (pluginDescriptor == null) return;
    PluginInfo info = PluginInfoDetectorKt.getPluginInfoByDescriptor(pluginDescriptor);
    if (!info.isDevelopedByJetBrains()) return;

    List<String> notificationGroups = parseIds(extension.groupIds);
    for (String notificationGroup : notificationGroups) {
      ourNotificationGroupsWhitelist.merge(notificationGroup, info, (oldValue, newValue) -> {
        if (!oldValue.equals(newValue)) {
          LOG.warn("Notification group '" + notificationGroup + "' is already registered in whitelist");
          return getUnknownPlugin();
        }
        return oldValue;
      });
    }

    ourNotificationsWhitelist.addAll(parseIds(extension.notificationIds));
  }

  public static @NotNull List<String> parseIds(@Nullable String entry) {
    if (entry == null) {
      return Collections.emptyList();
    }

    List<String> list = new ArrayList<>();
    String[] values = StringUtil.convertLineSeparators(entry, "").split(";");
    for (String value : values) {
      if (Strings.isEmptyOrSpaces(value)) {
        continue;
      }
      list.add(value.trim());
    }
    return list;
  }

  static final class NotificationGroupValidator extends CustomValidationRule {
    @Override
    public boolean acceptRuleId(@Nullable String ruleId) {
      return "notification_group".equals(ruleId);
    }

    @Override
    protected @NotNull ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
      if (UNKNOWN.equals(data)) return ValidationResultType.ACCEPTED;
      NotificationGroup group = NotificationGroupManager.getInstance().getNotificationGroup(data);
      if (group != null && getPluginInfoById(group.getPluginId()).isDevelopedByJetBrains()) {
        return ValidationResultType.ACCEPTED;
      }
      return ourNotificationGroupsWhitelist.containsKey(data) ? ValidationResultType.ACCEPTED : ValidationResultType.REJECTED;
    }
  }

  static final class NotificationIdValidator extends CustomValidationRule {
    @Override
    public boolean acceptRuleId(@Nullable String ruleId) {
      return "notification_display_id".equals(ruleId);
    }

    @Override
    protected @NotNull ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
      if (UNKNOWN.equals(data)) {
        return ValidationResultType.ACCEPTED;
      }
      if (NotificationGroupManager.getInstance().isRegisteredNotificationId(data)) {
        return ValidationResultType.ACCEPTED;
      }
      return ourNotificationsWhitelist.contains(data) ? ValidationResultType.ACCEPTED : ValidationResultType.REJECTED;
    }
  }

  public enum NotificationPlace {
    BALLOON, EVENT_LOG, TOOL_WINDOW,
  }

  public enum NotificationSeverity {
    INFORMATION,
    WARNING,
    ERROR
  }
}
