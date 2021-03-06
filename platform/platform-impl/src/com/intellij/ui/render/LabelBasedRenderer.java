// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.render;

import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;

public class LabelBasedRenderer extends JLabel {

  public static class List<E> extends LabelBasedRenderer implements ListCellRenderer<E> {
    private static final Border EMPTY = JBUI.Borders.empty(1); // see DefaultListCellRenderer.getNoFocusBorder

    @NotNull
    @Override
    public Component getListCellRendererComponent(@NotNull JList<? extends E> list, @Nullable E value,
                                                  int index, boolean selected, boolean focused) {
      configure(list, value);
      setForeground(RenderingUtil.getForeground(list, selected));
      setBackground(RenderingUtil.getBackground(list, selected));
      setBorder(EMPTY);
      return this;
    }
  }

  public static class Tree extends LabelBasedRenderer implements TreeCellRenderer {
    private static final Border EMPTY = JBUI.Borders.emptyRight(3); // see DefaultTreeCellRenderer.getPreferredSize

    @NotNull
    @Override
    public Component getTreeCellRendererComponent(@NotNull JTree tree, @Nullable Object value,
                                                  boolean selected, boolean expanded, boolean leaf, int row, boolean focused) {
      configure(tree, tree.convertValueToText(value, selected, expanded, leaf, row, focused));
      setForeground(RenderingUtil.getForeground(tree, selected));
      setBackground(RenderingUtil.getBackground(tree, selected));
      setBorder(EMPTY);
      return this;
    }
  }

  void configure(@NotNull Component component, @Nullable Object value) {
    setComponentOrientation(component.getComponentOrientation());
    setEnabled(component.isEnabled());
    setFont(component.getFont());
    //noinspection HardCodedStringLiteral
    setText(value == null ? "" : value.toString());
    setIcon(null);
  }

  // The following methods are overridden for performance reasons, see DefaultListCellRenderer and DefaultTreeCellRenderer

  @Override
  public void validate() {}

  @Override
  public void invalidate() {}

  @Override
  public void revalidate() {}

  @Override
  public void repaint() {}

  @Override
  public void repaint(Rectangle bounds) {}

  @Override
  public void repaint(long unused, int x, int y, int width, int height) {}

  @Override
  public void firePropertyChange(String propertyName, byte oldValue, byte newValue) {}

  @Override
  public void firePropertyChange(String propertyName, char oldValue, char newValue) {}

  @Override
  public void firePropertyChange(String propertyName, short oldValue, short newValue) {}

  @Override
  public void firePropertyChange(String propertyName, int oldValue, int newValue) {}

  @Override
  public void firePropertyChange(String propertyName, long oldValue, long newValue) {}

  @Override
  public void firePropertyChange(String propertyName, float oldValue, float newValue) {}

  @Override
  public void firePropertyChange(String propertyName, double oldValue, double newValue) {}

  @Override
  public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {}

  @Override
  protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
    if ("text".equals(propertyName)
        || (("font".equals(propertyName) || "foreground".equals(propertyName))
            && oldValue != newValue
            && getClientProperty(BasicHTML.propertyKey) != null)) {

      super.firePropertyChange(propertyName, oldValue, newValue);
    }
  }
}
