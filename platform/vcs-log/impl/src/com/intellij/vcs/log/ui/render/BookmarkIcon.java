// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.render;

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.vcs.log.ui.VcsBookmarkRef;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;

@ApiStatus.Internal
public class BookmarkIcon implements Icon {
  private final Color BOOKMARK_REF_TYPE_COLOR = JBColor.namedColor("VersionControl.RefLabel.bookmarkBackground",
                                                                   new JBColor(0xf4af3d, 0xd9a343));
  public static final float WIDTH = 3.5f;

  private final int mySize;

  public BookmarkIcon(@NotNull JComponent component, int size, @NotNull Color bgColor, @NotNull VcsBookmarkRef bookmark) {
    mySize = size;
  }


  @Override
  public void paintIcon(Component c, Graphics g, int iconX, int iconY) {
    Graphics2D g2 = (Graphics2D)g;
    GraphicsConfig config = GraphicsUtil.setupAAPainting(g2);

    g2.setColor(BOOKMARK_REF_TYPE_COLOR);

    float scale = mySize / LabelIcon.SIZE;

    float x = iconX + scale * 0.25f;
    float y = iconY + scale;
    Path2D.Float path = new Path2D.Float();
    path.moveTo(x, y);
    path.lineTo(x + 3 * scale, y);
    path.lineTo(x + 3 * scale, y + 5 * scale);
    path.lineTo(x + 1.5 * scale, y + 3.5 * scale);
    path.lineTo(x, y + 5 * scale);
    path.lineTo(x, y);
    path.closePath();
    g2.fill(path);

    config.restore();
  }

  @Override
  public int getIconWidth() {
    float scale = mySize / LabelIcon.SIZE;
    return Math.round(WIDTH * scale);
  }

  @Override
  public int getIconHeight() {
    return mySize;
  }
}
