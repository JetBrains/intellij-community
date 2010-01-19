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

package com.intellij.ui.popup;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.StackingPopupDispatcher;
import com.intellij.util.containers.WeakList;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Stack;

public class StackingPopupDispatcherImpl extends StackingPopupDispatcher implements AWTEventListener, KeyEventDispatcher {

  private final Stack<JBPopup> myStack = new Stack<JBPopup>();
  private final WeakList<JBPopup> myPersistentPopups = new WeakList<JBPopup>();

  private final WeakList<JBPopup> myAllPopups = new WeakList<JBPopup>();


  private StackingPopupDispatcherImpl() {
  }

  public void onPopupShown(JBPopup popup, boolean inStack) {
    if (inStack) {
      myStack.push(popup);
      if (ApplicationManager.getApplication() != null) {
        IdeEventQueue.getInstance().getPopupManager().push(getInstance());
      }
    } else if (popup.isPersistent()) {
      myPersistentPopups.add(popup);
    }

    myAllPopups.add(popup);
  }

  public void onPopupHidden(JBPopup popup) {
    boolean wasInStack = myStack.remove(popup);
    myPersistentPopups.remove(popup);

    if (wasInStack && myStack.isEmpty()) {
      if (ApplicationManager.getApplication() != null) {
        IdeEventQueue.getInstance().getPopupManager().remove(this);
      }
    }

    myAllPopups.remove(popup);
  }

  public void hidePersistentPopups() {
    final WeakList<JBPopup> list = myPersistentPopups;
    for (JBPopup each : list) {
      if (each.isNativePopup()) {
        each.setUiVisible(false);
      }
    }
  }

  public void restorePersistentPopups() {
    final WeakList<JBPopup> list = myPersistentPopups;
    for (JBPopup each : list) {
      if (each.isNativePopup()) {
        each.setUiVisible(true);
      }
    }
  }

  public void eventDispatched(AWTEvent event) {
    dispatchMouseEvent(event);
  }

  protected boolean dispatchMouseEvent(AWTEvent event) {
    if (event.getID() != MouseEvent.MOUSE_PRESSED) {
      return false;
    }

    if (myStack.isEmpty()) {
      return false;
    }

    AbstractPopup popup = (AbstractPopup)findPopup();

    final MouseEvent mouseEvent = ((MouseEvent) event);

    Point point = (Point) mouseEvent.getPoint().clone();
    SwingUtilities.convertPointToScreen(point, mouseEvent.getComponent());

    while (true) {
      if (popup != null && !popup.isDisposed()) {
        final Component content = popup.getContent();
        if (!content.isShowing()) {
          popup.cancel();
          return false;
        }

        final Rectangle bounds = new Rectangle(content.getLocationOnScreen(), content.getSize());
        if (bounds.contains(point) || !popup.isCancelOnClickOutside()) {
          return false;
        }

        if (!popup.canClose()){
          return false;
        }
        popup.cancel(mouseEvent);
      }

      if (myStack.isEmpty()) {
        return false;
      }

      popup = (AbstractPopup)myStack.peek();
      if (popup == null || popup.isDisposed()) {
        myStack.pop();
      }
    }
  }

  protected JBPopup findPopup() {
    while(true) {
      if (myStack.isEmpty()) break;
      final AbstractPopup each = (AbstractPopup)myStack.peek();
      if (each == null || each.isDisposed()) {
        myStack.pop();
      } else {
        return each;
      }
    }

    return null;
  }

  public boolean dispatchKeyEvent(final KeyEvent e) {
    final boolean closeRequest = e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_ESCAPE && e.getModifiers() == 0;

    JBPopup popup;

    if (closeRequest) {
      popup = findPopup();
    } else {
      popup = getFocusedPopup();
    }

    if (popup == null) return false;


    if (closeRequest) {
      if (popup.isCancelKeyEnabled()) {
        popup.cancel();
        return true;
      }
    }

    return false;
  }


  @Nullable
  public Component getComponent() {
    return myStack.size() > 0 ?myStack.peek().getContent() : null;
  }

  public boolean dispatch(AWTEvent event) {
   if (event instanceof KeyEvent) {
      return dispatchKeyEvent(((KeyEvent) event));
   } else if (event instanceof MouseEvent) {
     return dispatchMouseEvent(event);
   } else {
     return false;
   }
  }

  public void requestFocus() {
    if (myStack.isEmpty()) return;

    final AbstractPopup popup = (AbstractPopup)myStack.peek();
    popup.requestFocus();

  }

  public boolean close() {
    return closeActivePopup();
  }

  public boolean closeActivePopup() {
    if (myStack.isEmpty()) return false;

    final AbstractPopup popup = (AbstractPopup)myStack.pop();
    if (popup != null && popup.isVisible()){
      popup.cancel();
      return true;
    }
    return false;
  }

  public boolean isPopupFocused() {
    return getFocusedPopup() != null;
  }

  private JBPopup getFocusedPopup() {
    for (JBPopup each : myAllPopups) {
      if (each != null && each.isFocused()) return each;
    }
    return null;
  }
}
