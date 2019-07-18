// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree.ui;

import com.intellij.ui.scale.JBUIScale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

final class CompactPainter implements Control.Painter {
  static final Control.Painter DEFAULT = new CompactPainter(true, null, null, 0);
  private final Boolean myPaintLines;
  private final Integer myLeftIndent;
  private final Integer myRightIndent;
  private final Integer myLeafIndent;

  CompactPainter(@Nullable Boolean paintLines, @Nullable Integer leftIndent, @Nullable Integer rightIndent, @Nullable Integer leafIndent) {
    myPaintLines = paintLines;
    myLeftIndent = leftIndent;
    myRightIndent = rightIndent;
    myLeafIndent = leafIndent;
  }

  @Override
  public int getRendererOffset(@NotNull Control control, int depth, boolean leaf) {
    if (depth < 0) return -1; // do not paint row
    if (depth == 0) return 0;
    int left = getLeftIndent();
    int offset = getLeafIndent(leaf);
    if (offset < 0) offset = left + control.getWidth() + getRightIndent();
    return depth > 1 ? (depth - 1) * (left + JBUIScale.scale(2)) + offset : offset;
  }

  @Override
  public int getControlOffset(@NotNull Control control, int depth, boolean leaf) {
    if (depth <= 0 || leaf) return -1; // do not paint control
    int left = getLeftIndent();
    return depth > 1 ? (depth - 1) * (left + JBUIScale.scale(2)) + left : left;
  }

  @Override
  public void paint(@NotNull Component c, @NotNull Graphics g, int x, int y, int width, int height,
                    @NotNull Control control, int depth, boolean leaf, boolean expanded, boolean selected) {
    if (depth <= 0) return; // do not paint
    boolean paintLines = getPaintLines();
    if (!paintLines && leaf) return; // nothing to paint
    int controlWidth = control.getWidth();
    int left = getLeftIndent();
    int indent = left + JBUIScale.scale(2);
    x += left;
    int controlX = !leaf && depth > 1 ? (depth - 1) * indent + x : x;
    if (paintLines && (depth != 1 || (!leaf && expanded))) {
      g.setColor(LINE_COLOR);
      x += JBUIScale.scale(1);
      while (--depth > 0) {
        g.drawLine(x, y, x, y + height);
        x += indent;
      }
      if (!leaf && expanded) {
        int offset = height / 2 - control.getIcon(expanded, selected).getIconHeight() / 4;
        if (offset > 0) g.drawLine(x, y + height - offset, x, y + height);
      }
    }
    if (leaf) return; // do not paint control for a leaf node
    control.paint(c, g, controlX, y, controlWidth, height, expanded, selected);
  }

  private boolean getPaintLines() {
    return myPaintLines != null ? myPaintLines : UIManager.getBoolean("Tree.paintLines");
  }

  private int getLeftIndent() {
    return myLeftIndent == null ? 0 : Math.max(0, JBUIScale.scale(myLeftIndent));
  }

  private int getRightIndent() {
    return myRightIndent == null ? 0 : Math.max(0, JBUIScale.scale(myRightIndent));
  }

  private int getLeafIndent(boolean leaf) {
    return !leaf || myLeafIndent == null ? -1 : JBUIScale.scale(myLeafIndent);
  }
}
