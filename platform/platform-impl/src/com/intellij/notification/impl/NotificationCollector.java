// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl;

import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsCollectorImpl;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomWhiteListRule;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.internal.statistic.utils.PluginInfoDetectorKt.getPluginInfoById;
import static com.intellij.internal.statistic.utils.PluginInfoDetectorKt.getUnknownPlugin;

public class NotificationCollector {
  private static final Logger LOG = Logger.getInstance(NotificationCollector.class);
  private static final AtomicReference<NotificationsWhitelist> ourNotificationsWhitelist = new AtomicReference<>();
  private static final String NOTIFICATIONS = "notifications";
  private static final String UNKNOWN = "unknown";
  private static final String NOTIFICATION_GROUP = "notification_group";

  private NotificationCollector() {
    ourNotificationsWhitelist.set(createNotificationsWhitelist());
    NotificationWhitelistEP.EP_NAME.addExtensionPointListener(new ExtensionPointListener<NotificationWhitelistEP>() {
      @Override
      public void extensionAdded(@NotNull NotificationWhitelistEP extension, @NotNull PluginDescriptor pluginDescriptor) {
        ourNotificationsWhitelist.set(createNotificationsWhitelist());
      }

      @Override
      public void extensionRemoved(@NotNull NotificationWhitelistEP extension, @NotNull PluginDescriptor pluginDescriptor) {
        ourNotificationsWhitelist.set(createNotificationsWhitelist());
      }
    }, ApplicationManager.getApplication());
  }

  private static NotificationsWhitelist createNotificationsWhitelist() {
    HashMap<String, PluginInfo> notificationGroups = new HashMap<>();
    Set<String> notificationIds = new HashSet<>();
    for (NotificationWhitelistEP extension : NotificationWhitelistEP.EP_NAME.getExtensionList()) {
      PluginDescriptor pluginDescriptor = extension.getPluginDescriptor();
      if (pluginDescriptor == null) continue;
      PluginInfo info = PluginInfoDetectorKt.getPluginInfoByDescriptor(pluginDescriptor);
      if (!info.isDevelopedByJetBrains()) continue;

      List<String> groups = parseIds(extension.groupIds);
      for (String notificationGroup : groups) {
        notificationGroups.merge(notificationGroup, info, (oldValue, newValue) -> {
          if (!oldValue.equals(newValue)) {
            LOG.warn("Notification group '" + notificationGroup + "' is already registered in whitelist");
            return getUnknownPlugin();
          }
          return oldValue;
        });
      }

      notificationIds.addAll(parseIds(extension.notificationIds));
    }
    return new NotificationsWhitelist(notificationGroups, notificationIds);
  }

  public void logBalloonShown(@Nullable Project project,
                              @NotNull NotificationDisplayType displayType,
                              @NotNull Notification notification,
                              boolean isExpandable) {
    FeatureUsageData data = createNotificationData(notification.getGroupId(), notification.id, notification.displayId)
      .addData("display_type", displayType.name())
      .addData("severity", notification.getType().name())
      .addData("is_expandable", isExpandable);
    FUCounterUsageLogger.getInstance().logEvent(project, NOTIFICATIONS, "shown", data);
  }

  public void logToolWindowNotificationShown(@Nullable Project project,
                                             @NotNull Notification notification) {
    FeatureUsageData data = createNotificationData(notification.getGroupId(), notification.id, notification.displayId)
      .addData("display_type", NotificationDisplayType.TOOL_WINDOW.name())
      .addData("severity", notification.getType().name());
    FUCounterUsageLogger.getInstance().logEvent(project, NOTIFICATIONS, "shown", data);
  }

  public void logNotificationLoggedInEventLog(@NotNull Project project, @NotNull Notification notification) {
    FeatureUsageData data = createNotificationData(notification.getGroupId(), notification.id, notification.displayId)
      .addData("severity", notification.getType().name());
    FUCounterUsageLogger.getInstance().logEvent(project, NOTIFICATIONS, "logged", data);
  }

  public void logNotificationBalloonClosedByUser(@Nullable String notificationId, @Nullable String notificationDisplayId, @Nullable String groupId) {
    if (notificationId == null) return;
    FeatureUsageData data = createNotificationData(groupId, notificationId, notificationDisplayId);
    FUCounterUsageLogger.getInstance().logEvent(NOTIFICATIONS, "closed.by.user", data);
  }

  public void logNotificationActionInvoked(@NotNull Notification notification,
                                           @NotNull AnAction action,
                                           @NotNull NotificationPlace notificationPlace) {
    FeatureUsageData data = createNotificationData(notification.getGroupId(), notification.id, notification.displayId)
      .addData("notification_place", notificationPlace.name());
    ActionsCollectorImpl.addActionClass(data, action, PluginInfoDetectorKt.getPluginInfo(action.getClass()));
    FUCounterUsageLogger.getInstance().logEvent(NOTIFICATIONS, "action.invoked", data);
  }

