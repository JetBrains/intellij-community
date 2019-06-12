// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.AtomicNullableLazyValue;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;

/**
 * @author mike
 */
public class SystemNotificationsImpl extends SystemNotifications {
  interface Notifier {
    void notify(@NotNull String name, @NotNull String title, @NotNull String description);
  }

  private final NullableLazyValue<Notifier> myNotifier = AtomicNullableLazyValue.createValue(SystemNotificationsImpl::getPlatformNotifier);

  @Override
  public void notify(@NotNull String notificationName, @NotNull String title, @NotNull String text) {
    if (NotificationsConfigurationImpl.getInstanceImpl().SYSTEM_NOTIFICATIONS && !ApplicationManager.getApplication().isActive()) {
      Notifier notifier = myNotifier.getValue();
      if (notifier != null) {
        notifier.notify(notificationName, title, text);
      }
    }
  }

  private static Notifier getPlatformNotifier() {
    try {
      if (SystemInfo.isMac) {
        if (SystemInfo.isMacOSMountainLion && SystemProperties.getBooleanProperty("ide.mac.mountain.lion.notifications.enabled", true)) {
          return MountainLionNotifications.getInstance();
        }
        else {
          return GrowlNotifications.getInstance();
        }
      }
      else if (SystemInfo.isXWindow) {
        return LibNotifyWrapper.getInstance();
      }
      else if (SystemInfo.isWin10OrNewer) {
        return SystemTrayNotifications.getWin10Instance();
      }
    }
    catch (Throwable t) {
      Logger logger = Logger.getInstance(SystemNotifications.class);
      if (logger.isDebugEnabled()) {
        logger.debug(t);
      }
      else {
        logger.info(t.getMessage());
      }
    }

    return null;
  }
}