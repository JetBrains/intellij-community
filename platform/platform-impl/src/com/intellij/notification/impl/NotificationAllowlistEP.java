// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl;

import com.intellij.notification.Notification;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Extension point to register a notification group ID which should be recorder in feature usage statistics.
 * @deprecated use {@link NotificationGroupEP} instead
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
public class NotificationAllowlistEP implements PluginAware {
  public static final ExtensionPointName<NotificationAllowlistEP> EP_NAME = new ExtensionPointName<>("com.intellij.notificationAllowlist");

  /**
   * Semicolon-separated list of groupIds.
   */
  @Attribute("groupIds")
  public String groupIds;

  /**
   * Semicolon-separated list of notificationIds.
   *
   * @see Notification#getDisplayId
   */
  @Attribute("notificationIds")
  public String notificationIds;

  private PluginDescriptor pluginDescriptor;

  @Transient
  public PluginDescriptor getPluginDescriptor() {
    return pluginDescriptor;
  }

  @Override
  @Transient
  public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor;
  }
}
