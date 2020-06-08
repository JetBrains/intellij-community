// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationsConfiguration;
import com.intellij.openapi.keymap.KeyboardSettingsExternalizable;
import com.intellij.openapi.keymap.impl.ui.KeymapPanel;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;

/**
 * @author Denis Fokin
 */
public final class KeyboardInternationalizationNotificationManager {
  public static final String LOCALIZATION_GROUP_DISPLAY_ID = "Localization and Internationalization";
  public static boolean notificationHasBeenShown;

  private KeyboardInternationalizationNotificationManager() {
  }

/*  public static void showNotification() {

    Window mostRecentFocusedWindow = WindowManagerEx.getInstanceEx().getMostRecentFocusedWindow();
    if (notificationHasBeenShown || (mostRecentFocusedWindow != null && !KeyboardSettingsExternalizable.isSupportedKeyboardLayout(mostRecentFocusedWindow))) return;

    MyNotificationListener listener =
      new MyNotificationListener();

    Notifications.Bus.notify(createNotification(LOCALIZATION_GROUP_DISPLAY_ID, listener));
    notificationHasBeenShown = true;
  }*/

/*  public static Notification createNotification(@NotNull final String groupDisplayId, @Nullable NotificationListener listener) {

    Window recentFocusedWindow = WindowManagerEx.getInstanceEx().getMostRecentFocusedWindow();

    if (recentFocusedWindow == null) {
      recentFocusedWindow = Window.getWindows()[0];
    }

    final String productName = ApplicationNamesInfo.getInstance().getProductName();

    String text =
      "<html>We have found out that you are using a non-english keyboard layout. You can <a href='enable'>enable</a> smart layout support for " +
      KeyboardSettingsExternalizable.getDisplayLanguageNameForComponent(recentFocusedWindow) + " language." +
      "You can change this option in the settings of " + productName + " <a href='settings'>more...</a></html>";

    String title = "Enable smart keyboard internationalization for " + productName + ".";

    return new Notification(groupDisplayId, title,
                            text,
                            NotificationType.INFORMATION,
                            listener);
  }*/

  private static class MyNotificationListener implements NotificationListener {

    MyNotificationListener() {
    }

    @Override
    public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
      if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        final String description = event.getDescription();
        if ("enable".equals(description)) {
          KeyboardSettingsExternalizable.getInstance().setPreferKeyPositionOverCharOption(true);
        }
        else if ("settings".equals(description)) {
          final ShowSettingsUtil util = ShowSettingsUtil.getInstance();
          IdeFrame ideFrame = WindowManagerEx.getInstanceEx().findFrameFor(null);
          //util.editConfigurable((JFrame)ideFrame, new StatisticsConfigurable(true));
          util.showSettingsDialog(ideFrame.getProject(), KeymapPanel.class);
        }

        NotificationsConfiguration.getNotificationsConfiguration().changeSettings(LOCALIZATION_GROUP_DISPLAY_ID, NotificationDisplayType.NONE, false, false);
        notification.expire();
      }
    }
  }
}
