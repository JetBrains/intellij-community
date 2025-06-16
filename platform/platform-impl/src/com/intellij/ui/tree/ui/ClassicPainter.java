// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree.ui;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.ui.paint.PaintUtil;
import com.intellij.ui.scale.JBUIScale;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.*;
import java.awt.*;

@ApiStatus.Internal
public final class ClassicPainter implements Control.Painter {
  private static final int GAP = 2; // minimal space between a control icon and a renderer component
  private final Boolean myPaintLines;
  private final Integer myLeftIndent;
  private final Integer myRightIndent;
  private final Integer myLeafIndent;

  @VisibleForTesting
  public ClassicPainter(@Nullable Boolean paintLines, @Nullable Integer leftIndent, @Nullable Integer rightIndent, @Nullable Integer leafIndent) {
    myPaintLines = paintLines;
    myLeftIndent = leftIndent;
    myRightIndent = rightIndent;
    myLeafIndent = leafIndent;
  }

  @Override
  public int getRendererOffset(@NotNull Control control, int depth, boolean leaf) {
    if (depth < 0) return -1; // do not paint row
    if (depth == 0) return 0;
    int controlWidth = control.getWidth();
    int left = getLeftIndent(controlWidth / 2);
    int right = getRightIndent();
    int offset = getLeafIndent(leaf);
    if (offset < 0) offset = Math.max(controlWidth + left - controlWidth / 2 + JBUIScale.scale(GAP), left + right);
    return depth > 1 ? (depth - 1) * (left + right) + offset : offset;
  }

  @Override
  public int getControlOffset(@NotNull Control control, int depth, boolean leaf) {
    if (depth <= 0 || leaf) return -1; // do not paint control
    int controlWidth = control.getWidth();
    int left = getLeftIndent(controlWidth / 2);
    int offset = left - controlWidth / 2;
    return depth > 1 ? (depth - 1) * (left + getRightIndent()) + offset : offset;
  }

  @Override
  public void paint(@NotNull Component c, @NotNull Graphics g, int x, int y, int width, int height,
                    @NotNull Control control, int depth, boolean leaf, boolean expanded, boolean selected) {
    if (depth <= 0) return; // do not paint
    boolean paintLines = getPaintLines();
    if (!paintLines && leaf) return; // nothing to paint
    int controlWidth = control.getWidth();
    int left = getLeftIndent(controlWidth / 2);
    int indent = left + getRightIndent();
    x += left - controlWidth / 2;
    int controlX = !leaf && depth > 1 ? (depth - 1) * indent + x : x;
    if (paintLines && (depth != 1 || (!leaf && expanded))) {
      g.setColor(LINE_COLOR);
      while (--depth > 0) {
        paintLine(g, x, y, controlWidth, height);
        x += indent;
      }
      if (!leaf && expanded) {
        int offset = (height - control.getHeight()) / 2;
        if (offset > 0) paintLine(g, x, y + height - offset, controlWidth, offset);
      }
    }
    if (leaf) return; // do not paint control for a leaf node
    control.paint(c, g, controlX, y, controlWidth, height, expanded, selected);
  }

  static boolean getPaintLines(@Nullable Boolean paintLines) {
    if (paintLines != null) return paintLines;
    if (UIManager.getBoolean("Tree.paintLines")) return true;
    UISettings settings = UISettings.getInstanceOrNull();
    return settings != null && settings.getShowTreeIndentGuides();
  }

  private boolean getPaintLines() {
    return getPaintLines(myPaintLines);
  }

  private int getLeftIndent(int min) {
    return Math.max(min, myLeftIndent != null ? JBUIScale.scale(myLeftIndent) : UIManager.getInt("Tree.leftChildIndent"));
  }

  private int getRightIndent() {
    int old = myRightIndent == null ? Registry.intValue("ide.ui.tree.indent", -1) : -1;
    if (old >= 0) return JBUIScale.scale(old);
    return Math.max(0, myRightIndent != null ? JBUIScale.scale(myRightIndent) : UIManager.getInt("Tree.rightChildIndent"));
  }

  private int getLeafIndent(boolean leaf) {
    return !leaf || myLeafIndent == null ? -1 : JBUIScale.scale(myLeafIndent);
  }

  private static void paintLine(@NotNull Graphics g, int x, int y, int width, int height) {
    if (g instanceof Graphics2D g2d) {
      double dx = x + width / 2.0 - PaintUtil.devPixel(g2d);
      LinePainter2D.paint(g2d, dx, y, dx, y + height, LinePainter2D.StrokeType.CENTERED, 1, RenderingHints.VALUE_ANTIALIAS_ON);
    }
    else {
      x += width / 2;
      g.drawLine(x, y, x, y + height);
    }
  }
}
