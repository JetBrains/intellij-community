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
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManagerListener;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.keymap.ex.WeakKeymapManagerListener;
import com.intellij.openapi.wm.ToolWindowAnchor;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/**
 * @author Eugene Belyaev
 */
final class Stripe extends JPanel{
  private final int myAnchor;
  private final ArrayList<StripeButton> myButtons = new ArrayList<StripeButton>();
  private final MyKeymapManagerListener myKeymapManagerListener;
  private final WeakKeymapManagerListener myWeakKeymapManagerListener;
  private final MyUISettingsListener myUISettingsListener;

  private Dimension myPrefSize;
  private StripeButton myDragButton;
  private Rectangle myDropRectangle;
  private final ToolWindowManagerImpl myManager;
  private JComponent myDragButtonImage;
  private LayoutData myLastLayoutData;
  private boolean myFinishingDrop;
  static final int DROP_DISTANCE_SENSIVITY = 20;

  Stripe(final int anchor, ToolWindowManagerImpl manager){
    super(new GridBagLayout());
    //setBackground(new Color(247, 243, 239));
    myManager = manager;
    myAnchor = anchor;
    myKeymapManagerListener=new MyKeymapManagerListener();
    myWeakKeymapManagerListener=new WeakKeymapManagerListener(KeymapManagerEx.getInstanceEx(),myKeymapManagerListener);
    myUISettingsListener=new MyUISettingsListener();
  }

  /**
   * Invoked when enclosed frame is being shown.
   */
  public void addNotify(){
    super.addNotify();
    updateText();
    updateState();
    KeymapManagerEx.getInstanceEx().addKeymapManagerListener(myWeakKeymapManagerListener);
    UISettings.getInstance().addUISettingsListener(myUISettingsListener);
  }

  /**
   * Invoked when enclosed frame is being disposed.
   */
  public void removeNotify(){
    KeymapManagerEx.getInstanceEx().removeKeymapManagerListener(myWeakKeymapManagerListener);
    UISettings.getInstance().removeUISettingsListener(myUISettingsListener);
    super.removeNotify();
  }

  void addButton(final StripeButton button,final Comparator comparator){
    myPrefSize = null;
    myButtons.add(button);
    Collections.sort(myButtons,comparator);
    add(button);
    revalidate();
  }

  void removeButton(final StripeButton button) {
    myPrefSize = null;
    myButtons.remove(button);
    remove(button);
    revalidate();
  }

  public List<StripeButton> getButtons() {
    return Collections.unmodifiableList(myButtons);
  }

  public void invalidate() {
    myPrefSize = null;
    super.invalidate();
  }

  public void doLayout() {
    if (!myFinishingDrop) {
      myLastLayoutData = recomputeBounds(true, getSize());
    }
  }

  private LayoutData recomputeBounds(boolean setBounds, Dimension toFitWith) {
    return recomputeBounds(setBounds, toFitWith, false);
  }

