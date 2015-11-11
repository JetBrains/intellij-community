/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl;

import com.intellij.notification.*;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.keymap.KeyboardSettingsExternalizable;
import com.intellij.openapi.keymap.impl.ui.KeymapPanel;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.awt.*;

/**
 * @author Denis Fokin
 */
public class KeyboardInternationalizationNotificationManager {
  public static final String LOCALIZATION_GROUP_DISPLAY_ID = "Localization and Internationalization";
  public static boolean notificationHasBeenShown;

  private KeyboardInternationalizationNotificationManager() {
  }

  public static void showNotification() {

    if (notificationHasBeenShown || !KeyboardSettingsExternalizable.isSupportedKeyboardLayout(WindowManagerEx.getInstanceEx().getMostRecentFocusedWindow())) return;

    MyNotificationListener listener =
      new MyNotificationListener();

    Notifications.Bus.notify(createNotification(LOCALIZATION_GROUP_DISPLAY_ID, listener));
    notificationHasBeenShown = true;
  }

  public static Notification createNotification(@NotNull final String groupDisplayId, @Nullable NotificationListener listener) {

    final String productName = ApplicationNamesInfo.getInstance().getProductName();

    Window recentFocusedWindow = WindowManagerEx.getInstanceEx().getMostRecentFocusedWindow();

    String text =
      "<html>We have found out that you are using a non-english keyboard layout. You can <a href='enable'>enable</a> smart layout support for " +
      KeyboardSettingsExternalizable.getDisplayLanguageNameForComponent(recentFocusedWindow) + " language." +
      "You can change this option in the settings of " + productName + " <a href='settings'>more...</a></html>";

    String title = "Enable smart keyboard internationalization for " + productName + ".";

    return new Notification(groupDisplayId, title,
                            text,
                            NotificationType.INFORMATION,
                            listener);
  }

  private static class MyNotificationListener implements NotificationListener {

    public MyNotificationListener() {
    }

    @Override
    public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
      if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        final String description = event.getDescription();
        if ("enable".equals(description)) {
          KeyboardSettingsExternalizable.getInstance().setNonEnglishKeyboardSupportEnabled(true);
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
