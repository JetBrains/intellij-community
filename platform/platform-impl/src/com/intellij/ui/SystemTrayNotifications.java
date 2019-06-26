// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.ui.paint.PaintUtil;
import com.intellij.ui.scale.DerivedScaleType;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
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
      ourWin10Instance = new SystemTrayNotifications(createImage(), TrayIcon.MessageType.INFO);
    }
    return ourWin10Instance;
  }

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(SystemTrayNotifications.class);
  }

  @NotNull
  private static Image createImage() {
    String iconUrl = ApplicationInfoImpl.getShadowInstance().getSmallApplicationSvgIconUrl();
    Icon icon;

    if (iconUrl == null) {
      getLogger().info("=== SmallApplicationSvgIconUrl not defined ===");
      return createStubImage();
    }

    icon = IconLoader.findIcon(iconUrl);
    if (icon == null) {
      getLogger().info("=== Icon (" + iconUrl + ") not found ===");
      return createStubImage();
    }

    float scale = 16 / (float)icon.getIconWidth();
    icon = IconUtil.scale(icon, null, scale);

    ScaleContext context = ScaleContext.create();
    int width = PaintUtil.RoundingMode.ROUND.round(context.apply(icon.getIconWidth(), DerivedScaleType.DEV_SCALE));
    int height = PaintUtil.RoundingMode.ROUND.round(context.apply(icon.getIconHeight(), DerivedScaleType.DEV_SCALE));
    BufferedImage image = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration()
      .createCompatibleImage(width, height, Transparency.TRANSLUCENT);

    Graphics2D g = image.createGraphics();
    try {
      icon.paintIcon(null, g, 0, 0);
    }
    finally {
      g.dispose();
    }
    return image;
  }

  @NotNull
  private static Image createStubImage() {
    getLogger().info("=== Use \"red square\" stub for system tray IDE icon ===");

    Image image = UIUtil.createImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    Graphics g = image.getGraphics();

    try {
      //noinspection UseJBColor
      g.setColor(Color.red);
      g.fillRect(0, 0, image.getWidth(null), image.getHeight(null));
    }
    finally {
      g.dispose();
    }

    return image;
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