// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

final class SystemTrayNotifications implements SystemNotificationsImpl.Notifier {
  private static SystemTrayNotifications ourWin10Instance;

  static synchronized @Nullable SystemTrayNotifications getWin10Instance() throws AWTException {
    if (ourWin10Instance == null && SystemTray.isSupported()) {
      ourWin10Instance = new SystemTrayNotifications(createImage(), TrayIcon.MessageType.INFO);
    }
    return ourWin10Instance;
  }

  private static Image createImage() {
    Icon icon = AppUIUtil.loadSmallApplicationIcon(ScaleContext.create());
    return ImageUtil.toBufferedImage(IconUtil.toImage(icon));
  }

  private final TrayIcon myTrayIcon;
  private final TrayIcon.MessageType myType;

  private SystemTrayNotifications(@NotNull Image image, @NotNull TrayIcon.MessageType type) throws AWTException {
    myType = type;

    String tooltip = ApplicationInfoImpl.getShadowInstance().getFullApplicationName();
    myTrayIcon = new TrayIcon(image, tooltip);
    myTrayIcon.setImageAutoSize(true);
    SystemTray.getSystemTray().add(myTrayIcon);

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
