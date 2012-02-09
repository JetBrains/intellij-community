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

import com.intellij.ide.actions.ActivateToolWindowAction;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.ui.MouseDragHelper;
import com.intellij.ui.PopupHandler;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * @author Eugene Belyaev
 * @author Vladimir Kondratyev
 */
public final class StripeButton extends AnchoredButton implements ActionListener {
  private final Color ourBackgroundColor = new Color(247, 243, 239);

  /**
   * This is analog of Swing mnemomic. We cannot use the standard ones
   * because it causes typing of "funny" characters into the editor.
   */
  private int myMnemonic;
  private final InternalDecorator myDecorator;
  private final MyPropertyChangeListener myToolWindowHandler;
  private boolean myPressedWhenSelected;

  private JLayeredPane myDragPane;
  private final ToolWindowsPane myPane;
  private JLabel myDragButtonImage;
  private Point myPressedPoint;
  private Stripe myLastStripe;
  private KeyEventDispatcher myDragKeyEventDispatcher;
  private boolean myDragCancelled = false;

  StripeButton(@NotNull final InternalDecorator decorator, ToolWindowsPane pane) {
    myDecorator = decorator;
    myToolWindowHandler = new MyPropertyChangeListener();
    myPane = pane;

    init();
  }

  /**
   * We are using the trick here: the method does all things that super method does
   * excepting firing of the MNEMONIC_CHANGED_PROPERTY event. After that mnemonic
   * doesn't work via standard Swing rules (processing of Alt keystrokes).
   */
  public void setMnemonic(final int mnemonic) {
    throw new UnsupportedOperationException("use setMnemonic2(int)");
  }

  private void setMnemonic2(final int mnemonic) {
    myMnemonic = mnemonic;
    revalidate();
    repaint();
  }

  @Override
  public int getMnemonic2() {
    return myMnemonic;
  }

  @Override
  public ToolWindowAnchor getAnchor() {
    return getWindowInfo().getAnchor();
  }

  WindowInfoImpl getWindowInfo() {
    return myDecorator.getWindowInfo();
  }

  private void init() {
    setFocusable(false);
    setBackground(ourBackgroundColor);
    final Border border = BorderFactory.createEmptyBorder(5, 5, 0, 5);
    setBorder(border);
    updateText();
    updateState();
    apply(myDecorator.getWindowInfo());
    myDecorator.getToolWindow().addPropertyChangeListener(myToolWindowHandler);
    addActionListener(this);
    addMouseListener(new MyPopupHandler());
    setRolloverEnabled(true);
    setOpaque(false);

    enableEvents(MouseEvent.MOUSE_EVENT_MASK);

    addMouseMotionListener(new MouseMotionAdapter() {
      public void mouseDragged(final MouseEvent e) {
        processDrag(e);
      }
    });
  }

  
  public boolean isFirst() {
    return is(true);
  }
  
  public boolean isLast() {
    return is(false);
  }
  
  public boolean isOppositeSide() {
    return getWindowInfo().isSplit();
  }
  
  private boolean is(boolean first) {
    Container parent = getParent();
    if (parent == null) return false;
    
    int max = first ? Integer.MAX_VALUE : 0;
    ToolWindowAnchor anchor = getAnchor();
    Component c = null;
    int count = parent.getComponentCount();
    for (int i = 0; i < count; i++) {
      Component component = parent.getComponent(i);
      if (!component.isVisible()) continue;
      Rectangle r = component.getBounds();
      if (anchor == ToolWindowAnchor.LEFT || anchor == ToolWindowAnchor.RIGHT) {
        if (first && (max > r.y) || (!first && max < r.y)) {
          max = r.y;
          c = component;
        }
      } else {
        if (first && (max > r.x) || (!first && max < r.x)) {
          max = r.x;
          c = component;
        }
      }
    }
    
    
    return c == this;
  }

  @NotNull
  public InternalDecorator getDecorator() {
    return myDecorator;
  }

