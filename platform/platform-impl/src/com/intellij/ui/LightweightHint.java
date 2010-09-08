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
package com.intellij.ui;

import com.intellij.ide.IdeTooltip;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.wm.ex.LayoutFocusTraversalPolicyExt;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.AbstractPopup;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.EventListener;
import java.util.EventObject;

public class LightweightHint extends UserDataHolderBase implements Hint {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.LightweightHint");

  private final JComponent myComponent;
  private JComponent myFocusBackComponent;
  private final EventListenerList myListenerList = new EventListenerList();
  private MyEscListener myEscListener;
  private JBPopup myPopup;
  private JComponent myParentComponent;
  private boolean myIsRealPopup = false;
  private boolean myForceLightweightPopup = false;
  private boolean mySelectingHint;

  private boolean myForceShowAsPopup = false;
  private String myTitle = null;
  private boolean myCancelOnClickOutside;
  private boolean myCancelOnOtherWindowOpen;
  private boolean myResizable;

  private IdeTooltip myCurrentIdeTooltip;

  public LightweightHint(@NotNull final JComponent component) {
    myComponent = component;
  }

  public void setForceLightweightPopup(final boolean forceLightweightPopup) {
    myForceLightweightPopup = forceLightweightPopup;
  }


  public void setForceShowAsPopup(final boolean forceShowAsPopup) {
    myForceShowAsPopup = forceShowAsPopup;
  }

  public void setTitle(final String title) {
    myTitle = title;
  }

  public boolean isSelectingHint() {
    return mySelectingHint;
  }

  public void setSelectingHint(final boolean selectingHint) {
    mySelectingHint = selectingHint;
  }

  public void setCancelOnClickOutside(final boolean b) {
    myCancelOnClickOutside = b;
  }

  public void setCancelOnOtherWindowOpen(final boolean b) {
    myCancelOnOtherWindowOpen = b;
  }

  public void setResizable(final boolean b) {
    myResizable = b;
  }

  /**
   * Shows the hint in the layered pane. Coordinates <code>x</code> and <code>y</code>
   * are in <code>parentComponent</code> coordinate system. Note that the component
   * appears on 250 layer.
   */
  public void show(@NotNull final JComponent parentComponent,
                   final int x,
                   final int y,
                   final JComponent focusBackComponent,
                   @NotNull final HintHint hintInfo) {
    myParentComponent = parentComponent;

    myFocusBackComponent = focusBackComponent;

    LOG.assertTrue(myParentComponent.isShowing());
    myEscListener = new MyEscListener();
    myComponent.registerKeyboardAction(myEscListener, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                                       JComponent.WHEN_IN_FOCUSED_WINDOW);
    final JLayeredPane layeredPane = parentComponent.getRootPane().getLayeredPane();

    myComponent.validate();

    if (!myForceShowAsPopup &&
        (myForceLightweightPopup || fitsLayeredPane(layeredPane, myComponent, new RelativePoint(parentComponent, new Point(x, y))))) {
      beforeShow();
      final Dimension preferredSize = myComponent.getPreferredSize();


      if (hintInfo.isAwtTooltip()) {
        myCurrentIdeTooltip = IdeTooltipManager.getInstance().showTipNow(new IdeTooltip(hintInfo.getOriginalComponent(), hintInfo.getOriginalPoint(), myComponent) {
          @Override
          protected boolean canAutohideOn(MouseEvent me) {
            return me.getComponent() != hintInfo.getOriginalComponent();
          }

          @Override
          protected void onHidden() {
            fireHintHidden();
          }
        });
      } else {
        final Point layeredPanePoint = SwingUtilities.convertPoint(parentComponent, x, y, layeredPane);
        myComponent.setBounds(layeredPanePoint.x, layeredPanePoint.y, preferredSize.width, preferredSize.height);
        layeredPane.add(myComponent, JLayeredPane.POPUP_LAYER);

        myComponent.validate();
        myComponent.repaint();
      }
    }
    else {
      myIsRealPopup = true;
      myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(myComponent, null)
        .setRequestFocus(false)
        .setResizable(myResizable)
        .setMovable(myTitle != null)
        .setTitle(myTitle)
        .setShowShadow(false)
        .setCancelKeyEnabled(false)
        .setCancelOnClickOutside(myCancelOnClickOutside)
        .setCancelOnOtherWindowOpen(myCancelOnOtherWindowOpen)
        .setForceHeavyweight(!myForceLightweightPopup && myForceShowAsPopup)
        .createPopup();

      beforeShow();
      myPopup.show(new RelativePoint(myParentComponent, new Point(x, y)));
    }
  }

