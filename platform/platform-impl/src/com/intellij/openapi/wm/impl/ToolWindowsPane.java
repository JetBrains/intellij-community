/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.wm.impl;

import com.intellij.ide.RemoteDesktopDetector;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.impl.commands.FinalizableCommand;
import com.intellij.reference.SoftReference;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * This panel contains all tool stripes and JLayeredPanle at the center area. All tool windows are
 * located inside this layered pane.
 *
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ToolWindowsPane extends JBLayeredPane implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.wm.impl.ToolWindowsPane");

  private final IdeFrameImpl myFrame;

  private final HashMap<String, StripeButton> myId2Button;
  private final HashMap<String, InternalDecorator> myId2Decorator;
  private final HashMap<StripeButton, WindowInfoImpl> myButton2Info;
  private final HashMap<InternalDecorator, WindowInfoImpl> myDecorator2Info;
  private final HashMap<String, Float> myId2SplitProportion;
  private Pair<ToolWindow, Integer> myMaximizedProportion = null;
  /**
   * This panel is the layered pane where all sliding tool windows are located. The DEFAULT
   * layer contains splitters. The PALETTE layer contains all sliding tool windows.
   */
  private final MyLayeredPane myLayeredPane;
  /*
   * Splitters.
   */
  private final ThreeComponentsSplitter myVerticalSplitter;
  private final ThreeComponentsSplitter myHorizontalSplitter;

  /*
   * Tool stripes.
   */
  private final Stripe myLeftStripe;
  private final Stripe myRightStripe;
  private final Stripe myBottomStripe;
  private final Stripe myTopStripe;

  private final ArrayList<Stripe> myStripes = new ArrayList<Stripe>();

  private final MyUISettingsListenerImpl myUISettingsListener;
  private final ToolWindowManagerImpl myManager;

  private boolean myStripesOverlayed;
  private final Disposable myDisposable = Disposer.newDisposable();
  private boolean myWidescreen = false;
  private boolean myLeftHorizontalSplit = false;
  private boolean myRightHorizontalSplit = false;

  ToolWindowsPane(final IdeFrameImpl frame, ToolWindowManagerImpl manager) {
    myManager = manager;

    setOpaque(false);
    myFrame = frame;
    myId2Button = new HashMap<String, StripeButton>();
    myId2Decorator = new HashMap<String, InternalDecorator>();
    myButton2Info = new HashMap<StripeButton, WindowInfoImpl>();
    myDecorator2Info = new HashMap<InternalDecorator, WindowInfoImpl>();
    myUISettingsListener = new MyUISettingsListenerImpl();
    myId2SplitProportion = new HashMap<String, Float>();

    // Splitters

    myVerticalSplitter = new ThreeComponentsSplitter(true);
    Disposer.register(this, myVerticalSplitter);
    myVerticalSplitter.setDividerWidth(0);
    myVerticalSplitter.setDividerMouseZoneSize(Registry.intValue("ide.splitter.mouseZone"));
    myVerticalSplitter.setBackground(Color.gray);
    myHorizontalSplitter = new ThreeComponentsSplitter(false);
    Disposer.register(this, myHorizontalSplitter);
    myHorizontalSplitter.setDividerWidth(0);
    myHorizontalSplitter.setDividerMouseZoneSize(Registry.intValue("ide.splitter.mouseZone"));
    myHorizontalSplitter.setBackground(Color.gray);
    myWidescreen = UISettings.getInstance().WIDESCREEN_SUPPORT;
    myLeftHorizontalSplit = UISettings.getInstance().LEFT_HORIZONTAL_SPLIT;
    myRightHorizontalSplit = UISettings.getInstance().RIGHT_HORIZONTAL_SPLIT;
    if (myWidescreen) {
      myHorizontalSplitter.setInnerComponent(myVerticalSplitter);
    }
    else {
      myVerticalSplitter.setInnerComponent(myHorizontalSplitter);
    }

    // Tool stripes

    myTopStripe = new Stripe(SwingConstants.TOP, manager);
    myStripes.add(myTopStripe);
    myLeftStripe = new Stripe(SwingConstants.LEFT, manager);
    myStripes.add(myLeftStripe);
    myBottomStripe = new Stripe(SwingConstants.BOTTOM, manager);
    myStripes.add(myBottomStripe);
    myRightStripe = new Stripe(SwingConstants.RIGHT, manager);
    myStripes.add(myRightStripe);

    updateToolStripesVisibility();

    // Layered pane

    myLayeredPane = new MyLayeredPane(myWidescreen ? myHorizontalSplitter : myVerticalSplitter);

    // Compose layout

    add(myTopStripe, JLayeredPane.POPUP_LAYER);
    add(myLeftStripe, JLayeredPane.POPUP_LAYER);
    add(myBottomStripe, JLayeredPane.POPUP_LAYER);
    add(myRightStripe, JLayeredPane.POPUP_LAYER);
    add(myLayeredPane, JLayeredPane.DEFAULT_LAYER);
  }

  @Override
  public void doLayout() {
    Dimension size = getSize();
    if (!myTopStripe.isVisible()) {
      myTopStripe.setBounds(0, 0, 0, 0);
      myBottomStripe.setBounds(0, 0, 0, 0);
      myLeftStripe.setBounds(0, 0, 0, 0);
      myRightStripe.setBounds(0, 0, 0, 0);
      myLayeredPane.setBounds(0, 0, getWidth(), getHeight());
    }
    else {
      Dimension topSize = myTopStripe.getPreferredSize();
      Dimension bottomSize = myBottomStripe.getPreferredSize();
      Dimension leftSize = myLeftStripe.getPreferredSize();
      Dimension rightSize = myRightStripe.getPreferredSize();

      myTopStripe.setBounds(0, 0, size.width, topSize.height);
      myLeftStripe.setBounds(0, topSize.height, leftSize.width, size.height - topSize.height - bottomSize.height);
      myRightStripe
        .setBounds(size.width - rightSize.width, topSize.height, rightSize.width, size.height - topSize.height - bottomSize.height);
      myBottomStripe.setBounds(0, size.height - bottomSize.height, size.width, bottomSize.height);

      if (UISettings.getInstance().HIDE_TOOL_STRIPES || UISettings.getInstance().PRESENTATION_MODE) {
        myLayeredPane.setBounds(0, 0, size.width, size.height);
      }
      else {
        myLayeredPane.setBounds(leftSize.width, topSize.height, size.width - leftSize.width - rightSize.width,
                                size.height - topSize.height - bottomSize.height);
      }
    }
  }

  @Override
  protected void paintChildren(Graphics g) {
    super.paintChildren(g);
  }

  /**
   * Invoked when enclosed frame is being shown.
   */
  public final void addNotify() {
    super.addNotify();
    if (ScreenUtil.isStandardAddRemoveNotify(this)) {
      UISettings.getInstance().addUISettingsListener(myUISettingsListener, myDisposable);
    }
  }

  /**
   * Invoked when enclosed frame is being disposed.
   */
  public final void removeNotify() {
    if (ScreenUtil.isStandardAddRemoveNotify(this)) {
      Disposer.dispose(myDisposable);
    }
    super.removeNotify();
  }

  public Project getProject() {
    return myFrame.getProject();
  }

  /**
   * Creates command which adds button into the specified tool stripe.
   * Command uses copy of passed <code>info</code> object.
   *
   * @param button         button which should be added.
   * @param info           window info for the corresponded tool window.
   * @param comparator     which is used to sort buttons within the stripe.
   * @param finishCallBack invoked when the command is completed.
   */
  final FinalizableCommand createAddButtonCmd(final StripeButton button,
                                              final WindowInfoImpl info,
                                              final Comparator<StripeButton> comparator,
                                              final Runnable finishCallBack) {
    final WindowInfoImpl copiedInfo = info.copy();
    myId2Button.put(copiedInfo.getId(), button);
    myButton2Info.put(button, copiedInfo);
    return new AddToolStripeButtonCmd(button, copiedInfo, comparator, finishCallBack);
  }

  /**
   * Creates command which shows tool window with specified set of parameters.
   * Command uses cloned copy of passed <code>info</code> object.
   *
   * @param dirtyMode if <code>true</code> then JRootPane will not be validated and repainted after adding
   *                  the decorator. Moreover in this (dirty) mode animation doesn't work.
   */
  final FinalizableCommand createAddDecoratorCmd(
    final InternalDecorator decorator,
    final WindowInfoImpl info,
    final boolean dirtyMode,
    final Runnable finishCallBack
  ) {
    final WindowInfoImpl copiedInfo = info.copy();
    final String id = copiedInfo.getId();

    myDecorator2Info.put(decorator, copiedInfo);
    myId2Decorator.put(id, decorator);

    if (info.isDocked()) {
      WindowInfoImpl sideInfo = getDockedInfoAt(info.getAnchor(), !info.isSplit());
      if (sideInfo == null) {
        return new AddDockedComponentCmd(decorator, info, dirtyMode, finishCallBack);
      }
      else {
        return new AddAndSplitDockedComponentCmd(decorator, info, dirtyMode, finishCallBack);
      }
    }
    else if (info.isSliding()) {
      return new AddSlidingComponentCmd(decorator, info, dirtyMode, finishCallBack);
    }
    else {
      throw new IllegalArgumentException("Unknown window type: " + info.getType());
    }
  }

  /**
   * Creates command which removes tool button from tool stripe.
   *
   * @param id <code>ID</code> of the button to be removed.
   */
  final FinalizableCommand createRemoveButtonCmd(final String id, final Runnable finishCallBack) {
    final StripeButton button = getButtonById(id);
    final WindowInfoImpl info = getButtonInfoById(id);
    //
    myButton2Info.remove(button);
    myId2Button.remove(id);
    return new RemoveToolStripeButtonCmd(button, info, finishCallBack);
  }

  /**
   * Creates command which hides tool window with specified set of parameters.
   *
   * @param dirtyMode if <code>true</code> then JRootPane will not be validated and repainted after removing
   *                  the decorator. Moreover in this (dirty) mode animation doesn't work.
   */
  final FinalizableCommand createRemoveDecoratorCmd(final String id, final boolean dirtyMode, final Runnable finishCallBack) {
    final Component decorator = getDecoratorById(id);
    final WindowInfoImpl info = getDecoratorInfoById(id);

    myDecorator2Info.remove(decorator);
    myId2Decorator.remove(id);

    WindowInfoImpl sideInfo = getDockedInfoAt(info.getAnchor(), !info.isSplit());

    if (info.isDocked()) {
      if (sideInfo == null) {
        return new RemoveDockedComponentCmd(info, dirtyMode, finishCallBack);
      }
      else {
        return new RemoveSplitAndDockedComponentCmd(info, dirtyMode, finishCallBack);
      }
    }
    else if (info.isSliding()) {
      return new RemoveSlidingComponentCmd(decorator, info, dirtyMode, finishCallBack);
    }
    else {
      throw new IllegalArgumentException("Unknown window type");
    }
  }

  /**
   * Creates command which sets specified document component.
   *
   * @param component component to be set.
   */
  final FinalizableCommand createSetEditorComponentCmd(final JComponent component, final Runnable finishCallBack) {
    return new SetEditorComponentCmd(component, finishCallBack);
  }

  final FinalizableCommand createUpdateButtonPositionCmd(String id, final Runnable finishCallback) {
    return new UpdateButtonPositionCmd(id, finishCallback);
  }

  public final JComponent getMyLayeredPane() {
    return myLayeredPane;
  }

  @Nullable
  private StripeButton getButtonById(final String id) {
    return myId2Button.get(id);
  }

  private Component getDecoratorById(final String id) {
    return myId2Decorator.get(id);
  }

  /**
   * @param id <code>ID</code> of tool stripe butoon.
   * @return <code>WindowInfo</code> associated with specified tool stripe button.
   */
  private WindowInfoImpl getButtonInfoById(final String id) {
    return myButton2Info.get(myId2Button.get(id));
  }

  /**
   * @param id <code>ID</code> of decorator.
   * @return <code>WindowInfo</code> associated with specified window decorator.
   */
  private WindowInfoImpl getDecoratorInfoById(final String id) {
    return myDecorator2Info.get(myId2Decorator.get(id));
  }

  /**
   * Sets (docks) specified component to the specified anchor.
   */
  private void setComponent(final JComponent component, final ToolWindowAnchor anchor, final float weight) {
    if (ToolWindowAnchor.TOP == anchor) {
      myVerticalSplitter.setFirstComponent(component);
      myVerticalSplitter.setFirstSize((int)(myLayeredPane.getHeight() * weight));
    }
    else if (ToolWindowAnchor.LEFT == anchor) {
      myHorizontalSplitter.setFirstComponent(component);
      myHorizontalSplitter.setFirstSize((int)(myLayeredPane.getWidth() * weight));
    }
    else if (ToolWindowAnchor.BOTTOM == anchor) {
      myVerticalSplitter.setLastComponent(component);
      myVerticalSplitter.setLastSize((int)(myLayeredPane.getHeight() * weight));
    }
    else if (ToolWindowAnchor.RIGHT == anchor) {
      myHorizontalSplitter.setLastComponent(component);
      myHorizontalSplitter.setLastSize((int)(myLayeredPane.getWidth() * weight));
    }
    else {
      LOG.error("unknown anchor: " + anchor);
    }
  }

  private JComponent getComponentAt(ToolWindowAnchor anchor) {
    if (ToolWindowAnchor.TOP == anchor) {
      return myVerticalSplitter.getFirstComponent();
    }
    else if (ToolWindowAnchor.LEFT == anchor) {
      return myHorizontalSplitter.getFirstComponent();
    }
    else if (ToolWindowAnchor.BOTTOM == anchor) {
      return myVerticalSplitter.getLastComponent();
    }
    else if (ToolWindowAnchor.RIGHT == anchor) {
      return myHorizontalSplitter.getLastComponent();
    }
    else {
      LOG.error("unknown anchor: " + anchor);
      return null;
    }
  }

  private float getPreferredSplitProportion(String id, float defaultValue) {
    Float f = myId2SplitProportion.get(id);
    return (f == null ? defaultValue : f);
  }

  private WindowInfoImpl getDockedInfoAt(ToolWindowAnchor anchor, boolean side) {
    for (WindowInfoImpl info : myDecorator2Info.values()) {
      if (info.isVisible() && info.isDocked() && info.getAnchor() == anchor && side == info.isSplit()) {
        return info;
      }
    }

    return null;
  }

  public void setDocumentComponent(final JComponent component) {
    (myWidescreen ? myVerticalSplitter : myHorizontalSplitter).setInnerComponent(component);
  }

  private void updateToolStripesVisibility() {
    boolean oldVisible = myLeftStripe.isVisible();

    final boolean showButtons = !UISettings.getInstance().HIDE_TOOL_STRIPES && !UISettings.getInstance().PRESENTATION_MODE;
    boolean visible = showButtons || myStripesOverlayed;
    myLeftStripe.setVisible(visible);
    myRightStripe.setVisible(visible);
    myTopStripe.setVisible(visible);
    myBottomStripe.setVisible(visible);

    boolean overlayed = !showButtons && myStripesOverlayed;

    myLeftStripe.setOverlayed(overlayed);
    myRightStripe.setOverlayed(overlayed);
    myTopStripe.setOverlayed(overlayed);
    myBottomStripe.setOverlayed(overlayed);


    if (oldVisible != visible) {
      revalidate();
      repaint();
    }
  }

  @Nullable
  Stripe getStripeFor(String id) {
    ToolWindow window = myManager.getToolWindow(id);
    if (window == null) {
      return null;
    }

    final ToolWindowAnchor anchor = myManager.getToolWindow(id).getAnchor();
    if (ToolWindowAnchor.TOP == anchor) {
      return myTopStripe;
    }
    else if (ToolWindowAnchor.BOTTOM == anchor) {
      return myBottomStripe;
    }
    else if (ToolWindowAnchor.LEFT == anchor) {
      return myLeftStripe;
    }
    else if (ToolWindowAnchor.RIGHT == anchor) {
      return myRightStripe;
    }

    throw new IllegalArgumentException("Anchor=" + anchor);
  }

  @Nullable
  Stripe getStripeFor(final Rectangle screenRec, Stripe preferred) {
    if (preferred.containsScreen(screenRec)) {
      return myStripes.get(myStripes.indexOf(preferred));
    }

    for (Stripe each : myStripes) {
      if (each.containsScreen(screenRec)) {
        return myStripes.get(myStripes.indexOf(each));
      }
    }

    return null;
  }

  void startDrag() {
    for (Stripe each : myStripes) {
      each.startDrag();
    }
  }

  void stopDrag() {
    for (Stripe each : myStripes) {
      each.stopDrag();
    }
  }

  public void stretchWidth(ToolWindow wnd, int value) {
    stretch(wnd, value);
  }

  public void stretchHeight(ToolWindow wnd, int value) {
    stretch(wnd, value);
  }

  private void stretch(ToolWindow wnd, int value) {
    Pair<Resizer, Component> pair = findResizerAndComponent(wnd);
    if (pair == null) return;

    boolean vertical = wnd.getAnchor() == ToolWindowAnchor.TOP || wnd.getAnchor() == ToolWindowAnchor.BOTTOM;
    int actualSize = (vertical ? pair.second.getHeight() : pair.second.getWidth()) + value;
    boolean first = wnd.getAnchor() == ToolWindowAnchor.LEFT  || wnd.getAnchor() == ToolWindowAnchor.TOP;
    int maxValue = vertical ? myVerticalSplitter.getMaxSize(first) : myHorizontalSplitter.getMaxSize(first);
    int minValue = vertical ? myVerticalSplitter.getMinSize(first) : myHorizontalSplitter.getMinSize(first);;

    pair.first.setSize(Math.max(minValue, Math.min(maxValue, actualSize)));
  }

  @Nullable
  private Pair<Resizer, Component> findResizerAndComponent(ToolWindow wnd) {
    if (!wnd.isVisible()) return null;

    Resizer resizer = null;
    Component cmp = null;

    if (wnd.getType() == ToolWindowType.DOCKED) {
      cmp = getComponentAt(wnd.getAnchor());

      if (cmp != null) {
        if (wnd.getAnchor().isHorizontal()) {
          resizer = myVerticalSplitter.getFirstComponent() == cmp
                    ? new Resizer.Splitter.FirstComponent(myVerticalSplitter)
                    : new Resizer.Splitter.LastComponent(myVerticalSplitter);
        }
        else {
          resizer = myHorizontalSplitter.getFirstComponent() == cmp
                    ? new Resizer.Splitter.FirstComponent(myHorizontalSplitter)
                    : new Resizer.Splitter.LastComponent(myHorizontalSplitter);
        }
      }
    }
    else if (wnd.getType() == ToolWindowType.SLIDING) {
      cmp = wnd.getComponent();
      while (cmp != null) {
        if (cmp.getParent() == myLayeredPane) break;
        cmp = cmp.getParent();
      }

      if (cmp != null) {
        if (wnd.getAnchor() == ToolWindowAnchor.TOP) {
          resizer = new Resizer.LayeredPane.Top(cmp);
        }
        else if (wnd.getAnchor() == ToolWindowAnchor.BOTTOM) {
          resizer = new Resizer.LayeredPane.Bottom(cmp);
        }
        else if (wnd.getAnchor() == ToolWindowAnchor.LEFT) {
          resizer = new Resizer.LayeredPane.Left(cmp);
        }
        else if (wnd.getAnchor() == ToolWindowAnchor.RIGHT) {
          resizer = new Resizer.LayeredPane.Right(cmp);
        }
      }
    }

    return resizer != null ? Pair.create(resizer, cmp) : null;
  }

  private void updateLayout() {
    if (myWidescreen != UISettings.getInstance().WIDESCREEN_SUPPORT) {
      JComponent documentComponent = (myWidescreen ? myVerticalSplitter : myHorizontalSplitter).getInnerComponent();
      myWidescreen = UISettings.getInstance().WIDESCREEN_SUPPORT;
      if (myWidescreen) {
        myVerticalSplitter.setInnerComponent(null);
        myHorizontalSplitter.setInnerComponent(myVerticalSplitter);
      }
      else {
        myHorizontalSplitter.setInnerComponent(null);
        myVerticalSplitter.setInnerComponent(myHorizontalSplitter);
      }
      myLayeredPane.remove(myWidescreen ? myVerticalSplitter : myHorizontalSplitter);
      myLayeredPane.add(myWidescreen ? myHorizontalSplitter : myVerticalSplitter, DEFAULT_LAYER);
      setDocumentComponent(documentComponent);
    }
    if (myLeftHorizontalSplit != UISettings.getInstance().LEFT_HORIZONTAL_SPLIT) {
      JComponent component = getComponentAt(ToolWindowAnchor.LEFT);
      if (component instanceof Splitter) {
        Splitter splitter = (Splitter)component;
        InternalDecorator first = (InternalDecorator)splitter.getFirstComponent();
        InternalDecorator second = (InternalDecorator)splitter.getSecondComponent();
        setComponent(splitter, ToolWindowAnchor.LEFT, ToolWindowAnchor.LEFT.isSplitVertically()
                                                      ? first.getWindowInfo().getWeight()
                                                      : first.getWindowInfo().getWeight() + second.getWindowInfo().getWeight());
      }
      myLeftHorizontalSplit = UISettings.getInstance().LEFT_HORIZONTAL_SPLIT;
    }
    if (myRightHorizontalSplit != UISettings.getInstance().RIGHT_HORIZONTAL_SPLIT) {
      JComponent component = getComponentAt(ToolWindowAnchor.RIGHT);
      if (component instanceof Splitter) {
        Splitter splitter = (Splitter)component;
        InternalDecorator first = (InternalDecorator)splitter.getFirstComponent();
        InternalDecorator second = (InternalDecorator)splitter.getSecondComponent();
        setComponent(splitter, ToolWindowAnchor.RIGHT, ToolWindowAnchor.RIGHT.isSplitVertically()
                                                       ? first.getWindowInfo().getWeight()
                                                       : first.getWindowInfo().getWeight() + second.getWindowInfo().getWeight());
      }
      myRightHorizontalSplit = UISettings.getInstance().RIGHT_HORIZONTAL_SPLIT;
    }
  }

  public boolean isMaximized(@NotNull ToolWindow wnd) {
      return myMaximizedProportion != null && myMaximizedProportion.first == wnd;
  }

  public void setMaximized(@NotNull ToolWindow wnd, boolean maximized) {
    Pair<Resizer, Component> resizerAndComponent = findResizerAndComponent(wnd);
    if (resizerAndComponent == null) return;

    if (!maximized) {
      ToolWindow maximizedWindow = myMaximizedProportion.first;
      assert maximizedWindow == wnd;
      resizerAndComponent.first.setSize(myMaximizedProportion.second);
      myMaximizedProportion = null;
    } else {
      int size = wnd.getAnchor().isHorizontal() ? resizerAndComponent.second.getHeight() : resizerAndComponent.second.getWidth();
      stretch(wnd, Short.MAX_VALUE);
      myMaximizedProportion = Pair.create(wnd, size);
    }
    doLayout();
  }


  interface Resizer {
    void setSize(int size);


    abstract class Splitter implements Resizer {
      ThreeComponentsSplitter mySplitter;

      Splitter(ThreeComponentsSplitter splitter) {
        mySplitter = splitter;
      }

      static class FirstComponent extends Splitter {
        FirstComponent(ThreeComponentsSplitter splitter) {
          super(splitter);
        }

        public void setSize(int size) {
          mySplitter.setFirstSize(size);
        }
      }

      static class LastComponent extends Splitter {
        LastComponent(ThreeComponentsSplitter splitter) {
          super(splitter);
        }

        public void setSize(int size) {
          mySplitter.setLastSize(size);
        }
      }
    }

    abstract class LayeredPane implements Resizer {
      Component myComponent;

      protected LayeredPane(Component component) {
        myComponent = component;
      }

      public final void setSize(int size) {
        _setSize(size);
        if (myComponent.getParent() instanceof JComponent) {
          JComponent parent = (JComponent)myComponent;
          parent.revalidate();
          parent.repaint();
        }
      }

      abstract void _setSize(int size);

      static class Left extends LayeredPane {

        Left(Component component) {
          super(component);
        }

        public void _setSize(int size) {
          myComponent.setSize(size, myComponent.getHeight());
        }
      }

      static class Right extends LayeredPane {
        Right(Component component) {
          super(component);
        }

        public void _setSize(int size) {
          Rectangle bounds = myComponent.getBounds();
          int delta = size - bounds.width;
          bounds.x -= delta;
          bounds.width += delta;
          myComponent.setBounds(bounds);
        }
      }

      static class Top extends LayeredPane {
        Top(Component component) {
          super(component);
        }

        public void _setSize(int size) {
          myComponent.setSize(myComponent.getWidth(), size);
        }
      }

      static class Bottom extends LayeredPane {
        Bottom(Component component) {
          super(component);
        }

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

  private final class AddDockedComponentCmd extends FinalizableCommand {
    private final JComponent myComponent;
    private final WindowInfoImpl myInfo;
    private final boolean myDirtyMode;

    public AddDockedComponentCmd(final JComponent component,
                                 final WindowInfoImpl info,
                                 final boolean dirtyMode,
                                 final Runnable finishCallBack) {
      super(finishCallBack);
      myComponent = component;
      myInfo = info;
      myDirtyMode = dirtyMode;
    }

    public final void run() {
      try {
        final ToolWindowAnchor anchor = myInfo.getAnchor();
        setComponent(myComponent, anchor, normalizeWeigh(myInfo.getWeight()));
        if (!myDirtyMode) {
          myLayeredPane.validate();
          myLayeredPane.repaint();
        }
      }
      finally {
        finish();
      }
    }
  }

  private final class AddAndSplitDockedComponentCmd extends FinalizableCommand {
    private final JComponent myNewComponent;
    private final WindowInfoImpl myInfo;
    private final boolean myDirtyMode;

    private AddAndSplitDockedComponentCmd(final JComponent newComponent,
                                          final WindowInfoImpl info, final boolean dirtyMode, final Runnable finishCallBack) {
      super(finishCallBack);
      myNewComponent = newComponent;
      myInfo = info;
      myDirtyMode = dirtyMode;
    }

    public void run() {
      try {
        float newWeight;
        final ToolWindowAnchor anchor = myInfo.getAnchor();
        final Disposable splitterDisposable = new Disposable() {
          @Override
          public void dispose() {
          }
        };
        Disposer.register(myDisposable, splitterDisposable);
        final Splitter splitter = new Splitter(anchor.isSplitVertically()) {
          @Override
          public void removeNotify() {
            super.removeNotify();
            Disposer.dispose(splitterDisposable);
          }
        };
        if (!anchor.isHorizontal()) {
          UISettings.getInstance().addUISettingsListener(new UISettingsListener() {
            @Override
            public void uiSettingsChanged(UISettings source) {
              if (anchor == ToolWindowAnchor.LEFT) {
                splitter.setOrientation(!source.LEFT_HORIZONTAL_SPLIT);
              }
              if (anchor == ToolWindowAnchor.RIGHT) {
                splitter.setOrientation(!source.RIGHT_HORIZONTAL_SPLIT);
              }
            }
          }, splitterDisposable);
          splitter.setAllowSwitchOrientationByMouseClick(true);
          splitter.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
              if (!Splitter.PROP_ORIENTATION.equals(evt.getPropertyName())) return;
              boolean isSplitterHorizontalNow = !splitter.isVertical();
              UISettings settings = UISettings.getInstance();
              if (anchor == ToolWindowAnchor.LEFT) {
                if (settings.LEFT_HORIZONTAL_SPLIT != isSplitterHorizontalNow) {
                  settings.LEFT_HORIZONTAL_SPLIT = isSplitterHorizontalNow;
                  settings.fireUISettingsChanged();
                }
              }
              if (anchor == ToolWindowAnchor.RIGHT) {
                if (settings.RIGHT_HORIZONTAL_SPLIT != isSplitterHorizontalNow) {
                  settings.RIGHT_HORIZONTAL_SPLIT = isSplitterHorizontalNow;
                  settings.fireUISettingsChanged();
                }
              }
            }
          });
        }
        JComponent c = getComponentAt(anchor);
        if (c instanceof InternalDecorator) {
          InternalDecorator oldComponent = (InternalDecorator)c;
          if (myInfo.isSplit()) {
            splitter.setFirstComponent(oldComponent);
            splitter.setSecondComponent(myNewComponent);
            float proportion = getPreferredSplitProportion(oldComponent.getWindowInfo().getId(),
                                                           normalizeWeigh(oldComponent.getWindowInfo().getSideWeight() /
                                                                          (oldComponent.getWindowInfo().getSideWeight() +
                                                                           myInfo.getSideWeight())));
            splitter.setProportion(proportion);
            if (!anchor.isHorizontal() && !anchor.isSplitVertically()) {
              newWeight = normalizeWeigh(oldComponent.getWindowInfo().getWeight() + myInfo.getWeight());
            }
            else {
              newWeight = normalizeWeigh(oldComponent.getWindowInfo().getWeight());
            }
          }
          else {
            splitter.setFirstComponent(myNewComponent);
            splitter.setSecondComponent(oldComponent);
            splitter.setProportion(normalizeWeigh(myInfo.getSideWeight()));
            if (!anchor.isHorizontal() && !anchor.isSplitVertically()) {
              newWeight = normalizeWeigh(oldComponent.getWindowInfo().getWeight() + myInfo.getWeight());
            }
            else {
              newWeight = normalizeWeigh(myInfo.getWeight());
            }
          }
        } else {
          newWeight = normalizeWeigh(myInfo.getWeight());
        }
        setComponent(splitter, anchor, newWeight);

        if (!myDirtyMode) {
          myLayeredPane.validate();
          myLayeredPane.repaint();
        }
      }
      finally {
        finish();
      }
    }
  }

  private final class AddSlidingComponentCmd extends FinalizableCommand {
    private final Component myComponent;
    private final WindowInfoImpl myInfo;
    private final boolean myDirtyMode;

    public AddSlidingComponentCmd(final Component component,
                                  final WindowInfoImpl info,
                                  final boolean dirtyMode,
                                  final Runnable finishCallBack) {
      super(finishCallBack);
      myComponent = component;
      myInfo = info;
      myDirtyMode = dirtyMode;
    }
    public final void run() {
      try {
        // Show component.
        final UISettings uiSettings = UISettings.getInstance();
        if (!myDirtyMode && uiSettings.ANIMATE_WINDOWS && !RemoteDesktopDetector.isRemoteSession()) {
          // Prepare top image. This image is scrolling over bottom image.
          final Image topImage = myLayeredPane.getTopImage();
          final Graphics topGraphics = topImage.getGraphics();

          Rectangle bounds;

          try {
            myLayeredPane.add(myComponent, JLayeredPane.PALETTE_LAYER);
            myLayeredPane.moveToFront(myComponent);
            myLayeredPane.setBoundsInPaletteLayer(myComponent, myInfo.getAnchor(), myInfo.getWeight());
            bounds = myComponent.getBounds();
            myComponent.paint(topGraphics);
            myLayeredPane.remove(myComponent);
          }
          finally {
            topGraphics.dispose();
          }
          // Prepare bottom image.
          final Image bottomImage = myLayeredPane.getBottomImage();
          final Graphics bottomGraphics = bottomImage.getGraphics();
          try {
            bottomGraphics.setClip(0, 0, bounds.width, bounds.height);
            bottomGraphics.translate(-bounds.x, -bounds.y);
            myLayeredPane.paint(bottomGraphics);
          }
          finally {
            bottomGraphics.dispose();
          }
          // Start animation.
          final Surface surface = new Surface(topImage, bottomImage, 1, myInfo.getAnchor(), uiSettings.ANIMATION_DURATION);
          myLayeredPane.add(surface, JLayeredPane.PALETTE_LAYER);
          surface.setBounds(bounds);
          myLayeredPane.validate();
          myLayeredPane.repaint();

          surface.runMovement();
          myLayeredPane.remove(surface);
          myLayeredPane.add(myComponent, JLayeredPane.PALETTE_LAYER);
        }
        else { // not animated
          myLayeredPane.add(myComponent, JLayeredPane.PALETTE_LAYER);
          myLayeredPane.setBoundsInPaletteLayer(myComponent, myInfo.getAnchor(), myInfo.getWeight());
        }
        if (!myDirtyMode) {
          myLayeredPane.validate();
          myLayeredPane.repaint();
        }
      }
      finally {
        finish();
      }
    }
  }

  private final class AddToolStripeButtonCmd extends FinalizableCommand {
    private final StripeButton myButton;
    private final WindowInfoImpl myInfo;
    private final Comparator<StripeButton> myComparator;

    public AddToolStripeButtonCmd(final StripeButton button,
                                  final WindowInfoImpl info,
                                  final Comparator<StripeButton> comparator,
                                  final Runnable finishCallBack) {
      super(finishCallBack);
      myButton = button;
      myInfo = info;
      myComparator = comparator;
    }

    public final void run() {
      try {
        final ToolWindowAnchor anchor = myInfo.getAnchor();
        if (ToolWindowAnchor.TOP == anchor) {
          myTopStripe.addButton(myButton, myComparator);
        }
        else if (ToolWindowAnchor.LEFT == anchor) {
          myLeftStripe.addButton(myButton, myComparator);
        }
        else if (ToolWindowAnchor.BOTTOM == anchor) {
          myBottomStripe.addButton(myButton, myComparator);
        }
        else if (ToolWindowAnchor.RIGHT == anchor) {
          myRightStripe.addButton(myButton, myComparator);
        }
        else {
          LOG.error("unknown anchor: " + anchor);
        }
        validate();
        repaint();
      }
      finally {
        finish();
      }
    }
  }

  private final class RemoveToolStripeButtonCmd extends FinalizableCommand {
    private final StripeButton myButton;
    private final WindowInfoImpl myInfo;

    public RemoveToolStripeButtonCmd(final StripeButton button, final WindowInfoImpl info, final Runnable finishCallBack) {
      super(finishCallBack);
      myButton = button;
      myInfo = info;
    }

    public final void run() {
      try {
        final ToolWindowAnchor anchor = myInfo.getAnchor();
        if (ToolWindowAnchor.TOP == anchor) {
          myTopStripe.removeButton(myButton);
        }
        else if (ToolWindowAnchor.LEFT == anchor) {
          myLeftStripe.removeButton(myButton);
        }
        else if (ToolWindowAnchor.BOTTOM == anchor) {
          myBottomStripe.removeButton(myButton);
        }
        else if (ToolWindowAnchor.RIGHT == anchor) {
          myRightStripe.removeButton(myButton);
        }
        else {
          LOG.error("unknown anchor: " + anchor);
        }
        validate();
        repaint();
      }
      finally {
        finish();
      }
    }
  }

  private final class RemoveDockedComponentCmd extends FinalizableCommand {
    private final WindowInfoImpl myInfo;
    private final boolean myDirtyMode;

    public RemoveDockedComponentCmd(final WindowInfoImpl info, final boolean dirtyMode, final Runnable finishCallBack) {
      super(finishCallBack);
      myInfo = info;
      myDirtyMode = dirtyMode;
    }

    public final void run() {
      try {
        setComponent(null, myInfo.getAnchor(), 0);
        if (!myDirtyMode) {
          myLayeredPane.validate();
          myLayeredPane.repaint();
        }
      }
      finally {
        finish();
      }
    }
  }

  private final class RemoveSplitAndDockedComponentCmd extends FinalizableCommand {
    private final WindowInfoImpl myInfo;
    private final boolean myDirtyMode;

    private RemoveSplitAndDockedComponentCmd(final WindowInfoImpl info,
                                             boolean dirtyMode,
                                             final Runnable finishCallBack) {
      super(finishCallBack);
      myInfo = info;
      myDirtyMode = dirtyMode;
    }

    public void run() {
      try {
        ToolWindowAnchor anchor = myInfo.getAnchor();
        JComponent c = getComponentAt(anchor);
        if (c instanceof Splitter) {
          Splitter splitter = (Splitter)c;
          final InternalDecorator component =
            myInfo.isSplit() ? (InternalDecorator)splitter.getFirstComponent() : (InternalDecorator)splitter.getSecondComponent();
          if (myInfo.isSplit() && component != null) {
              myId2SplitProportion.put(component.getWindowInfo().getId(), splitter.getProportion());
          }
          setComponent(component, anchor, component != null ? component.getWindowInfo().getWeight() : 0);
        } else {
          setComponent(null, anchor, 0);
        }
        if (!myDirtyMode) {
          myLayeredPane.validate();
          myLayeredPane.repaint();
        }
      }
      finally {
        finish();
      }
    }
  }

  private final class RemoveSlidingComponentCmd extends FinalizableCommand {
    private final Component myComponent;
    private final WindowInfoImpl myInfo;
    private final boolean myDirtyMode;

    public RemoveSlidingComponentCmd(Component component, WindowInfoImpl info, boolean dirtyMode, Runnable finishCallBack) {
      super(finishCallBack);
      myComponent = component;
      myInfo = info;
      myDirtyMode = dirtyMode;
    }
    public final void run() {
      try {
        final UISettings uiSettings = UISettings.getInstance();
        if (!myDirtyMode && uiSettings.ANIMATE_WINDOWS && !RemoteDesktopDetector.isRemoteSession()) {
          final Rectangle bounds = myComponent.getBounds();
          // Prepare top image. This image is scrolling over bottom image. It contains
          // picture of component is being removed.
          final Image topImage = myLayeredPane.getTopImage();
          final Graphics topGraphics = topImage.getGraphics();
          try {
            myComponent.paint(topGraphics);
          }
          finally {
            topGraphics.dispose();
          }
          // Prepare bottom image. This image contains picture of component that is located
          // under the component to is being removed.
          final Image bottomImage = myLayeredPane.getBottomImage();
          final Graphics bottomGraphics = bottomImage.getGraphics();
          try {
            myLayeredPane.remove(myComponent);
            bottomGraphics.clipRect(0, 0, bounds.width, bounds.height);
            bottomGraphics.translate(-bounds.x, -bounds.y);
            myLayeredPane.paint(bottomGraphics);
          }
          finally {
            bottomGraphics.dispose();
          }
          // Remove component from the layered pane and start animation.
          final Surface surface = new Surface(topImage, bottomImage, -1, myInfo.getAnchor(), uiSettings.ANIMATION_DURATION);
          myLayeredPane.add(surface, JLayeredPane.PALETTE_LAYER);
          surface.setBounds(bounds);
          myLayeredPane.validate();
          myLayeredPane.repaint();

          surface.runMovement();
          myLayeredPane.remove(surface);
        }
        else { // not animated
          myLayeredPane.remove(myComponent);
        }
        if (!myDirtyMode) {
          myLayeredPane.validate();
          myLayeredPane.repaint();
        }
      }
      finally {
        finish();
      }
    }
  }

  private final class SetEditorComponentCmd extends FinalizableCommand {
    private final JComponent myComponent;

    public SetEditorComponentCmd(final JComponent component, final Runnable finishCallBack) {
      super(finishCallBack);
      myComponent = component;
    }

    public void run() {
      try {
        setDocumentComponent(myComponent);
        myLayeredPane.validate();
        myLayeredPane.repaint();
      }
      finally {
        finish();
      }
    }
  }

  private final class UpdateButtonPositionCmd extends FinalizableCommand {
    private final String myId;

    private UpdateButtonPositionCmd(String id, final Runnable finishCallBack) {
      super(finishCallBack);
      myId = id;
    }

    public void run() {
      try {
        StripeButton stripeButton = getButtonById(myId);
        if (stripeButton == null) {
          return;
        }

        WindowInfoImpl info = stripeButton.getWindowInfo();
        ToolWindowAnchor anchor = info.getAnchor();

        if (ToolWindowAnchor.TOP == anchor) {
          myTopStripe.revalidate();
        }
        else if (ToolWindowAnchor.LEFT == anchor) {
          myLeftStripe.revalidate();
        }
        else if (ToolWindowAnchor.BOTTOM == anchor) {
          myBottomStripe.revalidate();
        }
        else if (ToolWindowAnchor.RIGHT == anchor) {
          myRightStripe.revalidate();
        }
        else {
          LOG.error("unknown anchor: " + anchor);
        }
      }
      finally {
        finish();
      }
    }
  }

  private final class MyUISettingsListenerImpl implements UISettingsListener {
    public final void uiSettingsChanged(final UISettings source) {
      updateToolStripesVisibility();
      updateLayout();
    }
  }
  private final class MyLayeredPane extends JBLayeredPane {
    /*
     * These images are used to perform animated showing and hiding of components.
     * They are the member for performance reason.
     */
    private SoftReference<BufferedImage> myBottomImageRef;
    private SoftReference<BufferedImage> myTopImageRef;

    public MyLayeredPane(final JComponent splitter) {
      myBottomImageRef = new SoftReference<BufferedImage>(null);
      myTopImageRef = new SoftReference<BufferedImage>(null);
      setOpaque(false);
      add(splitter, JLayeredPane.DEFAULT_LAYER);
    }

    public final Image getBottomImage() {
      Pair<BufferedImage, SoftReference<BufferedImage>> result = getImage(myBottomImageRef);
      myBottomImageRef = result.second;
      return result.first;
    }

    public final Image getTopImage() {
      Pair<BufferedImage, SoftReference<BufferedImage>> result = getImage(myTopImageRef);
      myTopImageRef = result.second;
      return result.first;
    }

    private Pair<BufferedImage, SoftReference<BufferedImage>> getImage(SoftReference<BufferedImage> imageRef) {
      LOG.assertTrue(UISettings.getInstance().ANIMATE_WINDOWS);
      BufferedImage image = imageRef.get();
      if (
        image == null ||
        image.getWidth(null) < getWidth() || image.getHeight(null) < getHeight()
        ) {
        final int width = Math.max(Math.max(1, getWidth()), myFrame.getWidth());
        final int height = Math.max(Math.max(1, getHeight()), myFrame.getHeight());
        if (SystemInfo.isWindows) {
          image = myFrame.getGraphicsConfiguration().createCompatibleImage(width, height);
        }
        else {
          // Under Linux we have found that images created by createCompatibleImage(),
          // createVolatileImage(), etc extremely slow for rendering. TrueColor buffered image
          // is MUCH faster.
          // On Mac we create a retina-compatible image

          image = UIUtil.createImage(width, height, BufferedImage.TYPE_INT_RGB);
        }
        imageRef = new SoftReference<BufferedImage>(image);
      }
      return Pair.create(image, imageRef);
    }

    /**
     * When component size becomes larger then bottom and top images should be enlarged.
     */
    public void doLayout() {
      final int width = getWidth();
      final int height = getHeight();
      if (width < 0 || height < 0) {
        return;
      }
      // Resize component at the DEFAULT layer. It should be only on component in that layer
      Component[] components = getComponentsInLayer(JLayeredPane.DEFAULT_LAYER.intValue());
      LOG.assertTrue(components.length <= 1);
      for (final Component component : components) {
        component.setBounds(0, 0, getWidth(), getHeight());
      }
      // Resize components at the PALETTE layer
      components = getComponentsInLayer(JLayeredPane.PALETTE_LAYER.intValue());
      for (final Component component : components) {
        if (!(component instanceof InternalDecorator)) {
          continue;
        }
        final WindowInfoImpl info = myDecorator2Info.get(component);
        // In normal situation info is not null. But sometimes Swing sends resize
        // event to removed component. See SCR #19566.
        if (info == null) {
          continue;
        }

        final float weight;
        if (info.getAnchor().isHorizontal()) {
          weight = (float)component.getHeight() / (float)getHeight();
        }
        else {
          weight = (float)component.getWidth() / (float)getWidth();
        }
        setBoundsInPaletteLayer(component, info.getAnchor(), weight);
      }
    }

    public final void setBoundsInPaletteLayer(final Component component, final ToolWindowAnchor anchor, float weight) {
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

  public void setStripesOverlayed(boolean stripesOverlayed) {
    myStripesOverlayed = stripesOverlayed;
    updateToolStripesVisibility();
  }

  @Override
  public void dispose() {
  }

  private static float normalizeWeigh(final float weight) {
    if (weight <= 0) return WindowInfoImpl.DEFAULT_WEIGHT;
    if (weight >= 1) return 1 - WindowInfoImpl.DEFAULT_WEIGHT;
    return weight;
  }
}
