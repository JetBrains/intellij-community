// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.util.NullableLazyValue.atomicLazyNullable;

public final class SystemNotificationsImpl extends SystemNotifications {
  interface Notifier {
    void notify(@NotNull String name, @NotNull String title, @NotNull String description);
  }

  private final NullableLazyValue<Notifier> myNotifier = atomicLazyNullable(SystemNotificationsImpl::getPlatformNotifier);

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
        return MountainLionNotifications.getInstance();
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
