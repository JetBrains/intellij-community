// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree.ui;

import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Graphics;
import javax.swing.UIManager;

final class ClassicPainter implements Control.Painter {
  static final Control.Painter DEFAULT = new ClassicPainter(null, null, null, null);
  static final Control.Painter COMPACT = new ClassicPainter(true, 0, 0, null);
  private final Boolean myPaintLines;
  private final Integer myLeftIndent;
  private final Integer myRightIndent;
  private final Integer myLeafIndent;

  ClassicPainter(@Nullable Boolean paintLines, @Nullable Integer leftIndent, @Nullable Integer rightIndent, @Nullable Integer leafIndent) {
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
    if (offset < 0) offset = Math.max(controlWidth, left + right);
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
  public void paint(@NotNull Graphics g, int x, int y, int width, int height,
                    @NotNull Control control, int depth, boolean leaf, boolean expanded, boolean selected) {
    if (depth <= 0) return; // do not paint
    boolean paintLines = getPaintLines();
    if (!paintLines && leaf) return; // nothing to paint
    int controlWidth = control.getWidth();
    int left = getLeftIndent(controlWidth / 2);
    int right = getRightIndent();
    int indent = left + right;
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
    control.paint(g, controlX, y, controlWidth, height, expanded, selected);
  }

  private boolean getPaintLines() {
    return myPaintLines != null ? myPaintLines : UIManager.getBoolean("Tree.paintLines");
  }

  private int getLeftIndent(int min) {
    return Math.max(min, myLeftIndent != null ? JBUI.scale(myLeftIndent) : UIManager.getInt("Tree.leftChildIndent"));
  }

  private int getRightIndent() {
    return Math.max(0, myRightIndent != null ? JBUI.scale(myRightIndent) : UIManager.getInt("Tree.rightChildIndent"));
  }

  private int getLeafIndent(boolean leaf) {
    return !leaf || myLeafIndent == null ? -1 : JBUI.scale(myLeafIndent);
  }

  private static void paintLine(@NotNull Graphics g, int x, int y, int width, int height) {
    x += width / 2;
    g.drawLine(x, y, x, y + height);
  }
}
