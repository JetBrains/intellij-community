/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.impl.commands.FinalizableCommand;
import com.intellij.reference.SoftReference;
import com.intellij.util.containers.HashMap;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * This panel contains all tool stripes and JLayeredPanle at the center area. All tool windows are
 * located inside this layered pane.
 *
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class ToolWindowsPane extends JLayeredPane {
  private static final Logger LOG=Logger.getInstance("#com.intellij.openapi.wm.impl.ToolWindowsPane");

  private final IdeFrameImpl myFrame;

  private final HashMap<String,StripeButton> myId2Button;
  private final HashMap<String,InternalDecorator> myId2Decorator;
  private final HashMap<StripeButton, WindowInfoImpl> myButton2Info;
  private final HashMap<InternalDecorator, WindowInfoImpl> myDecorator2Info;
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

  private final ArrayList<Stripe> myStipes = new ArrayList<Stripe>();

  private final MyUISettingsListenerImpl myUISettingsListener;
  private final ToolWindowManagerImpl myManager;

  private boolean myStripesOverlayed;

  ToolWindowsPane(final IdeFrameImpl frame, ToolWindowManagerImpl manager){
    myManager = manager;

    setOpaque(false);
    myFrame=frame;
    myId2Button=new HashMap<String,StripeButton>();
    myId2Decorator=new HashMap<String,InternalDecorator>();
    myButton2Info=new HashMap<StripeButton, WindowInfoImpl>();
    myDecorator2Info=new HashMap<InternalDecorator, WindowInfoImpl>();
    myUISettingsListener=new MyUISettingsListenerImpl();

    // Splitters

    myVerticalSplitter = new ThreeComponentsSplitter(true);
    myVerticalSplitter.setBackground(Color.gray);
    myHorizontalSplitter = new ThreeComponentsSplitter(false);
    myHorizontalSplitter.setBackground(Color.gray);

    myVerticalSplitter.setInnerComponent(myHorizontalSplitter);

    // Tool stripes

    myTopStripe=new Stripe(SwingConstants.TOP, manager);
    myStipes.add(myTopStripe);
    myLeftStripe=new Stripe(SwingConstants.LEFT, manager);
    myStipes.add(myLeftStripe);
    myBottomStripe=new Stripe(SwingConstants.BOTTOM, manager);
    myStipes.add(myBottomStripe);
    myRightStripe=new Stripe(SwingConstants.RIGHT, manager);
    myStipes.add(myRightStripe);

    updateToolStripesVisibility();

    // Layered pane

    myLayeredPane=new MyLayeredPane(myVerticalSplitter);

    // Compose layout

    add(myTopStripe, JLayeredPane.POPUP_LAYER);
    add(myLeftStripe,  JLayeredPane.POPUP_LAYER);
    add(myBottomStripe,  JLayeredPane.POPUP_LAYER);
    add(myRightStripe,  JLayeredPane.POPUP_LAYER);
    add(myLayeredPane,  JLayeredPane.DEFAULT_LAYER);
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
    } else {
      Dimension topSize = myTopStripe.getPreferredSize();
      Dimension bottomSize = myBottomStripe.getPreferredSize();
      Dimension leftSize = myLeftStripe.getPreferredSize();
      Dimension rightSize = myRightStripe.getPreferredSize();

      myTopStripe.setBounds(0, 0, size.width, topSize.height);
      myLeftStripe.setBounds(0, topSize.height, leftSize.width, size.height - topSize.height - bottomSize.height);
      myRightStripe.setBounds(size.width - rightSize.width, topSize.height, rightSize.width, size.height - topSize.height - bottomSize.height);
      myBottomStripe.setBounds(0, size.height - bottomSize.height, size.width, bottomSize.height);

      if (UISettings.getInstance().HIDE_TOOL_STRIPES) {
        myLayeredPane.setBounds(0, 0, size.width, size.height);
      } else {
        myLayeredPane.setBounds(leftSize.width, topSize.height, size.width - leftSize.width - rightSize.width, size.height - topSize.height - bottomSize.height);
      }
    }
  }

  @Override
  protected void paintChildren(Graphics g) {
    super.paintChildren(g);

    UISettings settings = UISettings.getInstance();
    if (myTopStripe.isVisible() && myStripesOverlayed && settings.HIDE_TOOL_STRIPES) {
      Dimension topSize = myTopStripe.getSize();
      Dimension bottomSize = myBottomStripe.getSize();
      Dimension leftSize = myLeftStripe.getSize();
      Dimension rightSize = myRightStripe.getSize();
      Dimension size = getSize();


      Rectangle rec = new Rectangle(leftSize.width, topSize.height, size.width - leftSize.width - rightSize.width,
                                    size.height - topSize.height - bottomSize.height);

      g.setColor(Color.gray);

      boolean strikeoutTop = settings.SHOW_MAIN_TOOLBAR && !settings.SHOW_NAVIGATION_BAR && topSize.height == 0;

      if (topSize.height> 0) {
        g.drawLine(rec.x, rec.y, rec.x + rec.width, rec.y);
      }

      if (leftSize.width > 0) {
        g.drawLine(rec.x, rec.y, rec.x, rec.y + rec.height);
        if (strikeoutTop) {
          g.drawLine(0, rec.y, rec.x, rec.y);
        }
      }

      if (bottomSize.height > 0) {
        g.drawLine(rec.x, rec.y + rec.height, rec.x + rec.width, rec.y + rec.height);
      }

      if (rightSize.width > 0) {
        g.drawLine(rec.x + rec.width, rec.y, rec.x + rec.width, rec.y + rec.height);
        if (strikeoutTop) {
          g.drawLine(rec.x + rec.width, rec.y, size.width, rec.y);
        }
      }
    }
  }

  /**
   * Invoked when enclosed frame is being shown.
   */
  public final void addNotify(){
    super.addNotify();
    UISettings.getInstance().addUISettingsListener(myUISettingsListener);
  }

  /**
   * Invoked when enclosed frame is being disposed.
   */
  public final void removeNotify(){
    UISettings.getInstance().removeUISettingsListener(myUISettingsListener);
    super.removeNotify();
  }

  /**
   * Creates command which adds button into the specified tool stripe.
   * Command uses copy of passed <code>info</code> object.
   * @param button button which should be added.
   * @param info window info for the corresponded tool window.
   * @param comparator which is used to sort buttons within the stripe.
   * @param finishCallBack invoked when the command is completed.
   */
  final FinalizableCommand createAddButtonCmd(final StripeButton button,final WindowInfoImpl info,final Comparator comparator,final Runnable finishCallBack){
    final WindowInfoImpl copiedInfo=info.copy();
    myId2Button.put(copiedInfo.getId(),button);
    myButton2Info.put(button,copiedInfo);
    return new AddToolStripeButtonCmd(button,copiedInfo,comparator,finishCallBack);
  }

  /**
   * Creates command which shows tool window with specified set of parameters.
   * Command uses cloned copy of passed <code>info</code> object.
   * @param dirtyMode if <code>true</code> then JRootPane will not be validated and repainted after adding
   * the decorator. Moreover in this (dirty) mode animation doesn't work.
   */
  final FinalizableCommand createAddDecoratorCmd(
    final InternalDecorator decorator,
    final WindowInfoImpl info,
    final boolean dirtyMode,
    final Runnable finishCallBack
  ){
    final WindowInfoImpl copiedInfo=info.copy();
    final String id=copiedInfo.getId();

    myDecorator2Info.put(decorator,copiedInfo);
    myId2Decorator.put(id,decorator);    
    
    if(info.isDocked()){
      WindowInfoImpl sideInfo = getDockedInfoAt(info.getAnchor(), !info.isSplit());
      if (sideInfo == null) {
        return new AddDockedComponentCmd(decorator,info,dirtyMode,finishCallBack);
      }
      else {
        return new AddAndSplitDockedComponentCmd(decorator, info, dirtyMode, finishCallBack);
      }
    } else if(info.isSliding()) {
      return new AddSlidingComponentCmd(decorator,info,dirtyMode,finishCallBack);
    }else{
      throw new IllegalArgumentException("Unknown window type: "+info.getType());
    }
  }

  /**
   * Creates command which removes tool button from tool stripe.
   * @param id <code>ID</code> of the button to be removed.
   */
  final FinalizableCommand createRemoveButtonCmd(final String id,final Runnable finishCallBack){
    final StripeButton button=getButtonById(id);
    final WindowInfoImpl info=getButtonInfoById(id);
    //
    myButton2Info.remove(button);
    myId2Button.remove(id);
    return new RemoveToolStripeButtonCmd(button,info,finishCallBack);
  }

  /**
   * Creates command which hides tool window with specified set of parameters.
   * @param dirtyMode if <code>true</code> then JRootPane will not be validated and repainted after removing
   * the decorator. Moreover in this (dirty) mode animation doesn't work.
   */
  final FinalizableCommand createRemoveDecoratorCmd(final String id, final boolean dirtyMode, final Runnable finishCallBack){
    final Component decorator=getDecoratorById(id);
    final WindowInfoImpl info=getDecoratorInfoById(id);

    myDecorator2Info.remove(decorator);
    myId2Decorator.remove(id);

    WindowInfoImpl sideInfo = getDockedInfoAt(info.getAnchor(), !info.isSplit());

    if(info.isDocked()){
      if (sideInfo == null) {
        return new RemoveDockedComponentCmd(info,dirtyMode,finishCallBack);
      }
      else {
        return new RemoveSplitAndDockedComponentCmd(info, sideInfo, dirtyMode, finishCallBack);
      }
    }else if(info.isSliding()){
      return new RemoveSlidingComponentCmd(decorator,info,dirtyMode,finishCallBack);
    }else{
      throw new IllegalArgumentException("Unknown window type");
    }
  }

  /**
   * Creates command which sets specified document component.
   * @param component component to be set.
   */
  final FinalizableCommand createSetEditorComponentCmd(final JComponent component,final Runnable finishCallBack){
    return new SetEditorComponentCmd(component,finishCallBack);
  }

  final FinalizableCommand createUpdateButtonPositionCmd(String id, final Runnable finishCallback) {
    return new UpdateButtonPositionCmd(id, finishCallback);
  }

  final JComponent getMyLayeredPane(){
    return myLayeredPane;
  }

  private StripeButton getButtonById(final String id){
    return myId2Button.get(id);
  }

  private Component getDecoratorById(final String id){
    return myId2Decorator.get(id);
  }

  /**
   * @return <code>WindowInfo</code> associated with specified tool stripe button.
   * @param id <code>ID</code> of tool stripe butoon.
   */
  private WindowInfoImpl getButtonInfoById(final String id){
    return myButton2Info.get(myId2Button.get(id));
  }

  /**
   * @return <code>WindowInfo</code> associated with specified window decorator.
   * @param id <code>ID</code> of decorator.
   */
  private WindowInfoImpl getDecoratorInfoById(final String id){
    return myDecorator2Info.get(myId2Decorator.get(id));
  }

  /**
   * Sets (docks) specified component to the specified anchor.
   */
  private void setComponent(final JComponent component, final ToolWindowAnchor anchor, final float weight) {
    if(ToolWindowAnchor.TOP==anchor){
      myVerticalSplitter.setFirstComponent(component);
      myVerticalSplitter.setFirstSize((int)(myLayeredPane.getHeight() * weight));
    }else if(ToolWindowAnchor.LEFT==anchor){
      myHorizontalSplitter.setFirstComponent(component);
      myHorizontalSplitter.setFirstSize((int)(myLayeredPane.getWidth() * weight));
    }else if(ToolWindowAnchor.BOTTOM==anchor){
      myVerticalSplitter.setLastComponent(component);
      myVerticalSplitter.setLastSize((int)(myLayeredPane.getHeight() * weight));
    }else if(ToolWindowAnchor.RIGHT==anchor){
      myHorizontalSplitter.setLastComponent(component);
      myHorizontalSplitter.setLastSize((int)(myLayeredPane.getWidth() * weight));
    }else{
      LOG.error("unknown anchor: "+anchor);
    }
  }

  private JComponent getComponentAt(ToolWindowAnchor anchor) {
    if (ToolWindowAnchor.TOP == anchor) {
      return myVerticalSplitter.getFirstComponent();
    }
    else if (ToolWindowAnchor.LEFT == anchor) {
      return myHorizontalSplitter.getFirstComponent();
    }
    else if (ToolWindowAnchor.BOTTOM == anchor){
      return myVerticalSplitter.getLastComponent();
    }
    else if (ToolWindowAnchor.RIGHT == anchor){
      return myHorizontalSplitter.getLastComponent();
    }
    else {
      LOG.error("unknown anchor: " + anchor);
      return null;
    }
  }

  private WindowInfoImpl getDockedInfoAt(ToolWindowAnchor anchor, boolean side) {
    for (WindowInfoImpl info : myDecorator2Info.values()) {
      if (info.isVisible() && info.isDocked() && info.getAnchor() == anchor && side == info.isSplit()) {
        return info;
      }
    }

    return null;
  }

  private void setDocumentComponent(final JComponent component){
    myHorizontalSplitter.setInnerComponent(component);
  }

  private void updateToolStripesVisibility(){
    final boolean showButtons = !UISettings.getInstance().HIDE_TOOL_STRIPES;
    myLeftStripe.setVisible(showButtons || myStripesOverlayed);
    myRightStripe.setVisible(showButtons || myStripesOverlayed);
    myTopStripe.setVisible(showButtons || myStripesOverlayed);
    myBottomStripe.setVisible(showButtons || myStripesOverlayed);

    boolean overlayed = !showButtons && myStripesOverlayed;

    myLeftStripe.setOverlayed(overlayed);
    myRightStripe.setOverlayed(overlayed);
    myTopStripe.setOverlayed(overlayed);
    myBottomStripe.setOverlayed(overlayed);

    revalidate();
    repaint();
  }

  Stripe getStripeFor(String id) {
    final ToolWindowAnchor anchor = myManager.getToolWindow(id).getAnchor();
    if (ToolWindowAnchor.TOP == anchor) {
      return myTopStripe;
    } else if (ToolWindowAnchor.BOTTOM == anchor) {
      return myBottomStripe;
    } else if (ToolWindowAnchor.LEFT == anchor) {
      return myLeftStripe;
    } else if (ToolWindowAnchor.RIGHT == anchor) {
      return myRightStripe;
    }

    throw new IllegalArgumentException("Anchor=" + anchor);
  }

  Stripe getStripeFor(final Rectangle screenRec, Stripe preferred) {
    if (preferred.containsScreen(screenRec)) {
      return myStipes.get(myStipes.indexOf(preferred));
    }

    for (Stripe each : myStipes) {
      if (each.containsScreen(screenRec)) {
        return myStipes.get(myStipes.indexOf(each));
      }
    }

    return null;
  }

  void startDrag() {
    for (Stripe each : myStipes) {
      each.startDrag();
    }
  }

  void stopDrag() {
    for (Stripe each : myStipes) {
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
    if (!wnd.isVisible()) return;

    Resizer resizer = null;
    Component cmp = null;

    if (wnd.getType() == ToolWindowType.DOCKED) {
      cmp = getComponentAt(wnd.getAnchor());

      if (cmp != null) {
        if (wnd.getAnchor().isHorizontal()) {
          resizer = myVerticalSplitter.getFirstComponent() == cmp ? new Resizer.Splitter.FirstComponent(myVerticalSplitter) : new Resizer.Splitter.LastComponent(myVerticalSplitter);
        } else {
          resizer = myHorizontalSplitter.getFirstComponent() == cmp ? new Resizer.Splitter.FirstComponent(myHorizontalSplitter) : new Resizer.Splitter.LastComponent(myHorizontalSplitter);
        }
      }
    } if (wnd.getType() == ToolWindowType.SLIDING) {
      cmp = wnd.getComponent();
      while (cmp != null) {
        if (cmp.getParent() == myLayeredPane) break;
        cmp = cmp.getParent();
      }

      if (cmp != null) {
        if (wnd.getAnchor() == ToolWindowAnchor.TOP) {
          resizer = new Resizer.LayeredPane.Top(cmp);
        } else if (wnd.getAnchor() == ToolWindowAnchor.BOTTOM) {
          resizer = new Resizer.LayeredPane.Bottom(cmp);
        } else if (wnd.getAnchor() == ToolWindowAnchor.LEFT) {
          resizer = new Resizer.LayeredPane.Left(cmp);
        } else if (wnd.getAnchor() == ToolWindowAnchor.RIGHT) {
          resizer = new Resizer.LayeredPane.Right(cmp);
        }
      }
    }

    if (resizer == null || cmp == null) return;

    int currentValue = wnd.getAnchor().isHorizontal() ? cmp.getHeight() : cmp.getWidth();

    int actualSize = currentValue + value;

    int minValue = wnd.getAnchor().isHorizontal() ? ((ToolWindowEx)wnd).getDecorator().getTitlePanel().getPreferredSize().height : 16 + myHorizontalSplitter.getDividerWidth();
    int maxValue = wnd.getAnchor().isHorizontal() ? myLayeredPane.getHeight() : myLayeredPane.getWidth();


    if (actualSize < minValue) {
      actualSize = minValue;
    }

    if (actualSize > maxValue) {
      actualSize = maxValue;
    }

    resizer.setSize(actualSize);
  }


  static interface Resizer {
    void setSize(int size);


    abstract static class Splitter implements Resizer {
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

    abstract static class LayeredPane implements Resizer {
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

  private final class AddDockedComponentCmd extends FinalizableCommand{
    private final JComponent myComponent;
    private final WindowInfoImpl myInfo;
    private final boolean myDirtyMode;

    public AddDockedComponentCmd(final JComponent component, final WindowInfoImpl info, final boolean dirtyMode, final Runnable finishCallBack){
      super(finishCallBack);
      myComponent=component;
      myInfo=info;
      myDirtyMode=dirtyMode;
    }

    public final void run(){
      try{
        float newWeight = myInfo.getWeight()<=.0f? WindowInfoImpl.DEFAULT_WEIGHT:myInfo.getWeight();
        if(newWeight>=1.0f){
          newWeight=1- WindowInfoImpl.DEFAULT_WEIGHT;
        }
        final ToolWindowAnchor anchor=myInfo.getAnchor();
        setComponent(myComponent, anchor, newWeight);
        if(!myDirtyMode){
          myLayeredPane.validate();
          myLayeredPane.repaint();
        }
      }finally{
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
        Splitter splitter = new Splitter(!myInfo.getAnchor().isHorizontal());
        InternalDecorator oldComponent = (InternalDecorator) getComponentAt(myInfo.getAnchor());
        if (myInfo.isSplit()) {
          splitter.setFirstComponent(oldComponent);
          splitter.setSecondComponent(myNewComponent);
          splitter.setProportion(normalizeWeigh(oldComponent.getWindowInfo().getSideWeight()));
          newWeight = normalizeWeigh(oldComponent.getWindowInfo().getWeight());
        }
        else {
          splitter.setFirstComponent(myNewComponent);
          splitter.setSecondComponent(oldComponent);
          splitter.setProportion(normalizeWeigh(myInfo.getSideWeight()));
          newWeight = normalizeWeigh(myInfo.getWeight());
        }
        setComponent(splitter, anchor, newWeight);

        if(!myDirtyMode){
          myLayeredPane.validate();
          myLayeredPane.repaint();
        }
      } finally {
        finish();
      }
    }

    private float normalizeWeigh(final float weight) {
      float newWeight= weight <= .0f ? WindowInfoImpl.DEFAULT_WEIGHT : weight;
      if (newWeight >= 1.0f) {
        newWeight= 1- WindowInfoImpl.DEFAULT_WEIGHT;
      }
      return newWeight;
    }
  }

  private final class AddSlidingComponentCmd extends FinalizableCommand{
    private final Component myComponent;
    private final WindowInfoImpl myInfo;
    private final boolean myDirtyMode;

    public AddSlidingComponentCmd(final Component component, final WindowInfoImpl info, final boolean dirtyMode, final Runnable finishCallBack){
      super(finishCallBack);
      myComponent=component;
      myInfo=info;
      myDirtyMode=dirtyMode;
    }

    public final void run(){
      try{
        // Show component.
        final UISettings uiSettings=UISettings.getInstance();
        if(!myDirtyMode && uiSettings.ANIMATE_WINDOWS && !UISettings.isRemoteDesktopConnected()){
          // Prepare top image. This image is scrolling over bottom image.
          final Image topImage=myLayeredPane.getTopImage();
          final Graphics topGraphics=topImage.getGraphics();

          Rectangle bounds;

          try{
            myLayeredPane.add(myComponent,JLayeredPane.PALETTE_LAYER);
            myLayeredPane.moveToFront(myComponent);
            myLayeredPane.setBoundsInPaletteLayer(myComponent,myInfo.getAnchor(),myInfo.getWeight());
            bounds=myComponent.getBounds();
            myComponent.paint(topGraphics);
            myLayeredPane.remove(myComponent);
          }finally{
            topGraphics.dispose();
          }
          // Prepare bottom image.
          final Image bottomImage=myLayeredPane.getBottomImage();
          final Graphics bottomGraphics=bottomImage.getGraphics();
          try{
            bottomGraphics.setClip(0,0,bounds.width,bounds.height);
            bottomGraphics.translate(-bounds.x,-bounds.y);
            myLayeredPane.paint(bottomGraphics);
          }finally{
            bottomGraphics.dispose();
          }
          // Start animation.
          final Surface surface=new Surface(topImage,bottomImage,1,myInfo.getAnchor(),uiSettings.ANIMATION_SPEED);
          myLayeredPane.add(surface,JLayeredPane.PALETTE_LAYER);
          surface.setBounds(bounds);
          myLayeredPane.validate();
          myLayeredPane.repaint();

          surface.runMovement();
          myLayeredPane.remove(surface);
          myLayeredPane.add(myComponent,JLayeredPane.PALETTE_LAYER);
        }else{ // not animated
          myLayeredPane.add(myComponent,JLayeredPane.PALETTE_LAYER);
          myLayeredPane.setBoundsInPaletteLayer(myComponent,myInfo.getAnchor(),myInfo.getWeight());
        }
        if(!myDirtyMode){
          myLayeredPane.validate();
          myLayeredPane.repaint();
        }
      }finally{
        finish();
      }
    }
  }

  private final class AddToolStripeButtonCmd extends FinalizableCommand{
    private final StripeButton myButton;
    private final WindowInfoImpl myInfo;
    private final Comparator myComparator;

    public AddToolStripeButtonCmd(final StripeButton button,final WindowInfoImpl info,final Comparator comparator,final Runnable finishCallBack){
      super(finishCallBack);
      myButton=button;
      myInfo=info;
      myComparator=comparator;
    }

    public final void run(){
      try{
        final ToolWindowAnchor anchor=myInfo.getAnchor();
        if(ToolWindowAnchor.TOP==anchor){
          myTopStripe.addButton(myButton,myComparator);
        }else if(ToolWindowAnchor.LEFT==anchor){
          myLeftStripe.addButton(myButton,myComparator);
        }else if(ToolWindowAnchor.BOTTOM==anchor){
          myBottomStripe.addButton(myButton,myComparator);
        }else if(ToolWindowAnchor.RIGHT==anchor){
          myRightStripe.addButton(myButton,myComparator);
        }else{
          LOG.error("unknown anchor: "+anchor);
        }
        validate();
        repaint();
      }finally{
        finish();
      }
    }
  }

  private final class RemoveToolStripeButtonCmd extends FinalizableCommand{
    private final StripeButton myButton;
    private final WindowInfoImpl myInfo;

    public RemoveToolStripeButtonCmd(final StripeButton button,final WindowInfoImpl info,final Runnable finishCallBack){
      super(finishCallBack);
      myButton=button;
      myInfo=info;
    }

    public final void run(){
      try{
        final ToolWindowAnchor anchor=myInfo.getAnchor();
        if(ToolWindowAnchor.TOP==anchor){
          myTopStripe.removeButton(myButton);
        }else if(ToolWindowAnchor.LEFT==anchor){
          myLeftStripe.removeButton(myButton);
        }else if(ToolWindowAnchor.BOTTOM==anchor){
          myBottomStripe.removeButton(myButton);
        }else if(ToolWindowAnchor.RIGHT==anchor){
          myRightStripe.removeButton(myButton);
        }else{
          LOG.error("unknown anchor: "+anchor);
        }
        validate();
        repaint();
      }finally{
        finish();
      }
    }
  }

  private final class RemoveDockedComponentCmd extends FinalizableCommand{
    private final WindowInfoImpl myInfo;
    private final boolean myDirtyMode;

    public RemoveDockedComponentCmd(final WindowInfoImpl info, final boolean dirtyMode, final Runnable finishCallBack){
      super(finishCallBack);
      myInfo=info;
      myDirtyMode=dirtyMode;
    }

    public final void run(){
      try{
        setComponent(null,myInfo.getAnchor(), 0);
        if(!myDirtyMode){
          myLayeredPane.validate();
          myLayeredPane.repaint();
        }
      }finally{
        finish();
      }
    }
  }

  private final class RemoveSplitAndDockedComponentCmd extends FinalizableCommand {
    private final WindowInfoImpl myInfo;
    private final WindowInfoImpl mySideInfo;
    private final boolean myDirtyMode;

    private RemoveSplitAndDockedComponentCmd(final WindowInfoImpl info, final WindowInfoImpl sideInfo, boolean dirtyMode, final Runnable finishCallBack) {
      super(finishCallBack);
      myInfo = info;
      mySideInfo = sideInfo;
      myDirtyMode = dirtyMode;
    }

    public void run() {
      try {
        Splitter splitter = (Splitter) getComponentAt(myInfo.getAnchor());

        if (myInfo.isSplit()) {
          InternalDecorator component = (InternalDecorator)splitter.getFirstComponent();
          setComponent(component, myInfo.getAnchor(), component.getWindowInfo().getWeight());
        }
        else {
          InternalDecorator component = (InternalDecorator) splitter.getSecondComponent();
          setComponent(component, myInfo.getAnchor(), component.getWindowInfo().getWeight());
        }
        if(!myDirtyMode){
          myLayeredPane.validate();
          myLayeredPane.repaint();
        }
      } finally {
        finish();
      }
    }
  }


  private final class RemoveSlidingComponentCmd extends FinalizableCommand{
    private final Component myComponent;
    private final WindowInfoImpl myInfo;
    private final boolean myDirtyMode;

    public RemoveSlidingComponentCmd(
      final Component component,
      final WindowInfoImpl info,
      final boolean dirtyMode,
      final Runnable finishCallBack
    ){
      super(finishCallBack);
      myComponent=component;
      myInfo=info;
      myDirtyMode=dirtyMode;
    }

    public final void run(){
      try{
        final UISettings uiSettings=UISettings.getInstance();
        if(!myDirtyMode && uiSettings.ANIMATE_WINDOWS && !UISettings.isRemoteDesktopConnected()){
          final Rectangle bounds=myComponent.getBounds();
          // Prepare top image. This image is scrolling over bottom image. It contains
          // picture of component is being removed.
          final Image topImage=myLayeredPane.getTopImage();
          final Graphics topGraphics=topImage.getGraphics();
          try{
            myComponent.paint(topGraphics);
          }finally{
            topGraphics.dispose();
          }
          // Prepare bottom image. This image contains picture of component that is located
          // under the component to is being removed.
          final Image bottomImage=myLayeredPane.getBottomImage();
          final Graphics bottomGraphics = bottomImage.getGraphics();
          try{
            myLayeredPane.remove(myComponent);
            bottomGraphics.clipRect(0,0,bounds.width,bounds.height);
            bottomGraphics.translate(-bounds.x,-bounds.y);
            myLayeredPane.paint(bottomGraphics);
          }finally{
            bottomGraphics.dispose();
          }
          // Remove component from the layered pane and start animation.
          final Surface surface=new Surface(topImage,bottomImage,-1,myInfo.getAnchor(),uiSettings.ANIMATION_SPEED * 2);
          myLayeredPane.add(surface,JLayeredPane.PALETTE_LAYER);
          surface.setBounds(bounds);
          myLayeredPane.validate();
          myLayeredPane.repaint();

          surface.runMovement();
          myLayeredPane.remove(surface);
        }else{ // not animated
          myLayeredPane.remove(myComponent);
        }
        if(!myDirtyMode){
          myLayeredPane.validate();
          myLayeredPane.repaint();
        }
      }finally{
        finish();
      }
    }
  }

  private final class SetEditorComponentCmd extends FinalizableCommand{
    private final JComponent myComponent;

    public SetEditorComponentCmd(final JComponent component,final Runnable finishCallBack){
      super(finishCallBack);
      myComponent=component;
    }

    public void run(){
      try{
        setDocumentComponent(myComponent);
        myLayeredPane.validate();
        myLayeredPane.repaint();
      }finally{
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
        WindowInfoImpl info = getButtonById(myId).getWindowInfo();
        ToolWindowAnchor anchor = info.getAnchor();

        if (ToolWindowAnchor.TOP == anchor){
          myTopStripe.revalidate();
        }
        else if (ToolWindowAnchor.LEFT == anchor){
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
      } finally {
        finish();
      }
    }
  }

  private final class MyUISettingsListenerImpl implements UISettingsListener{
    public final void uiSettingsChanged(final UISettings source){
      updateToolStripesVisibility();
    }
  }

  private final class MyLayeredPane extends JLayeredPane{
    /*
     * These images are used to perform animated showing and hiding of components.
     * They are the member for performance reason.
     */
    private SoftReference myBottomImageRef;
    private SoftReference myTopImageRef;

    public MyLayeredPane(final JComponent splitter) {
      myBottomImageRef=new SoftReference(null);
      myTopImageRef=new SoftReference(null);
      setOpaque(true);
      setBackground(Color.gray);
      add(splitter,JLayeredPane.DEFAULT_LAYER);
      splitter.setBounds(0,0,getWidth(),getHeight());
      enableEvents(ComponentEvent.COMPONENT_EVENT_MASK);
    }

    /**
     * TODO[vova] extract method
     * Lazily creates and returns bottom image for animation.
     */
    public final Image getBottomImage(){
      LOG.assertTrue(UISettings.getInstance().ANIMATE_WINDOWS);
      BufferedImage image=(BufferedImage)myBottomImageRef.get();
      if(
        image==null ||
        image.getWidth(null) < getWidth() || image.getHeight(null) < getHeight()
      ){
        final int width=Math.max(Math.max(1,getWidth()),myFrame.getWidth());
        final int height=Math.max(Math.max(1,getHeight()),myFrame.getHeight());
        if(SystemInfo.isWindows || SystemInfo.isMac){
          image=myFrame.getGraphicsConfiguration().createCompatibleImage(width,height);
        }else{
          // Under Linux we have found that images created by createCompatibleImage(),
          // createVolatileImage(), etc extremely slow for rendering. TrueColor buffered image
          // is MUCH faster.
          image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
        }
        myBottomImageRef=new SoftReference(image);
      }
      return image;
    }

    /**
     * TODO[vova] extract method
     * Lazily creates and returns top image for animation.
     */
    public final Image getTopImage(){
      LOG.assertTrue(UISettings.getInstance().ANIMATE_WINDOWS);
      BufferedImage image=(BufferedImage)myTopImageRef.get();
      if(
        image==null ||
        image.getWidth(null) < getWidth() || image.getHeight(null) < getHeight()
      ){
        final int width=Math.max(Math.max(1,getWidth()),myFrame.getWidth());
        final int height=Math.max(Math.max(1,getHeight()),myFrame.getHeight());
        if(SystemInfo.isWindows || SystemInfo.isMac){
          image=myFrame.getGraphicsConfiguration().createCompatibleImage(width,height);
        }else{
          // Under Linux we have found that images created by createCompatibleImage(),
          // createVolatileImage(), etc extremely slow for rendering. TrueColor buffered image
          // is MUCH faster.
          image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
        }
        myTopImageRef=new SoftReference(image);
      }
      return image;
    }

    /**
     * When component size becomes larger then bottom and top images should be enlarged.
     */
    protected final void processComponentEvent(final ComponentEvent e) {
      if(ComponentEvent.COMPONENT_RESIZED==e.getID()){
        final int width=getWidth();
        final int height=getHeight();
        if(width<0||height<0){
          return;
        }
        // Resize component at the DEFAULT layer. It should be only on component in that layer
        Component[] components=getComponentsInLayer(JLayeredPane.DEFAULT_LAYER.intValue());
        LOG.assertTrue(components.length<=1);
        for(int i=0;i<components.length;i++){
          final Component component=components[i];
          component.setBounds(0,0,getWidth(),getHeight());
        }
        // Resize components at the PALETTE layer
        components=getComponentsInLayer(JLayeredPane.PALETTE_LAYER.intValue());
        for(int i=0;i<components.length;i++){
          final Component component=components[i];
          if (!(component instanceof InternalDecorator)) {
            continue;
          }
          final WindowInfoImpl info=myDecorator2Info.get(component);
          // In normal situation info is not null. But sometimes Swing sends resize
          // event to removed component. See SCR #19566.
          if(info == null){
            continue;
          }

          final float weight;
          if(ToolWindowAnchor.TOP==info.getAnchor()||ToolWindowAnchor.BOTTOM==info.getAnchor()){
            weight=(float)component.getHeight()/(float)getHeight();
          }else{
            weight=(float)component.getWidth()/(float)getWidth();
          }
          setBoundsInPaletteLayer(component,info.getAnchor(),weight);
        }
        validate();
        repaint();
      }else{
        super.processComponentEvent(e);
      }
    }

    public final void setBoundsInPaletteLayer(final Component component,final ToolWindowAnchor anchor,float weight){
      if(weight<.0f){
        weight= WindowInfoImpl.DEFAULT_WEIGHT;
      }else if(weight>1.0f){
        weight=1.0f;
      }
      if(ToolWindowAnchor.TOP==anchor){
        component.setBounds(0,0,getWidth(),(int)(getHeight()*weight+.5f));
      }else if(ToolWindowAnchor.LEFT==anchor){
        component.setBounds(0,0,(int)(getWidth()*weight+.5f),getHeight());
      }else if(ToolWindowAnchor.BOTTOM==anchor){
        final int height=(int)(getHeight()*weight+.5f);
        component.setBounds(0,getHeight()-height,getWidth(),height);
      }else if(ToolWindowAnchor.RIGHT==anchor){
        final int width=(int)(getWidth()*weight+.5f);
        component.setBounds(getWidth()-width,0,width,getHeight());
      }else{
        LOG.error("unknown anchor "+anchor);
      }
    }
  }

//todo
  static int getHorizontalInsetX() {
    return 19;
  }

  public void setStripesOverlayed(boolean stripesOverlayed) {
    myStripesOverlayed = stripesOverlayed;
    updateToolStripesVisibility();
  }
}
