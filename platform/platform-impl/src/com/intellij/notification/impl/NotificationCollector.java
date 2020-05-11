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

import java.util.HashMap;
import java.util.Map;

import static com.intellij.internal.statistic.utils.PluginInfoDetectorKt.getPluginInfoById;

public class NotificationCollector {
  private static final Logger LOG = Logger.getInstance(NotificationCollector.class);
  private static final Map<String, PluginInfo> ourNotificationWhitelist = new HashMap<>();
  private static final String NOTIFICATIONS = "notifications";
  private static final String UNKNOWN = "unknown";
  private static final String NOTIFICATION_GROUP = "notification_group";

  private NotificationCollector() {
    for (NotificationWhitelistEP extension : NotificationWhitelistEP.EP_NAME.getExtensionList()) {
      addNotificationToWhitelist(extension);
    }
    NotificationWhitelistEP.EP_NAME.addExtensionPointListener(new ExtensionPointListener<NotificationWhitelistEP>() {
      @Override
      public void extensionAdded(@NotNull NotificationWhitelistEP extension, @NotNull PluginDescriptor pluginDescriptor) {
        addNotificationToWhitelist(extension);
      }
    }, ApplicationManager.getApplication());
  }

  public void logBalloonShown(@Nullable Project project,
                              @NotNull NotificationDisplayType displayType,
                              @NotNull Notification notification,
                              boolean isExpandable) {
    FeatureUsageData data = createNotificationData(notification.getGroupId(), notification.id)
      .addData("display_type", displayType.name())
      .addData("severity", notification.getType().name())
      .addData("is_expandable", isExpandable);
    FUCounterUsageLogger.getInstance().logEvent(project, NOTIFICATIONS, "shown", data);
  }

  public void logToolWindowNotificationShown(@Nullable Project project,
                                             @NotNull Notification notification) {
    FeatureUsageData data = createNotificationData(notification.getGroupId(), notification.id)
      .addData("display_type", NotificationDisplayType.TOOL_WINDOW.name())
      .addData("severity", notification.getType().name());
    FUCounterUsageLogger.getInstance().logEvent(project, NOTIFICATIONS, "shown", data);
  }

  public void logNotificationLoggedInEventLog(@NotNull Project project, @NotNull Notification notification) {
    FeatureUsageData data = createNotificationData(notification.getGroupId(), notification.id)
      .addData("severity", notification.getType().name());
    FUCounterUsageLogger.getInstance().logEvent(project, NOTIFICATIONS, "logged", data);
  }

  public void logNotificationBalloonClosedByUser(@Nullable String notificationId, @Nullable String groupId) {
    if (notificationId == null) return;
    FeatureUsageData data = createNotificationData(groupId, notificationId);
    FUCounterUsageLogger.getInstance().logEvent(NOTIFICATIONS, "closed.by.user", data);
  }

  public void logNotificationActionInvoked(@NotNull Notification notification,
                                           @NotNull AnAction action,
                                           @NotNull NotificationPlace notificationPlace) {
    FeatureUsageData data = createNotificationData(notification.getGroupId(), notification.id)
      .addData("notification_place", notificationPlace.name());
    ActionsCollectorImpl.addActionClass(data, action, PluginInfoDetectorKt.getPluginInfo(action.getClass()));
    FUCounterUsageLogger.getInstance().logEvent(NOTIFICATIONS, "action.invoked", data);
  }

  public void logHyperlinkClicked(@NotNull Notification notification) {
    FeatureUsageData data = createNotificationData(notification.getGroupId(), notification.id);
    FUCounterUsageLogger.getInstance().logEvent(NOTIFICATIONS, "hyperlink.clicked", data);
  }

  public void logBalloonShownFromEventLog(@NotNull Notification notification) {
    FeatureUsageData data = createNotificationData(notification.getGroupId(), notification.id);
    FUCounterUsageLogger.getInstance().logEvent(NOTIFICATIONS, "event.log.balloon.shown", data);
  }

  public void logNotificationSettingsClicked(@NotNull String notificationId, @Nullable String groupId) {
    FeatureUsageData data = createNotificationData(groupId, notificationId);
    FUCounterUsageLogger.getInstance().logEvent(NOTIFICATIONS, "settings.clicked", data);
  }

  public void logNotificationBalloonExpanded(@NotNull Notification notification) {
    FeatureUsageData data = createNotificationData(notification.getGroupId(), notification.id);
    FUCounterUsageLogger.getInstance().logEvent(NOTIFICATIONS, "balloon.expanded", data);
  }

  public void logNotificationBalloonCollapsed(@NotNull Notification notification) {
    FeatureUsageData data = createNotificationData(notification.getGroupId(), notification.id);
    FUCounterUsageLogger.getInstance().logEvent(NOTIFICATIONS, "balloon.collapsed", data);
  }

  @NotNull
  private static FeatureUsageData createNotificationData(@Nullable String groupId, @NotNull String id) {
    return new FeatureUsageData()
      .addData("id", id)
      .addData(NOTIFICATION_GROUP, StringUtil.isNotEmpty(groupId) ? groupId : UNKNOWN)
      .addPluginInfo(getPluginInfo(groupId));
  }

  public static NotificationCollector getInstance() {
    return ServiceManager.getService(NotificationCollector.class);
  }

  private static PluginInfo getPluginInfo(@Nullable String groupId) {
    if (groupId == null) return null;
    PluginInfo pluginInfo = ourNotificationWhitelist.get(groupId);
    if (pluginInfo != null) {
      return pluginInfo;
    }
    NotificationGroup group = NotificationGroup.findRegisteredGroup(groupId);
    if (group == null) return null;
    return getPluginInfoById(group.getPluginId());
  }

  private static void addNotificationToWhitelist(NotificationWhitelistEP extension) {
    if (extension == null) return;
    PluginDescriptor pluginDescriptor = extension.getPluginDescriptor();
    if (pluginDescriptor == null) return;
    PluginInfo info = PluginInfoDetectorKt.getPluginInfoByDescriptor(pluginDescriptor);
    String groupIds = extension.groupIds;
    if (groupIds == null || !info.isDevelopedByJetBrains()) return;
    String[] values = StringUtil.convertLineSeparators(groupIds, "").split(";");
    for (String value : values) {
      if (StringUtil.isEmptyOrSpaces(value)) continue;
      String notificationGroup = StringUtil.trim(value);
      PluginInfo oldValue = ourNotificationWhitelist.put(notificationGroup, info);
      if (oldValue != null) {
        LOG.warn("Notification group '" + notificationGroup + "' is already registered in whitelist");
      }
    }
  }

  public static class NotificationRuleValidator extends CustomWhiteListRule {

    @Override
    public boolean acceptRuleId(@Nullable String ruleId) {
      return NOTIFICATION_GROUP.equals(ruleId);
    }

    @NotNull
    @Override
    protected ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
      if (UNKNOWN.equals(data)) return ValidationResultType.ACCEPTED;
      return ourNotificationWhitelist.containsKey(data) ? ValidationResultType.ACCEPTED : ValidationResultType.REJECTED;
    }
  }

  public enum NotificationPlace {
    BALLOON, EVENT_LOG
  }
}
