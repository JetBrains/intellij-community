// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * @author Alexander Lobas
 */
final class SystemTrayNotifications implements SystemNotificationsImpl.Notifier {
  private static SystemTrayNotifications ourWin10Instance;

  @Nullable
  static synchronized SystemTrayNotifications getWin10Instance() throws AWTException {
    if (ourWin10Instance == null && SystemTray.isSupported()) {
      ourWin10Instance = new SystemTrayNotifications(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), TrayIcon.MessageType.INFO);
    }
    return ourWin10Instance;
  }

  private final TrayIcon myTrayIcon;
  private final TrayIcon.MessageType myType;

  private SystemTrayNotifications(@NotNull Image image, @NotNull TrayIcon.MessageType type) throws AWTException {
    myType = type;
    SystemTray.getSystemTray().add(myTrayIcon = new TrayIcon(image));
  }

  @Override
  public void notify(@NotNull String name, @NotNull String title, @NotNull String description) {
    myTrayIcon.displayMessage(title, description, myType);
  }
}