  private void processDrag(final MouseEvent e) {
    if (myDragCancelled) return;
    if (!isDraggingNow()) {
      if (myPressedPoint == null) return;
      if (isWithinDeadZone(e)) return;

      myDragPane = findLayeredPane(e);
      if (myDragPane == null) return;
      final BufferedImage image = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
      paint(image.getGraphics());
      myDragButtonImage = new JLabel(new ImageIcon(image)) {

        public String toString() {
          return "Image for: " + StripeButton.this.toString();
        }
      };
      myDragPane.add(myDragButtonImage, JLayeredPane.POPUP_LAYER);
      myDragButtonImage.setSize(myDragButtonImage.getPreferredSize());
      setVisible(false);
      myPane.startDrag();
      myDragKeyEventDispatcher = new DragKeyEventDispatcher();
      KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(myDragKeyEventDispatcher);
    }
    if (!isDraggingNow()) return;

    Point xy = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), myDragPane);
    if (myPressedPoint != null) {
      xy.x -= myPressedPoint.x;
      xy.y -= myPressedPoint.y;
    }
    myDragButtonImage.setLocation(xy);

    SwingUtilities.convertPointToScreen(xy, myDragPane);

    final Stripe stripe = myPane.getStripeFor(new Rectangle(xy, myDragButtonImage.getSize()), (Stripe)getParent());
    if (stripe == null) {
      if (myLastStripe != null) {
        myLastStripe.resetDrop();
      }
    } else {
      if (myLastStripe != null && myLastStripe != stripe) {
        myLastStripe.resetDrop();
      }
      stripe.processDropButton(this, myDragButtonImage, xy);
    }

    myLastStripe = stripe;
  }

  private class DragKeyEventDispatcher implements KeyEventDispatcher {
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
      if (isDraggingNow() && e.getKeyCode() == KeyEvent.VK_ESCAPE && e.getID() == KeyEvent.KEY_PRESSED) {
        myDragCancelled = true;
        finishDragging();
        return true;
      }
      return false;
    }
  }

  private boolean isWithinDeadZone(final MouseEvent e) {
    return Math.abs(myPressedPoint.x - e.getPoint().x) < MouseDragHelper.DRAG_START_DEADZONE && Math.abs(myPressedPoint.y - e.getPoint().y) < MouseDragHelper
      .DRAG_START_DEADZONE;
  }

  @Nullable
  private static JLayeredPane findLayeredPane(MouseEvent e) {
    if (!(e.getComponent() instanceof JComponent)) return null;
    final JRootPane root = ((JComponent)e.getComponent()).getRootPane();
    return root.getLayeredPane();
  }

  protected void processMouseEvent(final MouseEvent e) {
    if (e.isPopupTrigger() && e.getComponent().isShowing()) {
      super.processMouseEvent(e);
      return;
    }

    if (UIUtil.isCloseClick(e)) {
      myDecorator.fireHiddenSide();
      return;
    }

    if (e.getButton() == MouseEvent.BUTTON1) {
      if (MouseEvent.MOUSE_PRESSED == e.getID()) {
        myPressedPoint = e.getPoint();
        myPressedWhenSelected = isSelected();
        myDragCancelled = false;
      }
      else if (MouseEvent.MOUSE_RELEASED == e.getID()) {
        finishDragging();
        myPressedPoint = null;
        myDragButtonImage = null;
      }
    }

    super.processMouseEvent(e);
  }

  public void actionPerformed(final ActionEvent e) {
    if (myPressedWhenSelected) {
      myDecorator.fireHidden();
    }
    else {
      myDecorator.fireActivated();
    }
    myPressedWhenSelected = false;
  }

  public void apply(final WindowInfoImpl info) {
    setSelected(info.isVisible() || info.isActive());
  }

  void dispose() {
    myDecorator.getToolWindow().removePropertyChangeListener(myToolWindowHandler);
  }

  private void showPopup(final Component component, final int x, final int y) {
    final ActionGroup group = myDecorator.createPopupGroup();
    final ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN, group);
    popupMenu.getComponent().show(component, x, y);
  }

  public void updateUI() {
    setUI(StripeButtonUI.createUI(this));
    Font font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL);
    /*
    if (font.getSize() % 2 == 1) { // that's a trick. Size of antialiased font isn't properly calculated for fonts with odd size
      font = font.deriveFont(font.getStyle(), font.getSize() - 1);
    }
    */
    setFont(font);
  }

  /**
   * Updates button's text. It composes text as combination of tool window <code>id</code>
   * and short cut registered in the key map.
   */
  void updateText() {
    final String toolWindowId = getWindowInfo().getId();
    String text = toolWindowId;
    if (UISettings.getInstance().SHOW_TOOL_WINDOW_NUMBERS) {
      final int mnemonic = ActivateToolWindowAction.getMnemonicForToolWindow(toolWindowId);
      if (mnemonic != -1) {
        text = ((char)mnemonic) + ": " + text;
        setMnemonic2(mnemonic);
      }
      else {
        setMnemonic2(0);
      }
    }
    setText(text);
  }

  void updateState() {
    final ToolWindowImpl window = myDecorator.getToolWindow();
    final boolean toShow = window.isAvailable() || window.isPlaceholderMode();
    if (UISettings.getInstance().ALWAYS_SHOW_WINDOW_BUTTONS) {
      setVisible(true);
    }
    else {
      setVisible(toShow);
    }
    setEnabled(toShow && !window.isPlaceholderMode());
  }

  private final class MyPopupHandler extends PopupHandler {
    public void invokePopup(final Component component, final int x, final int y) {
      showPopup(component, x, y);
    }
  }

  private final class MyPropertyChangeListener implements PropertyChangeListener {
    public void propertyChange(final PropertyChangeEvent e) {
      final String name = e.getPropertyName();
      if (ToolWindowEx.PROP_AVAILABLE.equals(name)) {
        updateState();
      }
      else if (ToolWindowEx.PROP_TITLE.equals(name)) {
        updateText();
      }
      else if (ToolWindowEx.PROP_ICON.equals(name)) {
        final Icon icon = (Icon)e.getNewValue();
        final Icon disabledIcon = IconLoader.getDisabledIcon(icon);
        setIcon(icon);
        setDisabledIcon(disabledIcon);
      }
    }
  }

  private boolean isDraggingNow() {
    return myDragButtonImage != null;
  }

  private void finishDragging() {
    if (!isDraggingNow()) return;
    myDragPane.remove(myDragButtonImage);
    myDragButtonImage = null;
    myPane.stopDrag();
    myDragPane.repaint();
    setVisible(true);
    if (myLastStripe != null) {
      myLastStripe.finishDrop();
      myLastStripe = null;
    }
    if (myDragKeyEventDispatcher != null) {
      KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(myDragKeyEventDispatcher);
      myDragKeyEventDispatcher = null;
    }
  }


  public String toString() {
    return StringUtil.getShortName(getClass().getName()) + " text: " + getText();
  }
}