  public void logHyperlinkClicked(@NotNull Notification notification) {
    FeatureUsageData data = createNotificationData(notification.getGroupId(), notification.id, notification.displayId);
    FUCounterUsageLogger.getInstance().logEvent(NOTIFICATIONS, "hyperlink.clicked", data);
  }

  public void logBalloonShownFromEventLog(@NotNull Notification notification) {
    FeatureUsageData data = createNotificationData(notification.getGroupId(), notification.id, notification.displayId);
    FUCounterUsageLogger.getInstance().logEvent(NOTIFICATIONS, "event.log.balloon.shown", data);
  }

  public void logNotificationSettingsClicked(@NotNull String notificationId, @Nullable String notificationDisplayId, @Nullable String groupId) {
    FeatureUsageData data = createNotificationData(groupId, notificationId, notificationDisplayId);
    FUCounterUsageLogger.getInstance().logEvent(NOTIFICATIONS, "settings.clicked", data);
  }

  public void logNotificationBalloonExpanded(@NotNull Notification notification) {
    FeatureUsageData data = createNotificationData(notification.getGroupId(), notification.id, notification.displayId);
    FUCounterUsageLogger.getInstance().logEvent(NOTIFICATIONS, "balloon.expanded", data);
  }

  public void logNotificationBalloonCollapsed(@NotNull Notification notification) {
    FeatureUsageData data = createNotificationData(notification.getGroupId(), notification.id, notification.displayId);
    FUCounterUsageLogger.getInstance().logEvent(NOTIFICATIONS, "balloon.collapsed", data);
  }

  @NotNull
  private static FeatureUsageData createNotificationData(@Nullable String groupId, @NotNull String id, @Nullable String displayId) {
    return new FeatureUsageData()
      .addData("id", id)
      .addData("display_id", StringUtil.isNotEmpty(displayId) ? displayId : UNKNOWN)
      .addData(NOTIFICATION_GROUP, StringUtil.isNotEmpty(groupId) ? groupId : UNKNOWN)
      .addPluginInfo(getPluginInfo(groupId));
  }

  public static NotificationCollector getInstance() {
    return ServiceManager.getService(NotificationCollector.class);
  }

  @NotNull
  private static PluginInfo getPluginInfo(@Nullable String groupId) {
    if (groupId == null) return getUnknownPlugin();
    PluginInfo pluginInfo = ourNotificationsWhitelist.get().getPluginInfo(groupId);
    if (pluginInfo != null) {
      return pluginInfo;
    }
    NotificationGroup group = NotificationGroup.findRegisteredGroup(groupId);
    if (group == null) return getUnknownPlugin();
    return getPluginInfoById(group.getPluginId());
  }

  @NotNull
  private static List<String> parseIds(@Nullable String entry) {
    if (entry == null) return Collections.emptyList();
    List<String> list = new ArrayList<>();
    String[] values = StringUtil.convertLineSeparators(entry, "").split(";");
    for (String value : values) {
      if (StringUtil.isEmptyOrSpaces(value)) continue;
      list.add(StringUtil.trim(value));
    }
    return list;
  }

  public static class NotificationGroupValidator extends CustomWhiteListRule {

    @Override
    public boolean acceptRuleId(@Nullable String ruleId) {
      return NOTIFICATION_GROUP.equals(ruleId);
    }

    @NotNull
    @Override
    protected ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
      if (UNKNOWN.equals(data)) return ValidationResultType.ACCEPTED;
      return ourNotificationsWhitelist.get().isAllowedNotificationGroups(data)
             ? ValidationResultType.ACCEPTED
             : ValidationResultType.REJECTED;
    }
  }

  public static class NotificationIdValidator extends CustomWhiteListRule {

    @Override
    public boolean acceptRuleId(@Nullable String ruleId) {
      return "notification_display_id".equals(ruleId);
    }

    @NotNull
    @Override
    protected ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
      if (UNKNOWN.equals(data)) return ValidationResultType.ACCEPTED;
      return ourNotificationsWhitelist.get().isAllowedNotificationId(data) ? ValidationResultType.ACCEPTED : ValidationResultType.REJECTED;
    }
  }

  public enum NotificationPlace {
    BALLOON, EVENT_LOG
  }

  private static class NotificationsWhitelist {
    private final Map<String, PluginInfo> myNotificationGroups;
    private final Set<String> myNotificationIds;

    private NotificationsWhitelist(Map<String, PluginInfo> notificationGroups, Set<String> notificationIds) {
      myNotificationGroups = notificationGroups;
      myNotificationIds = notificationIds;
    }

    public boolean isAllowedNotificationGroups(@NotNull String notificationGroup) {
      return myNotificationGroups.containsKey(notificationGroup);
    }

    public boolean isAllowedNotificationId(@NotNull String notificationId) {
      return myNotificationIds.contains(notificationId);
    }

    public PluginInfo getPluginInfo(@NotNull String notificationGroup) {
      return myNotificationGroups.get(notificationGroup);
    }
  }
}
