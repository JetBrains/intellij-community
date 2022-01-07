// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl;

import com.intellij.BundleBase;
import com.intellij.DynamicBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.xmlb.Converter;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Registers notification group.
 * <p>
 * Use {@link com.intellij.notification.NotificationGroupManager#getNotificationGroup(String)} to obtain instance via {@link #id}.
 * </p>
 * <p>
 * See <a href="https://jetbrains.org/intellij/sdk/docs/user_interface_components/notifications.html#top-level-notifications">Top-Level Notifications</a>.
 * </p>
 */
public final class NotificationGroupEP implements PluginAware {
  static final ExtensionPointName<NotificationGroupEP> EP_NAME = new ExtensionPointName<>("com.intellij.notificationGroup");

  @Attribute("id")
  @RequiredElement
  public @NlsSafe String id;

  @Attribute("displayType")
  @RequiredElement
  public DisplayType displayType;

  @Attribute("isLogByDefault")
  public boolean isLogByDefault = true;

  @Attribute("toolWindowId")
  public String toolWindowId;

  @Attribute("icon")
  public String icon;

  /**
   * Message bundle, e.g. {@code "messages.DiagnosticBundle"}.
   * If unspecified, plugin's {@code <resource-bundle>} is used.
   *
   * @see #key
   */
  @Attribute("bundle")
  public String bundle;

  /**
   * Message key for {@link #getDisplayName}.
   *
   * @see #bundle
   */
  @Attribute("key")
  @NlsContexts.NotificationTitle
  public String key;

  /**
   * Semicolon-separated list of notificationIds which should be recorded in feature usage statistics.
   *
   * @see Notification#getDisplayId
   */
  @Attribute(value = "notificationIds", converter = IdParser.class)
  public @Nullable List<String> notificationIds;

  /**
   * If true, the group will not be shown in Settings | Notifications. Should be used for very rarely
   * shown notifications, where there's no expectation that the user will want to change how the notification
   * is presented.
   */
  @Attribute("hideFromSettings")
  public boolean hideFromSettings;

  private static final class IdParser extends Converter<List<String>> {
    @Override
    public @NotNull List<String> fromString(@NotNull String value) {
      if (value.isEmpty()) {
        return Collections.emptyList();
      }

      String[] values = StringUtilRt.convertLineSeparators(value, "").split(";");
      List<String> result = new ArrayList<>(values.length);
      for (String item : values) {
        if (!item.isEmpty()) {
          result.add(item.trim());
        }
      }
      return result;
    }

    @NotNull
    @Override
    public String toString(@NotNull List<String> ids) {
      return String.join(",", ids);
    }
  }

  private PluginDescriptor pluginDescriptor;

  public @NlsContexts.NotificationTitle @Nullable String getDisplayName(@NotNull PluginDescriptor pluginDescriptor) {
    String baseName = bundle == null ? pluginDescriptor.getResourceBundleBaseName() : bundle;
    if (baseName == null || key == null) {
      return id;
    }

    ResourceBundle resourceBundle = DynamicBundle.INSTANCE.getResourceBundle(baseName,
                                                                             pluginDescriptor.getClassLoader());
    return BundleBase.messageOrDefault(resourceBundle, key, null);
  }

  public @Nullable Icon getIcon(@NotNull PluginDescriptor pluginDescriptor) {
    return icon == null ? null : IconLoader.findIcon(icon, pluginDescriptor.getClassLoader());
  }

  //@Transient
  //public @NotNull PluginDescriptor getPluginDescriptor() {
  //  return pluginDescriptor;
  //}

  @Override
  public void setPluginDescriptor(@NotNull PluginDescriptor value) {
    pluginDescriptor = value;
  }

  public @Nullable NotificationDisplayType getDisplayType() {
    return displayType == null ? null : displayType.getNotificationDisplayType();
  }

  @Override
  public String toString() {
    return "NotificationGroupEP{" +
           "id='" + id + '\'' +
           ", displayType=" + displayType +
           ", isLogByDefault=" + isLogByDefault +
           ", toolWindowId='" + toolWindowId + '\'' +
           ", icon='" + icon + '\'' +
           ", bundle='" + bundle + '\'' +
           ", key='" + key + '\'' +
           ", notificationIds='" + notificationIds + '\'' +
           ", pluginDescriptor=" + pluginDescriptor +
           '}';
  }

  private enum DisplayType {
    /**
     * No popup.
     */
    NONE(NotificationDisplayType.NONE),
    /**
     * Expires automatically after 10 seconds.
     */
    BALLOON(NotificationDisplayType.BALLOON),
    /**
     * Needs to be closed by user.
     */
    STICKY_BALLOON(NotificationDisplayType.STICKY_BALLOON),
    /**
     * Tool window balloon.
     */
    TOOL_WINDOW(NotificationDisplayType.TOOL_WINDOW);

    private final NotificationDisplayType myNotificationDisplayType;

    DisplayType(NotificationDisplayType type) {myNotificationDisplayType = type;}

    NotificationDisplayType getNotificationDisplayType() {
      return myNotificationDisplayType;
    }
  }
}
