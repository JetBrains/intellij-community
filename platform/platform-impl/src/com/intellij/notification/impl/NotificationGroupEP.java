// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl;

import com.intellij.AbstractBundle;
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
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ResourceBundle;

/**
 * Extension point to register notification group.
 * To get an instance of registered NotificationGroup use {@link com.intellij.notification.NotificationGroupManager}
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
   * Semicolon-separated list of notificationIds which should be recorder in feature usage statistics.
   * @see Notification#displayId
   */
  @Attribute("notificationIds")
  public String notificationIds;

  private PluginDescriptor pluginDescriptor;

  public @NlsContexts.NotificationTitle @Nullable String getDisplayName() {
    String baseName = bundle == null ? getPluginDescriptor().getResourceBundleBaseName() : bundle;
    if (baseName == null || key == null) {
      return id;
    }
    ResourceBundle resourceBundle = DynamicBundle.INSTANCE.getResourceBundle(baseName, getPluginDescriptor().getPluginClassLoader());
    return AbstractBundle.message(resourceBundle, key);
  }

  public @Nullable Icon getIcon() {
    return icon == null ? null : IconLoader.findIcon(icon, getClass());
  }

  @Transient
  public final @NotNull PluginDescriptor getPluginDescriptor() {
    return pluginDescriptor;
  }

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
     * No popup
     */
    NONE(NotificationDisplayType.NONE),
    /**
     *  Expires automatically after 10 seconds.
     */
    BALLOON(NotificationDisplayType.BALLOON),
    /**
     * Needs to be closed by user
     */
    STICKY_BALLOON(NotificationDisplayType.STICKY_BALLOON),
    /**
     * Tool window balloon
     */
    TOOL_WINDOW(NotificationDisplayType.TOOL_WINDOW);

    private final NotificationDisplayType myNotificationDisplayType;

    DisplayType(NotificationDisplayType type) {myNotificationDisplayType = type;}

    NotificationDisplayType getNotificationDisplayType() {
      return myNotificationDisplayType;
    }
  }
}
