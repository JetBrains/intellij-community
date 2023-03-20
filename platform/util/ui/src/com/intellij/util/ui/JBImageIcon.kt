// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.image.ImageObserver;

/**
 * HiDPI-aware image icon
 *
 * @author Konstantin Bulenkov
 */
public class JBImageIcon extends ImageIcon {
  public JBImageIcon(@NotNull Image image) {
    super(image);
  }

  @Override
  public synchronized void paintIcon(final Component c, final Graphics g, final int x, final int y) {
    ImageObserver observer = getImageObserver();
    StartupUiUtil.drawImage(g, getImage(), x, y, observer == null ? c : observer);
  }
}
