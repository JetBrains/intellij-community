// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Inlay;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

class EditorEmbeddedComponentLayoutManager implements LayoutManager2 {

  private final @NotNull Map<JComponent, Constraint> constraints = new HashMap<>();
  private final @NotNull JScrollPane myEditorScrollPane;

  EditorEmbeddedComponentLayoutManager(@NotNull JScrollPane editorScrollPane) {
    myEditorScrollPane = editorScrollPane;
  }

  @Override
  public void addLayoutComponent(Component comp, Object constraints) {
    if (!(constraints instanceof Constraint)) return;
    this.constraints.put((JComponent)comp, (Constraint)constraints);
  }

  @Override
  public Dimension maximumLayoutSize(Container target) {
    return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
  }

  @Override
  public float getLayoutAlignmentX(Container target) {
    return 0;
  }

  @Override
  public float getLayoutAlignmentY(Container target) {
    return 0;
  }

  @Override
  public void invalidateLayout(Container target) {
  }

  @Override
  public void addLayoutComponent(String name, Component comp) {
    throw new UnsupportedOperationException("Using string-based constraints is not supported.");
  }

  @Override
  public void removeLayoutComponent(Component comp) {
    this.constraints.remove((JComponent)comp);
  }

  @Override
  public Dimension preferredLayoutSize(Container parent) {
    return new Dimension(0, 0);
  }

  @Override
  public Dimension minimumLayoutSize(Container parent) {
    return new Dimension(0, 0);
  }

  @Override
  public void layoutContainer(Container parent) {
    synchronized (parent.getTreeLock()) {
      int visibleWidth = myEditorScrollPane.getViewport().getWidth() - myEditorScrollPane.getVerticalScrollBar().getWidth();
      for (Map.Entry<JComponent, Constraint> entry : constraints.entrySet()) {
        JComponent component = entry.getKey();
        synchronizeBoundsWithInlay(entry.getValue(), component, visibleWidth);
      }
    }
  }

  private void synchronizeBoundsWithInlay(Constraint constraint, JComponent component, int visibleWidth) {
    Inlay<?> myInlay = constraint.getInlay();
    if (!myInlay.getEditor().getDocument().isInBulkUpdate()) {
      Rectangle inlayBounds = myInlay.getBounds();
      if (inlayBounds != null) {
        inlayBounds.setLocation(inlayBounds.x + verticalScrollbarLeftShift(), inlayBounds.y);
        Dimension size = component.getPreferredSize();
        Rectangle newBounds = new Rectangle(
          inlayBounds.x,
          inlayBounds.y,
          Math.min(constraint.isFullWidth() ? visibleWidth : size.width, visibleWidth),
          size.height
        );
        if (!newBounds.equals(component.getBounds())) {
          component.setBounds(newBounds);
          myInlay.update();
        }
      }
    }
  }

  private int verticalScrollbarLeftShift() {
    Object flipProperty = myEditorScrollPane.getClientProperty(JBScrollPane.Flip.class);
    if (flipProperty == JBScrollPane.Flip.HORIZONTAL || flipProperty == JBScrollPane.Flip.BOTH) {
      return myEditorScrollPane.getVerticalScrollBar().getWidth();
    }
    return 0;
  }

  static class Constraint {

    private final @NotNull Inlay<?> myInlay;

    private final boolean isFullWidth;

    Constraint(@NotNull Inlay<?> inlay, boolean width) {
      myInlay = inlay;
      isFullWidth = width;
    }

    private @NotNull Inlay<?> getInlay() {
      return myInlay;
    }

    private boolean isFullWidth() {
      return isFullWidth;
    }
  }
}
