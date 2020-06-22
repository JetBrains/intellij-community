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
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.intellij.internal.statistic.utils.PluginInfoDetectorKt.getPluginInfoById;
import static com.intellij.internal.statistic.utils.PluginInfoDetectorKt.getUnknownPlugin;

public final class NotificationCollector {
  private static final Logger LOG = Logger.getInstance(NotificationCollector.class);
  private static final Map<String, PluginInfo> ourNotificationGroupsWhitelist = new ConcurrentHashMap<>();
  private static final Set<String> ourNotificationsWhitelist = new HashSet<>();
  private static final String NOTIFICATIONS = "notifications";
  private static final String UNKNOWN = "unknown";
  private static final String NOTIFICATION_GROUP = "notification_group";

  private NotificationCollector() {
    //noinspection deprecation
    ContainerUtil.concat(NotificationWhitelistEP.EP_NAME.getExtensionList(), NotificationAllowlistEP.EP_NAME.getExtensionList())
      .forEach(NotificationCollector::addNotificationsToWhitelist);

    ExtensionPointListener<NotificationAllowlistEP> extensionPointListener = new ExtensionPointListener<NotificationAllowlistEP>() {
      @Override
      public void extensionAdded(@NotNull NotificationAllowlistEP extension, @NotNull PluginDescriptor pluginDescriptor) {
        addNotificationsToWhitelist(extension);
      }

      @Override
      public void extensionRemoved(@NotNull NotificationAllowlistEP extension, @NotNull PluginDescriptor pluginDescriptor) {
        removeNotificationsFromWhitelist(extension);
      }
    };
    //noinspection deprecation
    NotificationWhitelistEP.EP_NAME.addExtensionPointListener(extensionPointListener, null);
    NotificationAllowlistEP.EP_NAME.addExtensionPointListener(extensionPointListener, null);
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
    if (action instanceof NotificationAction.Simple) {
      Object actionInstance = ((NotificationAction.Simple)action).getActionInstance();
      PluginInfo info = PluginInfoDetectorKt.getPluginInfo(actionInstance.getClass());
      data.addData("action_id", info.isSafeToReport() ? actionInstance.getClass().getName() : ActionsCollectorImpl.DEFAULT_ID);
    }
    else {
      ActionsCollectorImpl.addActionClass(data, action, PluginInfoDetectorKt.getPluginInfo(action.getClass()));
    }
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

  private static PluginInfo getPluginInfo(@Nullable String groupId) {
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
      return ourNotificationGroupsWhitelist.containsKey(data) ? ValidationResultType.ACCEPTED : ValidationResultType.REJECTED;
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
      return ourNotificationsWhitelist.contains(data) ? ValidationResultType.ACCEPTED : ValidationResultType.REJECTED;
    }
  }

  public enum NotificationPlace {
    BALLOON, EVENT_LOG
  }
}
