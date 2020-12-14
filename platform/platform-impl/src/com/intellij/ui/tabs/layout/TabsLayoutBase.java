// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.layout;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsUtil;
import com.intellij.ui.tabs.impl.LayoutPassInfo;
import com.intellij.ui.tabs.impl.TabLabel;
import com.intellij.ui.tabs.impl.tabsLayout.TabsLayout;
import com.intellij.ui.tabs.impl.tabsLayout.TabsLayoutCallback;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelListener;
import java.util.ArrayList;
import java.util.List;

public abstract class TabsLayoutBase implements TabsLayout {

  @NonNls private static final String LAYOUT_DONE = "Layout.done";

  protected TabsLayoutCallback myCallback;


  protected double getDragOutMultiplier() {
    return Registry.doubleValue("ide.tabbedPane.dragOutMultiplier");
  }


  protected abstract LayoutPassInfo doLayout(List<TabInfo> infosToShow, boolean isForced);


  @Override
  public void init(@NotNull TabsLayoutCallback callback) {
    myCallback = callback;
  }

  @Override
  public final LayoutPassInfo layoutContainer(boolean isForced) {
    List<TabInfo> tabsInfos = new ArrayList<>(myCallback.getVisibleTabsInfos());

    TabInfo dropInfo = myCallback.getDropInfo();

    if (dropInfo != null && !tabsInfos.contains(dropInfo) && myCallback.isShowDropLocation()) {
      int dropInfoIndex = myCallback.getDropInfoIndex();
      if (dropInfoIndex >= 0 && dropInfoIndex < tabsInfos.size()) {
        tabsInfos.add(dropInfoIndex, dropInfo);
      }
      else {
        tabsInfos.add(dropInfo);
      }
    }

    LayoutPassInfo layoutPassInfo = doLayout(tabsInfos, isForced);

    applyResetComponents();

    return layoutPassInfo;
  }

  @Nullable
  @Override
  public MouseListener getMouseListener() {
    return null;
  }

  @Nullable
  @Override
  public MouseMotionListener getMouseMotionListener() {
    return null;
  }

  @Nullable
  @Override
  public MouseWheelListener getMouseWheelListener() {
    return null;
  }

  @Override
  public void mouseMotionEventDispatched(MouseEvent mouseMotionEvent) {
  }

  @Override
  public boolean isToolbarOnTabs() {
    return false;
  }

  @Override
  public boolean isDragOut(TabLabel tabLabel, int deltaX, int deltaY) {
    return Math.abs(deltaY) > tabLabel.getSize().height * getDragOutMultiplier();
  }

  @Override
  public boolean ignoreTabLabelLimitedWidthWhenPaint() {
    return false;
  }

  @Override
  public void dispose() {
  }

  protected Rectangle layoutComp(int componentX, int componentY,
                       final JComponent comp,
                       int deltaWidth, int deltaHeight) {
    return layoutComp(new Rectangle(componentX, componentY,
                                    myCallback.getComponent().getWidth(), myCallback.getComponent().getHeight()),
                      comp, deltaWidth, deltaHeight);
  }

  protected Rectangle layoutComp(final Rectangle bounds, final JComponent comp,
                       int deltaWidth, int deltaHeight) {
    final Insets insets = myCallback.getLayoutInsets();
    final Insets inner = myCallback.getInnerInsets();

    int x = insets.left + bounds.x + inner.left;
    int y = insets.top + bounds.y + inner.top;
    int width = bounds.width - insets.left - insets.right - bounds.x - inner.left - inner.right;
    int height = bounds.height - insets.top - insets.bottom - bounds.y - inner.top - inner.bottom;

    if (!myCallback.isHiddenTabs()) {
      width += deltaWidth;
      height += deltaHeight;
    }

    return layout(comp, x, y, width, height);
  }

  protected Rectangle layout(JComponent c, int x, int y, int width, int height) {
    return layout(c, new Rectangle(x, y, width, height));
  }

  protected Rectangle layout(JComponent component, Rectangle bounds) {
    final Rectangle now = component.getBounds();
    if (!bounds.equals(now)) {
      component.setBounds(bounds);
    }
    component.doLayout();
    component.putClientProperty(LAYOUT_DONE, Boolean.TRUE);

    return bounds;
  }


  protected void resetLayout(boolean resetLabels) {
    for (TabInfo each : myCallback.getVisibleTabsInfos()) {
      reset(each, resetLabels);
    }

    if (myCallback.getDropInfo() != null) {
      reset(myCallback.getDropInfo(), resetLabels);
    }

    for (TabInfo each : myCallback.getHiddenInfos().keySet()) {
      reset(each, resetLabels);
    }

    for (Component eachDeferred : myCallback.getDeferredToRemove().keySet()) {
      resetLayout((JComponent)eachDeferred);
    }
  }

  protected void reset(TabInfo each, boolean resetLabels) {
    final JComponent c = each.getComponent();
    if (c != null) {
      resetLayout(c);
    }

    resetLayout(myCallback.getToolbar(each));

    if (resetLabels) {
      resetLayout(myCallback.getTabLabel(each));
    }
  }

  protected void resetLayout(JComponent component) {
    if (component == null) return;
    component.putClientProperty(LAYOUT_DONE, null);
  }

  private void applyResetComponents() {
    JComponent container = myCallback.getComponent();
    for (int i = 0; i < container.getComponentCount(); i++) {
      final Component each = container.getComponent(i);
      if (each instanceof JComponent) {
        final JComponent jc = (JComponent)each;
        if (!UIUtil.isClientPropertyTrue(jc, LAYOUT_DONE)) {
          layout(jc, new Rectangle(0, 0, 0, 0));
        }
      }
    }
  }

  @Override
  @MagicConstant(intValues = {SwingConstants.CENTER, SwingConstants.TOP, SwingConstants.LEFT, SwingConstants.BOTTOM, SwingConstants.RIGHT, -1})
  public int getDropSideFor(Point point) {
    JComponent component = myCallback.getComponent();
    return TabsUtil.getDropSideFor(point, component);
  }
}
