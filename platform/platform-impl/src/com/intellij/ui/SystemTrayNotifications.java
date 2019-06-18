// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.ui.UIUtil;
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
      Image image = AppUIUtil.loadApplicationIcon16();
      if (image == null) {
        image = UIUtil.createImage(16, 16, BufferedImage.TYPE_INT_ARGB);
      }
      ourWin10Instance = new SystemTrayNotifications(image, TrayIcon.MessageType.INFO);
    }
    return ourWin10Instance;
  }

  private final TrayIcon myTrayIcon;
  private final TrayIcon.MessageType myType;

  private SystemTrayNotifications(@NotNull Image image, @NotNull TrayIcon.MessageType type) throws AWTException {
    myType = type;

    String tooltip = ApplicationInfoImpl.getShadowInstance().getFullApplicationName();
    SystemTray.getSystemTray().add(myTrayIcon = new TrayIcon(image, tooltip));

    myTrayIcon.addActionListener(e -> {
      IdeFrame frame = IdeFocusManager.getGlobalInstance().getLastFocusedFrame();
      if (frame instanceof Window) {
        UIUtil.toFront((Window)frame);
      }
    });
  }

  @Override
  public void notify(@NotNull String name, @NotNull String title, @NotNull String description) {
    myTrayIcon.displayMessage(title, description, myType);
  }
}