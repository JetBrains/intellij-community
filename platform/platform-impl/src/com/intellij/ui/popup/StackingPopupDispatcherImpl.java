// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ui.popup;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.StackingPopupDispatcher;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.ui.ComponentUtil;
import com.intellij.util.containers.Stack;
import com.intellij.util.containers.WeakList;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.stream.Stream;

public final class StackingPopupDispatcherImpl extends StackingPopupDispatcher implements AWTEventListener, KeyEventDispatcher {

  private final Stack<JBPopup> myStack = new Stack<>();
  private final Collection<JBPopup> myPersistentPopups = new WeakList<>();

  private final Collection<JBPopup> myAllPopups = new WeakList<>();


  private StackingPopupDispatcherImpl() {
  }

  @Override
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

  @Override
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

  @Override
  public void hidePersistentPopups() {
    for (JBPopup each : myPersistentPopups) {
      if (each.isNativePopup()) {
        each.setUiVisible(false);
      }
    }
  }

  @Override
  public void restorePersistentPopups() {
    for (JBPopup each : myPersistentPopups) {
      if (each.isNativePopup()) {
        each.setUiVisible(true);
      }
    }
  }

  @Override
  public void eventDispatched(AWTEvent event) {
    dispatchMouseEvent(event);
  }

  private boolean dispatchMouseEvent(AWTEvent event) {
    if (event.getID() != MouseEvent.MOUSE_PRESSED) {
      return false;
    }

    if (myStack.isEmpty()) {
      return false;
    }

    AbstractPopup popup = (AbstractPopup)findPopup();

    final MouseEvent mouseEvent = (MouseEvent) event;

    Point point = (Point) mouseEvent.getPoint().clone();
    SwingUtilities.convertPointToScreen(point, mouseEvent.getComponent());

    boolean needStopFurtherEventProcessing = false;
    while (true) {
      if (popup != null && !popup.isDisposed()) {
        Window window = ComponentUtil.getWindow(mouseEvent.getComponent());
        if (window != null && window != popup.getPopupWindow() && SwingUtilities.isDescendingFrom(window, popup.getPopupWindow())) {
          return false;
        }
        final Component content = popup.getContent();
        if (!content.isShowing()) {
          popup.cancel();
          return false;
        }

        if (!StartupUiUtil.isWaylandToolkit()) {
          final Rectangle bounds = new Rectangle(content.getLocationOnScreen(), content.getSize());
          if (bounds.contains(point) || !popup.isCancelOnClickOutside()) {
            return false;
          }
        } else {
          // In Wayland "location on screen" is not available, so do close unless the event came
          // directly from the popup itself.
          if (window == popup.getPopupWindow() || !popup.isCancelOnClickOutside()) {
            return false;
          }
        }

        if (!popup.canClose()){
          return false;
        }

        //click on context menu item
        if (MenuSelectionManager.defaultManager().getSelectedPath().length > 0) {
          return false;
        }

        if (needStopFurtherEventProcessing(popup, mouseEvent)) {
          needStopFurtherEventProcessing = true;
        }
        popup.cancel(mouseEvent);
      }

      if (myStack.isEmpty() || needStopFurtherEventProcessing) {
        return needStopFurtherEventProcessing;
      }

      popup = (AbstractPopup)myStack.peek();
      if (popup == null || popup.isDisposed()) {
        myStack.pop();
      }
    }
  }

  @ApiStatus.Internal
  public @Nullable JBPopup findPopup() {
    while (!myStack.isEmpty()) {
      final AbstractPopup each = (AbstractPopup)myStack.peek();
      if (each != null && !each.isDisposed()) {
        return each;
      }
      myStack.pop();
    }

    return null;
  }

  @Override
  public boolean dispatchKeyEvent(final KeyEvent e) {
    final boolean closeRequest = AbstractPopup.isCloseRequest(e);

    JBPopup popup = closeRequest ? findPopup() : getFocusedPopup();
    if (popup == null) return false;

    Window window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
    if (window instanceof Dialog && ((Dialog)window).isModal()) {
      if (!SwingUtilities.isDescendingFrom(popup.getContent(), window)) return false;
    }

    return popup.dispatchKeyEvent(e);
  }

  @Override
  public @Nullable Component getComponent() {
    return myStack.isEmpty() || myStack.peek().isDisposed() ? null : myStack.peek().getContent();
  }

  @Override
  public @NotNull Stream<JBPopup> getPopupStream() {
    return myStack.stream();
  }

  @Override
  public boolean dispatch(AWTEvent event) {
   if (event instanceof KeyEvent) {
      return dispatchKeyEvent((KeyEvent) event);
   }
    return event instanceof MouseEvent && dispatchMouseEvent(event);
  }

  @Override
  public boolean requestFocus() {
    if (myStack.isEmpty()) return false;

    final AbstractPopup popup = (AbstractPopup)myStack.peek();
    return popup.requestFocus();
  }

  @Override
  public boolean close() {
    if (!closeActivePopup()) return false;

    int size = myStack.size();
    while (closeActivePopup()) {
      int next = myStack.size();
      if (size == next) {
        // no popup was actually closed, break
        break;
      }
      size = next;
    }
    return true; // at least one popup was closed
  }

  @Override
  public void setRestoreFocusSilently() {
    if (myStack.isEmpty()) return;

    for (JBPopup each : myAllPopups) {
      if (each instanceof AbstractPopup) {
        ((AbstractPopup)each).setOk(true);
      }
    }

  }

  @Override
  public boolean closeActivePopup() {
    if (myStack.isEmpty()) return false;

    final AbstractPopup popup = (AbstractPopup)myStack.peek();
    if (popup != null && popup.isVisible() && popup.isCancelOnWindowDeactivation() && popup.canClose()) {
      popup.cancel();
      // setCancelCallback(..) can override cancel()
      return !popup.isVisible();
    }
    return false;
  }

  @Override
  public boolean isPopupFocused() {
    return getFocusedPopup() != null;
  }

  @Override
  public @Nullable JBPopup getFocusedPopup() {
    for (JBPopup each : myAllPopups) {
      if (each != null && each.isFocused()) return each;
    }
    return null;
  }

  private static boolean needStopFurtherEventProcessing(@NotNull AbstractPopup popup, @NotNull MouseEvent mouseEvent) {
    if (popup.isDisposed()) {
      return false;
    }
    int modifiers = mouseEvent.getModifiersEx() & (InputEvent.SHIFT_DOWN_MASK |
                                                   InputEvent.CTRL_DOWN_MASK |
                                                   InputEvent.ALT_DOWN_MASK |
                                                   InputEvent.META_DOWN_MASK);
    if (mouseEvent.getButton() != MouseEvent.BUTTON1 || modifiers != 0) { // on right mouse in most cases we can customize corresponding toolbar
      return false;
    }
    Component toggleButton = PopupUtil.getPopupToggleComponent(popup);
    Component c = mouseEvent.getComponent();
    if (toggleButton == null || c == null) {
      return false;
    }
    Point pointRelativeToButton = SwingUtilities.convertPoint(c, mouseEvent.getX(), mouseEvent.getY(), toggleButton);
    return toggleButton.contains(pointRelativeToButton);
  }
}
