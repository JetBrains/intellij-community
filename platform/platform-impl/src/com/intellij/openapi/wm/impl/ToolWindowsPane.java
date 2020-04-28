// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.ide.RemoteDesktopService;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.WindowInfo;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.reference.SoftReference;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.ui.paint.PaintUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ui.ImageUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static com.intellij.util.ui.UIUtil.useSafely;

/**
 * This panel contains all tool stripes and JLayeredPane at the center area. All tool windows are
 * located inside this layered pane.
 *
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ToolWindowsPane extends JBLayeredPane implements UISettingsListener {
  private static final Logger LOG = Logger.getInstance(ToolWindowsPane.class);
  public static final String TEMPORARY_ADDED = "TEMPORARY_ADDED";

  private final JFrame frame;

  private ToolWindowPaneState state = new ToolWindowPaneState();
  /**
   * This panel is the layered pane where all sliding tool windows are located. The DEFAULT
   * layer contains splitters. The PALETTE layer contains all sliding tool windows.
   */
  private final MyLayeredPane layeredPane;
  /*
   * Splitters.
   */
  private final ThreeComponentsSplitter verticalSplitter;
  private final ThreeComponentsSplitter horizontalSplitter;

  /*
   * Tool stripes.
   */
  private final Stripe leftStripe;
  private final Stripe rightStripe;
  private final Stripe bottomStripe;
  private final Stripe topStripe;

  private final List<Stripe> stripes = new ArrayList<>(4);

  private boolean isWideScreen;
  private boolean leftHorizontalSplit;
  private boolean rightHorizontalSplit;

  ToolWindowsPane(@NotNull JFrame frame, @NotNull Disposable parentDisposable) {
    setOpaque(false);
    this.frame = frame;

    // splitters
    verticalSplitter = new ThreeComponentsSplitter(true, parentDisposable);
    RegistryValue registryValue = Registry.get("ide.mainSplitter.min.size");
    registryValue.addListener(new RegistryValueListener() {
      @Override
      public void afterValueChanged(@NotNull RegistryValue value) {
        updateInnerMinSize(value);
      }
    }, parentDisposable);
    verticalSplitter.setDividerWidth(0);
    verticalSplitter.setDividerMouseZoneSize(Registry.intValue("ide.splitter.mouseZone"));
    verticalSplitter.setBackground(Color.gray);
    horizontalSplitter = new ThreeComponentsSplitter(false, parentDisposable);
    horizontalSplitter.setDividerWidth(0);
    horizontalSplitter.setDividerMouseZoneSize(Registry.intValue("ide.splitter.mouseZone"));
    horizontalSplitter.setBackground(Color.gray);
    updateInnerMinSize(registryValue);
    UISettings uiSettings = UISettings.getInstance();
    isWideScreen = uiSettings.getWideScreenSupport();
    leftHorizontalSplit = uiSettings.getLeftHorizontalSplit();
    rightHorizontalSplit = uiSettings.getRightHorizontalSplit();
    if (isWideScreen) {
      horizontalSplitter.setInnerComponent(verticalSplitter);
    }
    else {
      verticalSplitter.setInnerComponent(horizontalSplitter);
    }

    // tool stripes
    topStripe = new Stripe(SwingConstants.TOP);
    stripes.add(topStripe);
    leftStripe = new Stripe(SwingConstants.LEFT);
    stripes.add(leftStripe);
    bottomStripe = new Stripe(SwingConstants.BOTTOM);
    stripes.add(bottomStripe);
    rightStripe = new Stripe(SwingConstants.RIGHT);
    stripes.add(rightStripe);

    updateToolStripesVisibility(uiSettings);

    // layered pane
    layeredPane = new MyLayeredPane(isWideScreen ? horizontalSplitter : verticalSplitter);

    // compose layout
    add(topStripe, JLayeredPane.POPUP_LAYER);
    add(leftStripe, JLayeredPane.POPUP_LAYER);
    add(bottomStripe, JLayeredPane.POPUP_LAYER);
    add(rightStripe, JLayeredPane.POPUP_LAYER);
    add(layeredPane, JLayeredPane.DEFAULT_LAYER);

    setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());
  }

  void initDocumentComponent(@NotNull Project project) {
    JComponent editorComponent = FileEditorManagerEx.getInstanceEx(project).getComponent();
    editorComponent.setFocusable(false);
    setDocumentComponent(editorComponent);
  }

  private void updateInnerMinSize(@NotNull RegistryValue value) {
    int minSize = Math.max(0, Math.min(100, value.asInteger()));
    verticalSplitter.setMinSize(JBUIScale.scale(minSize));
    horizontalSplitter.setMinSize(JBUIScale.scale(minSize));
  }

  @Override
  public void doLayout() {
    Dimension size = getSize();
    if (!topStripe.isVisible()) {
      topStripe.setBounds(0, 0, 0, 0);
      bottomStripe.setBounds(0, 0, 0, 0);
      leftStripe.setBounds(0, 0, 0, 0);
      rightStripe.setBounds(0, 0, 0, 0);
      layeredPane.setBounds(0, 0, getWidth(), getHeight());
    }
    else {
      Dimension topSize = topStripe.getPreferredSize();
      Dimension bottomSize = bottomStripe.getPreferredSize();
      Dimension leftSize = leftStripe.getPreferredSize();
      Dimension rightSize = rightStripe.getPreferredSize();

      topStripe.setBounds(0, 0, size.width, topSize.height);
      int height = size.height - topSize.height - bottomSize.height;
      leftStripe.setBounds(0, topSize.height, leftSize.width, height);
      rightStripe.setBounds(size.width - rightSize.width, topSize.height, rightSize.width, height);
      bottomStripe.setBounds(0, size.height - bottomSize.height, size.width, bottomSize.height);

      UISettings uiSettings = UISettings.getInstance();
      if (uiSettings.getHideToolStripes() || uiSettings.getPresentationMode()) {
        layeredPane.setBounds(0, 0, size.width, size.height);
      }
      else {
        int width = size.width - leftSize.width - rightSize.width;
        layeredPane.setBounds(leftSize.width, topSize.height, width, height);
      }
    }
  }

  @Override
  public void uiSettingsChanged(@NotNull UISettings uiSettings) {
    updateToolStripesVisibility(uiSettings);
    updateLayout(uiSettings);
  }

  /**
   * @param dirtyMode if {@code true} then JRootPane will not be validated and repainted after adding
   *                  the decorator. Moreover in this (dirty) mode animation doesn't work.
   */
  final void addDecorator(@NotNull JComponent decorator, @NotNull WindowInfo info, boolean dirtyMode, @NotNull ToolWindowManagerImpl manager) {
    if (info.isDocked()) {
      boolean side = !info.isSplit();
      WindowInfo sideInfo = manager.getDockedInfoAt(info.getAnchor(), side);
      if (sideInfo == null) {
        ToolWindowAnchor anchor = info.getAnchor();
        setComponent(decorator, anchor, normalizeWeigh(info.getWeight()));
        if (!dirtyMode) {
          layeredPane.validate();
          layeredPane.repaint();
        }
      }
      else {
        addAndSplitDockedComponentCmd(decorator, info, dirtyMode, manager);
      }
    }
    else if (info.getType() == ToolWindowType.SLIDING) {
      addSlidingComponent(decorator, info, dirtyMode);
    }
    else {
      throw new IllegalArgumentException("Unknown window type: " + info.getType());
    }
  }

  void removeDecorator(@NotNull WindowInfo info, @Nullable JComponent component, boolean dirtyMode, @NotNull ToolWindowManagerImpl manager) {
    if (info.getType() == ToolWindowType.DOCKED) {
      WindowInfo sideInfo = manager.getDockedInfoAt(info.getAnchor(), !info.isSplit());
      if (sideInfo == null) {
        setComponent(null, info.getAnchor(), 0);
      }
      else {
        ToolWindowAnchor anchor = info.getAnchor();
        JComponent c = getComponentAt(anchor);
        if (c instanceof Splitter) {
          Splitter splitter = (Splitter)c;
          InternalDecorator component1 = (InternalDecorator)(info.isSplit() ? splitter.getFirstComponent() : splitter.getSecondComponent());
          state.addSplitProportion(info, component1, splitter);
          setComponent(component1, anchor,
                       component1 == null ? 0 : ToolWindowManagerImpl.getRegisteredMutableInfoOrLogError(component1).getWeight());
        }
        else {
          setComponent(null, anchor, 0);
        }
      }

      if (!dirtyMode) {
        layeredPane.validate();
        layeredPane.repaint();
      }
    }
    else if (info.getType() == ToolWindowType.SLIDING) {
      if (component != null) {
        removeSlidingComponent(component, info, dirtyMode);
      }
    }
  }

  public final @NotNull JComponent getLayeredPane() {
    return layeredPane;
  }

  public void validateAndRepaint() {
    layeredPane.validate();
    layeredPane.repaint();

    for (Stripe stripe : stripes) {
      stripe.revalidate();
      stripe.repaint();
    }
  }

  public void revalidateNotEmptyStripes() {
    for (Stripe stripe : stripes) {
      if (!stripe.isEmpty()) {
        stripe.revalidate();
      }
    }
  }

  private void setComponent(@Nullable JComponent component, @NotNull ToolWindowAnchor anchor, float weight) {
    if (ToolWindowAnchor.TOP == anchor) {
      verticalSplitter.setFirstComponent(component);
      verticalSplitter.setFirstSize((int)(layeredPane.getHeight() * weight));
    }
    else if (ToolWindowAnchor.LEFT == anchor) {
      horizontalSplitter.setFirstComponent(component);
      horizontalSplitter.setFirstSize((int)(layeredPane.getWidth() * weight));
    }
    else if (ToolWindowAnchor.BOTTOM == anchor) {
      verticalSplitter.setLastComponent(component);
      verticalSplitter.setLastSize((int)(layeredPane.getHeight() * weight));
    }
    else if (ToolWindowAnchor.RIGHT == anchor) {
      horizontalSplitter.setLastComponent(component);
      horizontalSplitter.setLastSize((int)(layeredPane.getWidth() * weight));
    }
    else {
      LOG.error("unknown anchor: " + anchor);
    }
  }

  private JComponent getComponentAt(@NotNull ToolWindowAnchor anchor) {
    if (ToolWindowAnchor.TOP == anchor) {
      return verticalSplitter.getFirstComponent();
    }
    else if (ToolWindowAnchor.LEFT == anchor) {
      return horizontalSplitter.getFirstComponent();
    }
    else if (ToolWindowAnchor.BOTTOM == anchor) {
      return verticalSplitter.getLastComponent();
    }
    else if (ToolWindowAnchor.RIGHT == anchor) {
      return horizontalSplitter.getLastComponent();
    }
    else {
      LOG.error("unknown anchor: " + anchor);
      return null;
    }
  }

  private void setDocumentComponent(@Nullable JComponent component) {
    (isWideScreen ? verticalSplitter : horizontalSplitter).setInnerComponent(component);
  }

  private void updateToolStripesVisibility(@NotNull UISettings uiSettings) {
    boolean oldVisible = leftStripe.isVisible();

    boolean showButtons = !uiSettings.getHideToolStripes() && !uiSettings.getPresentationMode();
    boolean visible = showButtons || state.isStripesOverlaid();
    leftStripe.setVisible(visible);
    rightStripe.setVisible(visible);
    topStripe.setVisible(visible);
    bottomStripe.setVisible(visible);

    boolean overlayed = !showButtons && state.isStripesOverlaid();

    leftStripe.setOverlayed(overlayed);
    rightStripe.setOverlayed(overlayed);
    topStripe.setOverlayed(overlayed);
    bottomStripe.setOverlayed(overlayed);

    if (oldVisible != visible) {
      revalidate();
      repaint();
    }
  }

  public int getBottomHeight() {
    return bottomStripe.isVisible() ? bottomStripe.getHeight() : 0;
  }

  public boolean isBottomSideToolWindowsVisible() {
    return getComponentAt(ToolWindowAnchor.BOTTOM) != null;
  }

  @NotNull
  Stripe getStripeFor(@NotNull ToolWindowAnchor anchor) {
    if (ToolWindowAnchor.TOP == anchor) {
      return topStripe;
    }
    if (ToolWindowAnchor.BOTTOM == anchor) {
      return bottomStripe;
    }
    if (ToolWindowAnchor.LEFT == anchor) {
      return leftStripe;
    }
    if (ToolWindowAnchor.RIGHT == anchor) {
      return rightStripe;
    }

    throw new IllegalArgumentException("Anchor=" + anchor);
  }

  @Nullable
  Stripe getStripeFor(@NotNull Rectangle screenRectangle, @NotNull Stripe preferred) {
    if (preferred.containsScreen(screenRectangle)) {
      return preferred;
    }

    for (Stripe stripe : stripes) {
      if (stripe.containsScreen(screenRectangle)) {
        return stripe;
      }
    }
    return null;
  }

  void startDrag() {
    for (Stripe each : stripes) {
      each.startDrag();
    }
  }

  void stopDrag() {
    for (Stripe stripe : stripes) {
      stripe.stopDrag();
    }
  }

  void stretchWidth(@NotNull ToolWindow window, int value) {
    stretch(window, value);
  }

  void stretchHeight(@NotNull ToolWindow window, int value) {
    stretch(window, value);
  }

  private void stretch(@NotNull ToolWindow wnd, int value) {
    Pair<Resizer, Component> pair = findResizerAndComponent(wnd);
    if (pair == null) return;

    boolean vertical = wnd.getAnchor() == ToolWindowAnchor.TOP || wnd.getAnchor() == ToolWindowAnchor.BOTTOM;
    int actualSize = (vertical ? pair.second.getHeight() : pair.second.getWidth()) + value;
    boolean first = wnd.getAnchor() == ToolWindowAnchor.LEFT  || wnd.getAnchor() == ToolWindowAnchor.TOP;
    int maxValue = vertical ? verticalSplitter.getMaxSize(first) : horizontalSplitter.getMaxSize(first);
    int minValue = vertical ? verticalSplitter.getMinSize(first) : horizontalSplitter.getMinSize(first);

    pair.first.setSize(Math.max(minValue, Math.min(maxValue, actualSize)));
  }

  private @Nullable Pair<Resizer, Component> findResizerAndComponent(@NotNull ToolWindow window) {
    if (!window.isVisible()) return null;

    Resizer resizer = null;
    Component component = null;

    if (window.getType() == ToolWindowType.DOCKED) {
      component = getComponentAt(window.getAnchor());

      if (component != null) {
        if (window.getAnchor().isHorizontal()) {
          resizer = verticalSplitter.getFirstComponent() == component
                    ? new Resizer.Splitter.FirstComponent(verticalSplitter)
                    : new Resizer.Splitter.LastComponent(verticalSplitter);
        }
        else {
          resizer = horizontalSplitter.getFirstComponent() == component
                    ? new Resizer.Splitter.FirstComponent(horizontalSplitter)
                    : new Resizer.Splitter.LastComponent(horizontalSplitter);
        }
      }
    }
    else if (window.getType() == ToolWindowType.SLIDING) {
      component = window.getComponent();
      while (component != null) {
        if (component.getParent() == layeredPane) break;
        component = component.getParent();
      }

      if (component != null) {
        if (window.getAnchor() == ToolWindowAnchor.TOP) {
          resizer = new Resizer.LayeredPane.Top(component);
        }
        else if (window.getAnchor() == ToolWindowAnchor.BOTTOM) {
          resizer = new Resizer.LayeredPane.Bottom(component);
        }
        else if (window.getAnchor() == ToolWindowAnchor.LEFT) {
          resizer = new Resizer.LayeredPane.Left(component);
        }
        else if (window.getAnchor() == ToolWindowAnchor.RIGHT) {
          resizer = new Resizer.LayeredPane.Right(component);
        }
      }
    }

    return resizer != null ? Pair.create(resizer, component) : null;
  }

  private void updateLayout(@NotNull UISettings uiSettings) {
    if (isWideScreen != uiSettings.getWideScreenSupport()) {
      JComponent documentComponent = (isWideScreen ? verticalSplitter : horizontalSplitter).getInnerComponent();
      isWideScreen = uiSettings.getWideScreenSupport();
      if (isWideScreen) {
        verticalSplitter.setInnerComponent(null);
        horizontalSplitter.setInnerComponent(verticalSplitter);
      }
      else {
        horizontalSplitter.setInnerComponent(null);
        verticalSplitter.setInnerComponent(horizontalSplitter);
      }
      layeredPane.remove(isWideScreen ? verticalSplitter : horizontalSplitter);
      layeredPane.add(isWideScreen ? horizontalSplitter : verticalSplitter, DEFAULT_LAYER);
      setDocumentComponent(documentComponent);
    }

    if (leftHorizontalSplit != uiSettings.getLeftHorizontalSplit()) {
      JComponent component = getComponentAt(ToolWindowAnchor.LEFT);
      if (component instanceof Splitter) {
        Splitter splitter = (Splitter)component;
        WindowInfoImpl firstInfo = ToolWindowManagerImpl.getRegisteredMutableInfoOrLogError((InternalDecorator)splitter.getFirstComponent());
        WindowInfoImpl secondInfo = ToolWindowManagerImpl.getRegisteredMutableInfoOrLogError((InternalDecorator)splitter.getSecondComponent());
        setComponent(splitter, ToolWindowAnchor.LEFT, ToolWindowAnchor.LEFT.isSplitVertically()
                                                      ? firstInfo.getWeight()
                                                      : firstInfo.getWeight() + secondInfo.getWeight());
      }
      leftHorizontalSplit = uiSettings.getLeftHorizontalSplit();
    }

    if (rightHorizontalSplit != uiSettings.getRightHorizontalSplit()) {
      JComponent component = getComponentAt(ToolWindowAnchor.RIGHT);
      if (component instanceof Splitter) {
        Splitter splitter = (Splitter)component;
        WindowInfoImpl firstInfo = ToolWindowManagerImpl.getRegisteredMutableInfoOrLogError((InternalDecorator)splitter.getFirstComponent());
        WindowInfoImpl secondInfo = ToolWindowManagerImpl.getRegisteredMutableInfoOrLogError((InternalDecorator)splitter.getSecondComponent());
        setComponent(splitter, ToolWindowAnchor.RIGHT, ToolWindowAnchor.RIGHT.isSplitVertically()
                                                       ? firstInfo.getWeight()
                                                       : firstInfo.getWeight() + secondInfo.getWeight());
      }
      rightHorizontalSplit = uiSettings.getRightHorizontalSplit();
    }
  }

  public boolean isMaximized(@NotNull ToolWindow window) {
    return state.isMaximized(window);
  }

  void setMaximized(@NotNull ToolWindow toolWindow, boolean maximized) {
    Pair<Resizer, Component> resizerAndComponent = findResizerAndComponent(toolWindow);
    if (resizerAndComponent == null) {
      return;
    }

    if (maximized) {
      int size = toolWindow.getAnchor().isHorizontal() ? resizerAndComponent.second.getHeight() : resizerAndComponent.second.getWidth();
      stretch(toolWindow, Short.MAX_VALUE);
      state.setMaximizedProportion(Pair.create(toolWindow, size));
    }
    else {
      Pair<ToolWindow, Integer> maximizedProportion = state.getMaximizedProportion();
      LOG.assertTrue(maximizedProportion != null);
      ToolWindow maximizedWindow = maximizedProportion.first;
      assert maximizedWindow == toolWindow;
      resizerAndComponent.first.setSize(maximizedProportion.second);
      state.setMaximizedProportion(null);
    }
    doLayout();
  }

  void reset() {
    for (Stripe stripe : stripes) {
      stripe.reset();
    }

    state = new ToolWindowPaneState();

    revalidate();
  }

  @FunctionalInterface
  interface Resizer {
    void setSize(int size);

    abstract class Splitter implements Resizer {
      ThreeComponentsSplitter mySplitter;

      Splitter(@NotNull ThreeComponentsSplitter splitter) {
        mySplitter = splitter;
      }

      static final class FirstComponent extends Splitter {
        FirstComponent(@NotNull ThreeComponentsSplitter splitter) {
          super(splitter);
        }

        @Override
        public void setSize(int size) {
          mySplitter.setFirstSize(size);
        }
      }

      static final class LastComponent extends Splitter {
        LastComponent(@NotNull ThreeComponentsSplitter splitter) {
          super(splitter);
        }

        @Override
        public void setSize(int size) {
          mySplitter.setLastSize(size);
        }
      }
    }

    abstract class LayeredPane implements Resizer {
      Component myComponent;

      LayeredPane(@NotNull Component component) {
        myComponent = component;
      }

      @Override
      public final void setSize(int size) {
        _setSize(size);
        if (myComponent.getParent() instanceof JComponent) {
          JComponent parent = (JComponent)myComponent;
          parent.revalidate();
          parent.repaint();
        }
      }

      abstract void _setSize(int size);

      static final class Left extends LayeredPane {
        Left(@NotNull Component component) {
          super(component);
        }

        @Override
        public void _setSize(int size) {
          myComponent.setSize(size, myComponent.getHeight());
        }
      }

      static final class Right extends LayeredPane {
        Right(@NotNull Component component) {
          super(component);
        }

        @Override
        public void _setSize(int size) {
          Rectangle bounds = myComponent.getBounds();
          int delta = size - bounds.width;
          bounds.x -= delta;
          bounds.width += delta;
          myComponent.setBounds(bounds);
        }
      }

      static class Top extends LayeredPane {
        Top(@NotNull Component component) {
          super(component);
        }

        @Override
        public void _setSize(int size) {
          myComponent.setSize(myComponent.getWidth(), size);
        }
      }

      static class Bottom extends LayeredPane {
        Bottom(@NotNull Component component) {
          super(component);
        }

        @Override
        public void _setSize(int size) {
          Rectangle bounds = myComponent.getBounds();
          int delta = size - bounds.height;
          bounds.y -= delta;
          bounds.height += delta;
          myComponent.setBounds(bounds);
        }
      }
    }
  }

  private void addAndSplitDockedComponentCmd(@NotNull JComponent newComponent,
                                             @NotNull WindowInfo info,
                                             boolean dirtyMode,
                                             @NotNull ToolWindowManagerImpl manager) {
    ToolWindowAnchor anchor = info.getAnchor();
    final class MySplitter extends OnePixelSplitter implements UISettingsListener {
      @Override
      public void uiSettingsChanged(@NotNull UISettings uiSettings) {
        if (anchor == ToolWindowAnchor.LEFT) {
          setOrientation(!uiSettings.getLeftHorizontalSplit());
        }
        else if (anchor == ToolWindowAnchor.RIGHT) {
          setOrientation(!uiSettings.getRightHorizontalSplit());
        }
      }

      @Override
      public String toString() {
        return "[" + getFirstComponent() + "|" + getSecondComponent() + "]";
      }
    }

    Splitter splitter = new MySplitter();
    splitter.setOrientation(anchor.isSplitVertically());
    if (!anchor.isHorizontal()) {
      splitter.setAllowSwitchOrientationByMouseClick(true);
      splitter.addPropertyChangeListener(evt -> {
        if (!Splitter.PROP_ORIENTATION.equals(evt.getPropertyName())) return;
        boolean isSplitterHorizontalNow = !splitter.isVertical();
        UISettings settings = UISettings.getInstance();
        if (anchor == ToolWindowAnchor.LEFT) {
          if (settings.getLeftHorizontalSplit() != isSplitterHorizontalNow) {
            settings.setLeftHorizontalSplit(isSplitterHorizontalNow);
            settings.fireUISettingsChanged();
          }
        }
        if (anchor == ToolWindowAnchor.RIGHT) {
          if (settings.getRightHorizontalSplit() != isSplitterHorizontalNow) {
            settings.setRightHorizontalSplit(isSplitterHorizontalNow);
            settings.fireUISettingsChanged();
          }
        }
      });
    }

    JComponent c = getComponentAt(anchor);
    // if all components are hidden for anchor we should find the second component to put in a splitter
    // otherwise we add empty splitter
    if (c == null) {
      List<ToolWindowEx> toolWindows = manager.getToolWindowsOn(anchor, Objects.requireNonNull(info.getId()));
      toolWindows.removeIf(window -> window == null || window.isSplitMode() == info.isSplit() || !window.isVisible());
      if (!toolWindows.isEmpty()) {
        c = ((ToolWindowImpl)toolWindows.get(0)).getDecoratorComponent();
      }
      if (c == null) {
        LOG.error("Empty splitter @ " + anchor + " during AddAndSplitDockedComponentCmd for " + info.getId());
      }
    }

    float newWeight;
    if (c instanceof InternalDecorator) {
      InternalDecorator oldComponent = (InternalDecorator)c;
      WindowInfoImpl oldInfo = ToolWindowManagerImpl.getRegisteredMutableInfoOrLogError(oldComponent);

      IJSwingUtilities.updateComponentTreeUI(oldComponent);
      IJSwingUtilities.updateComponentTreeUI(newComponent);

      if (info.isSplit()) {
        splitter.setFirstComponent(oldComponent);
        splitter.setSecondComponent(newComponent);
        float proportion = state.getPreferredSplitProportion(Objects.requireNonNull(oldInfo.getId()), normalizeWeigh(
          oldInfo.getSideWeight() / (oldInfo.getSideWeight() + info.getSideWeight())));
        splitter.setProportion(proportion);
        if (!anchor.isHorizontal() && !anchor.isSplitVertically()) {
          newWeight = normalizeWeigh(oldInfo.getWeight() + info.getWeight());
        }
        else {
          newWeight = normalizeWeigh(oldInfo.getWeight());
        }
      }
      else {
        splitter.setFirstComponent(newComponent);
        splitter.setSecondComponent(oldComponent);
        splitter.setProportion(normalizeWeigh(info.getSideWeight()));
        if (!anchor.isHorizontal() && !anchor.isSplitVertically()) {
          newWeight = normalizeWeigh(oldInfo.getWeight() + info.getWeight());
        }
        else {
          newWeight = normalizeWeigh(info.getWeight());
        }
      }
    }
    else {
      newWeight = normalizeWeigh(info.getWeight());
    }
    setComponent(splitter, anchor, newWeight);

    if (!dirtyMode) {
      layeredPane.validate();
      layeredPane.repaint();
    }
  }

  private void addSlidingComponent(@NotNull JComponent component, @NotNull WindowInfo info, boolean dirtyMode) {
    if (dirtyMode || !UISettings.getInstance().getAnimateWindows() || RemoteDesktopService.isRemoteSession()) {
      // not animated
      layeredPane.add(component, JLayeredPane.PALETTE_LAYER);
      layeredPane.setBoundsInPaletteLayer(component, info.getAnchor(), info.getWeight());
    }
    else {
      // Prepare top image. This image is scrolling over bottom image.
      Image topImage = layeredPane.getTopImage();

      Rectangle bounds = component.getBounds();

      useSafely(topImage.getGraphics(), topGraphics -> {
        component.putClientProperty(TEMPORARY_ADDED, Boolean.TRUE);
        try {
          layeredPane.add(component, JLayeredPane.PALETTE_LAYER);
          layeredPane.moveToFront(component);
          layeredPane.setBoundsInPaletteLayer(component, info.getAnchor(), info.getWeight());
          component.paint(topGraphics);
          layeredPane.remove(component);
        }
        finally {
          component.putClientProperty(TEMPORARY_ADDED, null);
        }
      });

      // prepare bottom image
      Image bottomImage = layeredPane.getBottomImage();

      Point2D bottomImageOffset = PaintUtil.getFractOffsetInRootPane(layeredPane);
      useSafely(bottomImage.getGraphics(), bottomGraphics -> {
        bottomGraphics.setClip(0, 0, bounds.width, bounds.height);
        bottomGraphics.translate(bottomImageOffset.getX() - bounds.x, bottomImageOffset.getY() - bounds.y);
        layeredPane.paint(bottomGraphics);
      });

      // start animation.
      Surface surface = new Surface(topImage, bottomImage, PaintUtil.negate(bottomImageOffset), 1, info.getAnchor(), UISettings.ANIMATION_DURATION);
      layeredPane.add(surface, JLayeredPane.PALETTE_LAYER);
      surface.setBounds(bounds);
      layeredPane.validate();
      layeredPane.repaint();

      surface.runMovement();
      layeredPane.remove(surface);
      layeredPane.add(component, JLayeredPane.PALETTE_LAYER);
    }

    if (!dirtyMode) {
      layeredPane.validate();
      layeredPane.repaint();
    }
  }

  private void removeSlidingComponent(@NotNull Component component, @NotNull WindowInfo info, boolean dirtyMode) {
    UISettings uiSettings = UISettings.getInstance();
    if (!dirtyMode && uiSettings.getAnimateWindows() && !RemoteDesktopService.isRemoteSession()) {
      Rectangle bounds = component.getBounds();
      // Prepare top image. This image is scrolling over bottom image. It contains
      // picture of component is being removed.
      Image topImage = layeredPane.getTopImage();
      useSafely(topImage.getGraphics(), component::paint);

      // Prepare bottom image. This image contains picture of component that is located
      // under the component to is being removed.
      Image bottomImage = layeredPane.getBottomImage();

      Point2D bottomImageOffset = PaintUtil.getFractOffsetInRootPane(layeredPane);
      useSafely(bottomImage.getGraphics(), bottomGraphics -> {
        layeredPane.remove(component);
        bottomGraphics.clipRect(0, 0, bounds.width, bounds.height);
        bottomGraphics.translate(bottomImageOffset.getX() - bounds.x, bottomImageOffset.getY() - bounds.y);
        layeredPane.paint(bottomGraphics);
      });

      // Remove component from the layered pane and start animation.
      Surface surface = new Surface(topImage, bottomImage, PaintUtil.negate(bottomImageOffset), -1, info.getAnchor(), UISettings.ANIMATION_DURATION);
      layeredPane.add(surface, JLayeredPane.PALETTE_LAYER);
      surface.setBounds(bounds);
      layeredPane.validate();
      layeredPane.repaint();

      surface.runMovement();
      layeredPane.remove(surface);
    }
    else {
      // not animated
      layeredPane.remove(component);
    }

    if (!dirtyMode) {
      layeredPane.validate();
      layeredPane.repaint();
    }
  }

  private static final class ImageRef extends SoftReference<BufferedImage> {
    private @Nullable BufferedImage myStrongRef;

    ImageRef(@NotNull BufferedImage image) {
      super(image);
      myStrongRef = image;
    }

    @Override
    public BufferedImage get() {
      if (myStrongRef != null) {
        BufferedImage img = myStrongRef;
        myStrongRef = null; // drop on first request
        return img;
      }
      return super.get();
    }
  }

  private static class ImageCache extends ScaleContext.Cache<ImageRef> {
    ImageCache(@NotNull Function<? super ScaleContext, ImageRef> imageProvider) {
      super(imageProvider);
    }

    public BufferedImage get(@NotNull ScaleContext ctx) {
      ImageRef ref = getOrProvide(ctx);
      BufferedImage image = SoftReference.dereference(ref);
      if (image != null) return image;
      clear(); // clear to recalculate the image
      return get(ctx); // first recalculated image will be non-null
    }
  }

  private final class MyLayeredPane extends JBLayeredPane {
    private final Function<ScaleContext, ImageRef> myImageProvider = __ -> {
      int width = Math.max(Math.max(1, getWidth()), frame.getWidth());
      int height = Math.max(Math.max(1, getHeight()), frame.getHeight());
      return new ImageRef(ImageUtil.createImage(getGraphicsConfiguration(), width, height, BufferedImage.TYPE_INT_RGB));
    };

    /*
     * These images are used to perform animated showing and hiding of components.
     * They are the member for performance reason.
     */
    private final ImageCache myBottomImageCache = new ImageCache(myImageProvider);
    private final ImageCache myTopImageCache = new ImageCache(myImageProvider);

    MyLayeredPane(@NotNull JComponent splitter) {
      setOpaque(false);
      add(splitter, JLayeredPane.DEFAULT_LAYER);
    }

    final Image getBottomImage() {
      return myBottomImageCache.get(ScaleContext.create(this));
    }

    final Image getTopImage() {
      return myTopImageCache.get(ScaleContext.create(this));
    }

    /**
     * When component size becomes larger then bottom and top images should be enlarged.
     */
    @Override
    public void doLayout() {
      final int width = getWidth();
      final int height = getHeight();
      if (width < 0 || height < 0) {
        return;
      }

      // Resize component at the DEFAULT layer. It should be only on component in that layer
      Component[] components = getComponentsInLayer(JLayeredPane.DEFAULT_LAYER);
      LOG.assertTrue(components.length <= 1);
      for (Component component : components) {
        component.setBounds(0, 0, getWidth(), getHeight());
      }
      // Resize components at the PALETTE layer
      components = getComponentsInLayer(JLayeredPane.PALETTE_LAYER);
      for (Component component : components) {
        if (!(component instanceof InternalDecorator)) {
          continue;
        }

        WindowInfo info = (((InternalDecorator)component)).getToolWindow().getWindowInfo();
        float weight = info.getAnchor().isHorizontal()
                       ? (float)component.getHeight() / getHeight()
                       : (float)component.getWidth() / getWidth();
        setBoundsInPaletteLayer(component, info.getAnchor(), weight);
      }
    }

    final void setBoundsInPaletteLayer(@NotNull Component component, @NotNull ToolWindowAnchor anchor, float weight) {
      if (weight < .0f) {
        weight = WindowInfoImpl.DEFAULT_WEIGHT;
      }
      else if (weight > 1.0f) {
        weight = 1.0f;
      }
      if (ToolWindowAnchor.TOP == anchor) {
        component.setBounds(0, 0, getWidth(), (int)(getHeight() * weight + .5f));
      }
      else if (ToolWindowAnchor.LEFT == anchor) {
        component.setBounds(0, 0, (int)(getWidth() * weight + .5f), getHeight());
      }
      else if (ToolWindowAnchor.BOTTOM == anchor) {
        final int height = (int)(getHeight() * weight + .5f);
        component.setBounds(0, getHeight() - height, getWidth(), height);
      }
      else if (ToolWindowAnchor.RIGHT == anchor) {
        final int width = (int)(getWidth() * weight + .5f);
        component.setBounds(getWidth() - width, 0, width, getHeight());
      }
      else {
        LOG.error("unknown anchor " + anchor);
      }
    }
  }

  void setStripesOverlayed(boolean value) {
    state.setStripesOverlaid(value);
    updateToolStripesVisibility(UISettings.getInstance());
  }

  private static float normalizeWeigh(final float weight) {
    if (weight <= 0) return WindowInfoImpl.DEFAULT_WEIGHT;
    if (weight >= 1) return 1 - WindowInfoImpl.DEFAULT_WEIGHT;
    return weight;
  }
}
