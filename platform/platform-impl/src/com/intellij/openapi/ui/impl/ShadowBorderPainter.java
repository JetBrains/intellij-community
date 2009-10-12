/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.ui.impl;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * @author spleaner
 * @author Konstantin Bulenkov
 */
public class ShadowBorderPainter {
  private static final Icon TOP = IconLoader.getIcon("/ide/shadow/top.png");
  private static final Icon TOP_RIGHT = IconLoader.getIcon("/ide/shadow/top-right.png");
  private static final Icon RIGHT = IconLoader.getIcon("/ide/shadow/right.png");
  private static final Icon BOTTOM_RIGHT = IconLoader.getIcon("/ide/shadow/bottom-right.png");
  private static final Icon BOTTOM = IconLoader.getIcon("/ide/shadow/bottom.png");
  private static final Icon BOTTOM_LEFT = IconLoader.getIcon("/ide/shadow/bottom-left.png");
  private static final Icon LEFT = IconLoader.getIcon("/ide/shadow/left.png");
  private static final Icon TOP_LEFT = IconLoader.getIcon("/ide/shadow/top-left.png");

  public static final int SIDE_SIZE = 35;
  public static final int TOP_SIZE = 20;
  public static final int BOTTOM_SIZE = 49;

  private static final Icon POPUP_TOP = IconLoader.getIcon("/ide/shadow/popup/top.png");
  private static final Icon POPUP_TOP_RIGHT = IconLoader.getIcon("/ide/shadow/popup/top-right.png");
  private static final Icon POPUP_RIGHT = IconLoader.getIcon("/ide/shadow/popup/right.png");
  private static final Icon POPUP_BOTTOM_RIGHT = IconLoader.getIcon("/ide/shadow/popup/bottom-right.png");
  private static final Icon POPUP_BOTTOM = IconLoader.getIcon("/ide/shadow/popup/bottom.png");
  private static final Icon POPUP_BOTTOM_LEFT = IconLoader.getIcon("/ide/shadow/popup/bottom-left.png");
  private static final Icon POPUP_LEFT = IconLoader.getIcon("/ide/shadow/popup/left.png");
  private static final Icon POPUP_TOP_LEFT = IconLoader.getIcon("/ide/shadow/popup/top-left.png");

  public static final int POPUP_SIDE_SIZE = 7;
  public static final int POPUP_TOP_SIZE = 4;
  public static final int POPUP_BOTTOM_SIZE = 10;

  private ShadowBorderPainter() {
  }

  public static BufferedImage createShadow(final JComponent c, final int width, final int height, boolean isPopup) {
    final GraphicsConfiguration graphicsConfiguration = GraphicsEnvironment.getLocalGraphicsEnvironment().
        getDefaultScreenDevice().getDefaultConfiguration();

    final BufferedImage image = graphicsConfiguration.createCompatibleImage(width, height, Transparency.TRANSLUCENT);
    final Graphics2D g = image.createGraphics();

    final Icon topLeft = isPopup ? POPUP_TOP_LEFT : TOP_LEFT;
    final Icon topRight = isPopup ? POPUP_TOP_RIGHT : TOP_RIGHT;
    final Icon bottom = isPopup ? POPUP_BOTTOM : BOTTOM;
    final Icon top = isPopup ? POPUP_TOP : TOP;
    final Icon bottomRight = isPopup ? POPUP_BOTTOM_RIGHT : BOTTOM_RIGHT;
    final Icon bottomLeft = isPopup ? POPUP_BOTTOM_LEFT : BOTTOM_LEFT;
    final Icon left = isPopup ? POPUP_LEFT : LEFT;
    final Icon right = isPopup ? POPUP_RIGHT : RIGHT;
    final int sideSize = isPopup ? POPUP_SIDE_SIZE : SIDE_SIZE;
    final int bottomSize = isPopup ? POPUP_BOTTOM_SIZE : BOTTOM_SIZE;


    topLeft.paintIcon(c, g, 0, 0);
    topRight.paintIcon(c, g, width - sideSize * 2, 0);

    for (int _x = sideSize * 2; _x < width - sideSize * 2; _x ++) {
      top.paintIcon(c, g, _x, 0);
    }

    for (int _x = bottomSize * 2; _x < width - bottomSize * 2; _x ++) {
      bottom.paintIcon(c, g, _x, height - bottomSize);
    }

    for (int _y = sideSize * 2; _y < height - bottomSize * 2; _y ++) {
      left.paintIcon(c, g, 0, _y);
      right.paintIcon(c, g, width - sideSize, _y);
    }

    bottomRight.paintIcon(c, g, width - bottomSize * 2, height - bottomSize * 2);
    bottomLeft.paintIcon(c, g, 0, height - bottomSize * 2);

    g.dispose();
    return image;
  }

  public static BufferedImage createShadow(final JComponent c, final int width, final int height) {
    return createShadow(c, width, height, false);
  }

  public static BufferedImage createPopupShadow(final JComponent c, final int width, final int height) {
    return createShadow(c, width, height, true);
  }
}
