// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.render;

import com.intellij.openapi.util.Key;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.ComponentWithExpandableItems;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

import static com.intellij.ui.components.JBScrollPane.IGNORE_SCROLLBAR_IN_INSETS;

public final class RenderingHelper {
  /**
   * This key can be set to a tree to resize renderer component if it exceed a visible area.
   *
   * @see JComponent#putClientProperty
   */
  public static final Key<Boolean> SHRINK_LONG_RENDERER = Key.create("SHRINK_LONG_RENDERER");

  private final Rectangle myViewBounds;
  private final int myHintIndex;
  private int myRightMargin;
  private boolean myShrinkingDisabled;

  @ApiStatus.Internal
  public RenderingHelper(@NotNull JComponent component) {
    myViewBounds = new Rectangle(component.getWidth(), component.getHeight());
    myHintIndex = getExpandableHintIndex(component);
    JViewport viewport = ComponentUtil.getViewport(component);
    if (viewport != null) {
      myViewBounds.setBounds(-component.getX(), -component.getY(), viewport.getWidth(), viewport.getHeight());
      JScrollPane pane = ComponentUtil.getScrollPane(viewport);
      if (pane != null) {
        JScrollBar hsb = pane.getHorizontalScrollBar();
        if (hsb != null && hsb.isVisible()) {
          myShrinkingDisabled = !ClientProperty.isTrue(component, SHRINK_LONG_RENDERER);
        }
        JScrollBar vsb = pane.getVerticalScrollBar();
        if (vsb != null && vsb.isVisible() && !vsb.isOpaque() && ClientProperty.isFalse(vsb, IGNORE_SCROLLBAR_IN_INSETS)) {
          myRightMargin = vsb.getWidth();
        }
      }
    }
  }

  public int getX() {
    return myViewBounds.x;
  }

  public int getY() {
    return myViewBounds.y;
  }

  public int getWidth() {
    return myViewBounds.width;
  }

  public int getHeight() {
    return myViewBounds.height;
  }

  public int getRightMargin() {
    return myRightMargin;
  }

  public boolean isRendererShrinkingDisabled(int index) {
    return myShrinkingDisabled || isExpandableHintShown(index);
  }

  public boolean isExpandableHintShown(int index) {
    return myHintIndex == index;
  }

  private static int getExpandableHintIndex(@NotNull JComponent component) {
    if (component instanceof ComponentWithExpandableItems) {
      ComponentWithExpandableItems<?> c = (ComponentWithExpandableItems<?>)component;
      Collection<?> items = c.getExpandableItemsHandler().getExpandedItems();
      Object item = items.isEmpty() ? null : items.iterator().next();
      if (item instanceof Integer) return (Integer)item;
    }
    return -1;
  }
}
