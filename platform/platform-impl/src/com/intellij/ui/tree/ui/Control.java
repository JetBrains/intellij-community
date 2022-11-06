// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree.ui;

import com.intellij.openapi.util.Key;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.function.Function;

public interface Control {
  /**
   * This key can be set to a custom control for particular {@link TreePath} to render custom icon instead of collapse/expand tree icons.
   *
   * @see JComponent#putClientProperty
   */
  Key<Function<@NotNull TreePath, @Nullable Control>> CUSTOM_CONTROL = Key.create("CUSTOM_CONTROL");

  @NotNull
  Icon getIcon(boolean expanded, boolean selected);

  int getWidth();

  int getHeight();

  void paint(@NotNull Component c, @NotNull Graphics g, int x, int y, int width, int height, boolean expanded, boolean selected);


  interface Painter {
    /**
     * This key is used to specify a custom tree control painter for a tree or a whole application.
     */
    Key<Painter> KEY = Key.create("tree control painter");

    Control.Painter DEFAULT = new ClassicPainter(null, null, null, null);
    Control.Painter COMPACT = new ClassicPainter(null, null, 0, null);
    Control.Painter LEAF_WITHOUT_INDENT = new ClassicPainter(null, null, null, 0);

    JBColor LINE_COLOR = JBColor.namedColor("Tree.hash", new JBColor(0xE6E6E6, 0x505355));

    int getRendererOffset(@NotNull Control control, int depth, boolean leaf);

    int getControlOffset(@NotNull Control control, int depth, boolean leaf);

    void paint(@NotNull Component c, @NotNull Graphics g, int x, int y, int width, int height,
               @NotNull Control control, int depth, boolean leaf, boolean expanded, boolean selected);
  }
}
