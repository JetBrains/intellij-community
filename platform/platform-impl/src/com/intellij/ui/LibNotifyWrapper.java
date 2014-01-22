/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.PathManager;
import com.intellij.util.lang.UrlClassLoader;

/**
 * @author Denis Fokin
 */
public class LibNotifyWrapper {

  private final static String message = "Looks like you have run 32-bit Java on a 64-bit version of OS " +
                                "or just have not installed appropriate libnotify.so library";

  private static boolean available = true;

  static{
    UrlClassLoader.loadPlatformLibrary("notifywrapper");
  }

  native private static void showNotification(final String title, final String description, final String iconPath);

  public static void show(final String title, final String description, final String iconPath) {
    if (! available) return;
    try {
      showNotification(title, description, iconPath);
    } catch (UnsatisfiedLinkError ule) {
      available = false;
      NotificationGroup.balloonGroup("Linux configuration messages");
      Notifications.Bus.notify(
        new Notification("Linux configuration messages",
                         "Notification library has not been installed",
                         message, NotificationType.INFORMATION)
      );
    }
  }

  /**
   * Shows a libnotify notification with an icon from the ide bin directory.
   * If there is no such icon a default information icon is shown.
   * @param title notification title
   * @param description notification description
   */
  public static void showWithAppIcon(final String title, final String description) {
    String iconPath = AppUIUtil.findIcon(PathManager.getBinPath());
    show(title, description, (iconPath == null) ? "dialog-information" : iconPath);
  }

}
