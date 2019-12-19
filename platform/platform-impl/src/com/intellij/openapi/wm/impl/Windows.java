// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ToolWindowType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.FocusEvent;
import java.util.function.Consumer;
import java.util.function.Predicate;

@ApiStatus.Internal
public final class Windows {
  public final static class ToolWindowProvider {
    private final Signal mySignal;

    private Consumer<? super String> pinnedWindowFocusLostHandler;

    public ToolWindowProvider(@NotNull Signal signal) {
      mySignal = signal;
    }

    public ToolWindowProvider handleFocusLostOnPinned(@NotNull Consumer<? super String> value) {
      pinnedWindowFocusLostHandler = value;
      return this;
    }

    public ToolWindowProvider withEscAction() {
      return this;
    }

    public static boolean isInActiveToolWindow(@Nullable Object component) {
      ToolWindowImpl activeToolWindow = (ToolWindowImpl)ToolWindowManager.getActiveToolWindow();
      if (activeToolWindow == null) {
        return false;
      }

      JComponent source = component instanceof JComponent ? (JComponent)component : null;
      JComponent activeToolWindowComponent = activeToolWindow.getComponentIfInitialized();
      if (activeToolWindowComponent != null) {
        while (source != null && source != activeToolWindowComponent) {
          source = ((source.getParent() != null) && (source.getParent() instanceof JComponent)) ? ((JComponent)source.getParent()) : null;
        }
      }
      return source != null;
    }

    public void bind(@NotNull Disposable parentDisposable) {
      AWTEventListener listener = new AWTEventListener() {
        @Override
        public void eventDispatched(AWTEvent event) {
          if (!mySignal.isAppropriatePredicate.test(event)) {
            return;
          }

          // Find toolwindows from the event
          // pass the toolwindow to the appropriate consumers

          // FocusEvent
          // for now we are interested in focus lost events
          String id = ToolWindowManager.getActiveId();
          if (event.getID() != FocusEvent.FOCUS_LOST) {
            return;
          }
          // let's check that it is a toolwindow who loses the focus

          FocusEvent focusEvent = (FocusEvent)event;

          if (isInActiveToolWindow(focusEvent.getSource())
              && !isInActiveToolWindow(focusEvent.getOppositeComponent())
              && focusEvent.getOppositeComponent() != null) {
            //System.err.println("Tool window is loosing focus: " + ToolWindowManager.getActiveToolWindow().getStripeTitle());

            // A toolwindow lost focus
            ToolWindow activeToolWindow = ToolWindowManager.getActiveToolWindow();
            boolean focusGoesToPopup = JBPopupFactory.getInstance().getParentBalloonFor(focusEvent.getOppositeComponent()) != null;
            if (!focusEvent.isTemporary() &&
                !focusGoesToPopup &&
                activeToolWindow != null &&
                (activeToolWindow.isAutoHide() || activeToolWindow.getType() == ToolWindowType.SLIDING)) {
              pinnedWindowFocusLostHandler.accept(id);
            }
          }
        }
      };

      Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.FOCUS_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
      Disposer.register(parentDisposable, new Disposable() {
        @Override
        public void dispose() {
          Toolkit.getDefaultToolkit().removeAWTEventListener(listener);
        }
      });
    }
  }

  static final class Signal {
    private final Predicate<? super AWTEvent> isAppropriatePredicate;

    Signal(Predicate<? super AWTEvent> isAppropriatePredicate) {
      this.isAppropriatePredicate = isAppropriatePredicate;
    }

    public boolean appropriate(AWTEvent event) {
      return isAppropriatePredicate.test(event);
    }
  }
}