  protected void beforeShow() {

  }

  private static boolean fitsLayeredPane(JLayeredPane pane, JComponent component, RelativePoint desiredLocation) {
    final Rectangle lpRect = new Rectangle(pane.getLocationOnScreen().x, pane.getLocationOnScreen().y, pane.getWidth(), pane.getHeight());
    Rectangle componentRect = new Rectangle(desiredLocation.getScreenPoint().x,
                                            desiredLocation.getScreenPoint().y,
                                            component.getPreferredSize().width,
                                            component.getPreferredSize().height);
    return lpRect.contains(componentRect);
  }

  private void fireHintHidden() {
    final EventListener[] listeners = myListenerList.getListeners(HintListener.class);
    for (EventListener listener : listeners) {
      ((HintListener)listener).hintHidden(new EventObject(this));
    }
  }

  /**
   * @return bounds of hint component in the layered pane.
   */
  public final Rectangle getBounds() {
    return myComponent.getBounds();
  }

  public boolean isVisible() {
    return myIsRealPopup ? myPopup != null : myComponent.isShowing();
  }

  protected final boolean isRealPopup() {
    return myIsRealPopup;
  }

  public void hide() {
    if (isVisible()) {
      if (myIsRealPopup) {
        myPopup.cancel();
        myPopup = null;
      }
      else {
        if (myCurrentIdeTooltip != null) {
          IdeTooltip tooltip = myCurrentIdeTooltip;
          myCurrentIdeTooltip = null;
          tooltip.hide();
        } else {
          final JRootPane rootPane = myComponent.getRootPane();
          if (rootPane != null) {
            final Rectangle bounds = myComponent.getBounds();
            final JLayeredPane layeredPane = rootPane.getLayeredPane();

            try {
              if (myFocusBackComponent != null) {
                LayoutFocusTraversalPolicyExt.setOverridenDefaultComponent(myFocusBackComponent);
              }
              layeredPane.remove(myComponent);
            }
            finally {
              LayoutFocusTraversalPolicyExt.setOverridenDefaultComponent(null);
            }

            layeredPane.repaint(bounds.x, bounds.y, bounds.width, bounds.height);
          }
        }
      }
    }
    if (myEscListener != null) {
      myComponent.unregisterKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
    }
    fireHintHidden();
  }

  @Override
  public void updateBounds() {
    updateBounds(-1, -1, false);
  }

  @Override
  public void updateBounds(int x, int y) {
    updateBounds(x, y, true);
  }

  private void updateBounds(int x, int y, boolean updateLocation) {
    if (myIsRealPopup) {
      if (myPopup == null) return;
      if (updateLocation) ((AbstractPopup)myPopup).setLocation(new RelativePoint(myParentComponent, new Point(x, y)));
      myPopup.setSize(myComponent.getPreferredSize());
    }
    else {
      if (updateLocation) {
        JLayeredPane layeredPane = myParentComponent.getRootPane().getLayeredPane();
        myComponent.setLocation(SwingUtilities.convertPoint(myParentComponent, x, y, layeredPane));

      }
      Dimension preferredSize = myComponent.getPreferredSize();
      myComponent.setSize(preferredSize.width, preferredSize.height);

      myComponent.validate();
      myComponent.repaint();
    }
  }

  public final JComponent getComponent() {
    return myComponent;
  }

  public final void addHintListener(final HintListener listener) {
    myListenerList.add(HintListener.class, listener);
  }

  public final void removeHintListener(final HintListener listener) {
    myListenerList.remove(HintListener.class, listener);
  }

  private final class MyEscListener implements ActionListener {
    public final void actionPerformed(final ActionEvent e) {
      hide();
    }
  }

  @Override
  public String toString() {
    return getComponent().toString();
  }
}
