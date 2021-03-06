// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;

/**
 * Extension point to register a notification group ID which should be recorder in feature usage statistics.
 * @deprecated use {@link NotificationGroupEP} instead
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
public class NotificationWhitelistEP extends NotificationAllowlistEP {
  public static final ExtensionPointName<NotificationAllowlistEP> EP_NAME = ExtensionPointName.create("com.intellij.notificationWhitelist");
}