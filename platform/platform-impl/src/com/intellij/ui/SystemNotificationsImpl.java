// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.util.system.OS;
import com.intellij.util.ui.GraphicsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.util.NullableLazyValue.atomicLazyNullable;

final class SystemNotificationsImpl extends SystemNotifications {
  interface Notifier {
    void notify(@NotNull String name, @NotNull String title, @NotNull String description);

    default void notify(@NotNull String name, @NotNull String title, @NotNull String description, @Nullable Runnable onActivated) {
      notify(name, title, description);
    }
  }

  private final NullableLazyValue<Notifier> myNotifier = atomicLazyNullable(SystemNotificationsImpl::getPlatformNotifier);

  @Override
  public void notify(@NotNull String notificationName, @NotNull String title, @NotNull String text) {
    notify(notificationName, title, text, null);
  }

  @Override
  public void notify(@NotNull String notificationName, @NotNull String title, @NotNull String text, @Nullable Runnable onActivated) {
    if (NotificationsConfigurationImpl.getInstanceImpl().SYSTEM_NOTIFICATIONS && !ApplicationManager.getApplication().isActive()) {
      var notifier = myNotifier.getValue();
      if (notifier != null) {
        notifier.notify(notificationName, title, text, onActivated);
      }
    }
  }

  private static @Nullable Notifier getPlatformNotifier() {
    if (!GraphicsUtil.isRemoteEnvironment()) {
      try {
        return switch (OS.CURRENT) {
          case Windows -> SystemTrayNotifications.getWin10Instance();
          case macOS -> MacOsNotifications.getInstance();
          default -> LibNotifyWrapper.getInstance();
        };
      }
      catch (Throwable t) {
        Logger.getInstance(SystemNotificationsImpl.class).infoWithDebug(t);
      }
    }

    return null;
  }
}