  private LayoutData recomputeBounds(boolean setBounds, Dimension toFitWith, boolean noDrop) {
    final LayoutData data = new LayoutData();
    data.eachY = 0;
    data.size = new Dimension();
    data.gap = 1;
    data.horizontal = isHorizontal();
    data.dragInsertPosition = -1;
    if (data.horizontal) {
      data.eachX = myManager.getToolWindowsPane().getHorizontalInsetX();
    } else {
      data.eachX = 0;
    }

    data.fitSize = toFitWith != null ? toFitWith : new Dimension();

    final Rectangle stripeSensetiveRec = new Rectangle(-DROP_DISTANCE_SENSIVITY, -DROP_DISTANCE_SENSIVITY, getWidth() + DROP_DISTANCE_SENSIVITY * 2, getHeight() + DROP_DISTANCE_SENSIVITY * 2);
    boolean processDrop = isDroppingButton() && stripeSensetiveRec.intersects(myDropRectangle) && !noDrop;

    if (toFitWith == null) {
      for (StripeButton eachButton : myButtons) {
        if (!isConsideredInLayout(eachButton)) continue;
        final Dimension eachSize = eachButton.getPreferredSize();
        data.fitSize.width = Math.max(eachSize.width, data.fitSize.width);
        data.fitSize.height = Math.max(eachSize.height, data.fitSize.height);
      }
    }

    int gap = 0;
    if (toFitWith != null) {
      LayoutData layoutData = recomputeBounds(false, null, true);
      if (data.horizontal) {
        gap = toFitWith.width - myManager.getToolWindowsPane().getHorizontalInsetX() - layoutData.size.width - data.eachX ;
      }
      else {
        gap = toFitWith.height - layoutData.size.height - data.eachY;
      }

      if (processDrop) {
        if (data.horizontal) {
          gap -= myDropRectangle.width + data.gap;
        }
        else {
          gap -= myDropRectangle.height + data.gap;
        }
      }
      gap = Math.max(gap, 0);
    }

    int insertOrder = -1;
    boolean sidesStarted = false;

    for (StripeButton eachButton : getButtonsToLayOut()) {
      insertOrder = eachButton.getDecorator().getWindowInfo().getOrder();
      final Dimension eachSize = eachButton.getPreferredSize();

      if (!sidesStarted && eachButton.getWindowInfo().isSplit()) {
        if (processDrop) {
          tryDroppingOnGap(data, gap, eachButton.getWindowInfo().getOrder());
        }
        if (data.horizontal) {
          data.eachX += gap;
          data.size.width += gap;
        } else {
          data.eachY += gap;
          data.size.height += gap;
        }
        sidesStarted = true;
      }

      if (processDrop && !data.dragTargetChoosen) {
        if (data.horizontal) {
          int distance = myDropRectangle.x - data.eachX;
          if (distance < eachSize.width / 2 || (myDropRectangle.x + myDropRectangle.width) < eachSize.width / 2) {
            layoutButton(data, myDragButtonImage, false);
            data.dragInsertPosition = insertOrder;
            data.dragToSide = sidesStarted;
            data.dragTargetChoosen = true;
          }
        }
        else {
          int distance = myDropRectangle.y - data.eachY;
          if (distance < eachSize.height / 2 || (myDropRectangle.y + myDropRectangle.height) < eachSize.height / 2) {
            layoutButton(data, myDragButtonImage, false);
            data.dragInsertPosition = insertOrder;
            data.dragToSide = sidesStarted;
            data.dragTargetChoosen = true;
          }
        }
      }

      layoutButton(data, eachButton, setBounds);
    }

    if (!sidesStarted && processDrop) {
      tryDroppingOnGap(data, gap, -1);
    }


    if (isDroppingButton()) {
      final Dimension dragSize = myDragButton.getPreferredSize();
      if (getAnchor().isHorizontal() == myDragButton.getWindowInfo().getAnchor().isHorizontal()) {
        data.size.width = Math.max(data.size.width, dragSize.width);
        data.size.height = Math.max(data.size.height, dragSize.height);
      } else {
        data.size.width = Math.max(data.size.width, dragSize.height);
        data.size.height = Math.max(data.size.height, dragSize.width);
      }
    }

    if (processDrop && !data.dragTargetChoosen) {
      data.dragInsertPosition = -1;
      data.dragToSide = true;
      data.dragTargetChoosen = true;
    }

    return data;
  }

  private void tryDroppingOnGap(final LayoutData data, final int gap, final int insertOrder) {
    if (data.dragTargetChoosen) return;

    int nonSideDistance;
    int sideDistance;
    if (data.horizontal) {
      nonSideDistance = myDropRectangle.x - data.eachX;
      sideDistance = data.eachX + gap - myDropRectangle.x;
    }
    else {
      nonSideDistance = myDropRectangle.y - data.eachY;
      sideDistance = data.eachY + gap - myDropRectangle.y;
    }
    nonSideDistance = Math.max(0, nonSideDistance);

    if (sideDistance > 0) {
      if (nonSideDistance > sideDistance) {
        data.dragInsertPosition = insertOrder;
        data.dragToSide = true;
        data.dragTargetChoosen = true;
      }
      else {
        data.dragInsertPosition = -1;
        data.dragToSide = false;
        data.dragTargetChoosen = true;
      }

      layoutButton(data, myDragButtonImage, false);
    }
  }

  private List<StripeButton> getButtonsToLayOut() {
    List<StripeButton> result = new ArrayList<StripeButton>();

    List<StripeButton> tools = new ArrayList<StripeButton>();
    List<StripeButton> sideTools = new ArrayList<StripeButton>();

    for (StripeButton b : myButtons) {
      if (!isConsideredInLayout(b)) continue;

      if (b.getWindowInfo().isSplit()) {
        sideTools.add(b);
      }
      else {
        tools.add(b);
      }
    }

    result.addAll(tools);
    result.addAll(sideTools);

    return result;
  }


  public ToolWindowAnchor getAnchor() {
    return ToolWindowAnchor.get(myAnchor);
  }

  private static void layoutButton(final LayoutData data, final JComponent eachButton, boolean setBounds) {
    final Dimension eachSize = eachButton.getPreferredSize();
    if (setBounds) {
      final int width = data.horizontal ? eachSize.width : data.fitSize.width;
      final int height = data.horizontal ? data.fitSize.height : eachSize.height;
      eachButton.setBounds(data.eachX, data.eachY, width, height);
    }
    if (data.horizontal) {
      final int deltaX = eachSize.width + data.gap;
      data.eachX += deltaX;
      data.size.width += deltaX;
      data.size.height = eachSize.height;
    } else {
      final int deltaY = eachSize.height + data.gap;
      data.eachY += deltaY;
      data.size.width = eachSize.width;
      data.size.height += deltaY;
    }
    data.processedComponents++;
  }

