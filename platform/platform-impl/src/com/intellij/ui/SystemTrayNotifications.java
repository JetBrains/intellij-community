// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
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
      Logger logger = Logger.getInstance(SystemNotifications.class);
      Image image = AppUIUtil.loadApplicationIcon16();
      if (image == null) {
        logger.info("=== No system tray IDE icon (" + ApplicationInfoImpl.getShadowInstance().getApplicationSvgIconUrl() + ") ===");

        image = UIUtil.createImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics g = image.getGraphics();
        //noinspection UseJBColor
        g.setColor(Color.red);
        g.fillRect(0, 0, image.getWidth(null), image.getHeight(null));
        g.dispose();

        logger.info("=== Use \"red square\" stub for system tray IDE icon ===");
      }
      else {
        logger.info("=== System tray IDE icon: " + image + " ===");
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