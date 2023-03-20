// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree.ui;

import com.intellij.ui.render.LabelBasedRenderer;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.NamedColorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;

final class LoadingNodeRenderer extends LabelBasedRenderer.Tree {
  static final TreeCellRenderer SHARED = new LoadingNodeRenderer();
  private static final Color COLOR = NamedColorUtil.getInactiveTextColor();
  private static final Icon ICON = JBUIScale.scaleIcon(EmptyIcon.create(8, 16));

  @NotNull
  @Override
  public Component getTreeCellRendererComponent(@NotNull JTree tree, @Nullable Object value,
                                                boolean selected, boolean expanded, boolean leaf, int row, boolean focused) {
    Component component = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, focused);
    if (!selected) setForeground(COLOR);
    setIcon(ICON);
    return component;
  }
}
