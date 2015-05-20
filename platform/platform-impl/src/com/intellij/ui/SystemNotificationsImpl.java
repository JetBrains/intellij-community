/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui;

import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;

/**
 * @author mike
 */
public class SystemNotificationsImpl extends SystemNotifications {
  interface Notifier {
    void notify(@NotNull String name, @NotNull String title, @NotNull String description);
  }

  private final Notifier myNotifier = getPlatformNotifier();

  @Override
  public boolean isAvailable() {
    return myNotifier != null;
  }

  @Override
  public void notify(@NotNull String notificationName, @NotNull String title, @NotNull String text) {
    if (myNotifier != null &&
        NotificationsConfigurationImpl.getInstanceImpl().SYSTEM_NOTIFICATIONS &&
        !ApplicationManager.getApplication().isActive()) {
      myNotifier.notify(notificationName, title, text);
    }
  }

  private static Notifier getPlatformNotifier() {
    try {
      if (SystemInfo.isMac) {
        if (SystemInfo.isMacOSMountainLion && Registry.is("ide.mac.mountain.lion.notifications.enabled")) {
          return MountainLionNotifications.getInstance();
        }
        if (!Boolean.getBoolean("growl.disable")) {
          return GrowlNotifications.getInstance();
        }
      }

      if (SystemInfo.isXWindow && Registry.is("ide.libnotify.enabled") ) {
        return LibNotifyWrapper.getInstance();
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