  public void startDrag() {
    revalidate();
    repaint();
  }

  public void stopDrag() {
    revalidate();
    repaint();
  }

  public StripeButton getButtonFor(final String toolWindowId) {
    for (StripeButton each : myButtons) {
      if (each.getWindowInfo().getId().equals(toolWindowId)) return each;
    }
    return null;
  }

  public void setOverlayed(boolean overlayed) {
    Color bg = UIManager.getColor("Panel.background");
    if (overlayed) {
     setBackground(new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), 150));
    } else {
      setBackground(bg);
    }
  }

  private static class LayoutData {
    int eachX;
    int eachY;
    int gap;
    Dimension size;
    Dimension fitSize;
    boolean horizontal;
    int processedComponents;

    boolean dragTargetChoosen;
    boolean dragToSide;
    int dragInsertPosition;
  }

  private boolean isHorizontal() {
    return myAnchor == SwingConstants.TOP || myAnchor == SwingConstants.BOTTOM;
  }

  public Dimension getPreferredSize() {
    if (myPrefSize == null) {
      myPrefSize = recomputeBounds(false, null).size;
    }

    return myPrefSize;
  }


  private void updateText(){
    final int size=myButtons.size();
    for(int i=0;i<size;i++){
      myButtons.get(i).updateText();
    }
  }

  void updateState(){
    final int size=myButtons.size();
    for(int i=0;i<size;i++){
      myButtons.get(i).updateState();
    }
  }

  public boolean containsScreen(final Rectangle screenRec) {
    final Point point = screenRec.getLocation();
    SwingUtilities.convertPointFromScreen(point, this);
    return new Rectangle(point, screenRec.getSize()).intersects(
      new Rectangle(-DROP_DISTANCE_SENSIVITY,
                    -DROP_DISTANCE_SENSIVITY,
                    getWidth() + DROP_DISTANCE_SENSIVITY,
                    getHeight() + DROP_DISTANCE_SENSIVITY));
  }

  public void finishDrop() {
    if (myLastLayoutData == null) return;

    final WindowInfoImpl info = myDragButton.getDecorator().getWindowInfo();
    myFinishingDrop = true;
    myManager.setSideToolAndAnchor(info.getId(), ToolWindowAnchor.get(myAnchor), myLastLayoutData.dragInsertPosition, myLastLayoutData.dragToSide);

    myManager.invokeLater(new Runnable() {
      public void run() {
        resetDrop();
      }
    });
  }

  public void resetDrop() {
    myDragButton = null;
    myDragButtonImage = null;
    myFinishingDrop = false;
    myPrefSize = null;
    revalidate();
    repaint();
  }

  public void processDropButton(final StripeButton button, JComponent buttonImage, Point screenPoint) {
    if (!isDroppingButton()) {
      final BufferedImage image = new BufferedImage(button.getWidth(), button.getHeight(), BufferedImage.TYPE_INT_RGB);
      buttonImage.paint(image.getGraphics());
      myDragButton = button;
      myDragButtonImage = buttonImage;
      myPrefSize = null;
    }

    final Point point = new Point(screenPoint);
    SwingUtilities.convertPointFromScreen(point, this);

    myDropRectangle = new Rectangle(point, buttonImage.getSize());

    revalidate();
    repaint();
  }

  private boolean isDroppingButton() {
    return myDragButton != null;
  }

  private boolean isConsideredInLayout(final StripeButton each) {
    return each.isVisible();
  }

  private final class MyKeymapManagerListener implements KeymapManagerListener {
    public void activeKeymapChanged(final Keymap keymap){
      updateText();
    }
  }

  private final class MyUISettingsListener implements UISettingsListener{
    public void uiSettingsChanged(final UISettings source){
      updateText();
      updateState();
    }
  }


  public String toString() {
    String anchor = null;
    switch(myAnchor) {
      case SwingConstants.TOP:
        anchor = "TOP";
        break;
      case SwingConstants.BOTTOM:
        anchor = "BOTTOM";
        break;
      case SwingConstants.LEFT:
        anchor = "LEFT";
        break;
      case SwingConstants.RIGHT:
        anchor = "RIGHT";
        break;
    }
    return getClass().getName() + " " + anchor;
  }


  protected void paintComponent(final Graphics g) {
    super.paintComponent(g);
    if (!myFinishingDrop && isDroppingButton() && myDragButton.getParent() != this) {
      g.setColor(getBackground().brighter());
      g.fillRect(0, 0, getWidth(), getHeight());
    }
  }
}
