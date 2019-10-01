// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ToolWindowType;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.FocusEvent;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class Windows {
  static class ToolWindowProvider {

    private final Signal mySignal;

    private Consumer<? super String> pinnedWindowFocusLostHandler;

    private ToolWindowProvider(Signal signal) {
      mySignal = signal;
    }

    public ToolWindowProvider handleDocked(Consumer<? super String> dockedWindowHandler) {
      return this;
    }

    public ToolWindowProvider handleFocusLostOnPinned(Consumer<? super String> pinnedWindowHandler) {
      this.pinnedWindowFocusLostHandler = pinnedWindowHandler;
      return this;
    }

    public ToolWindowProvider handleFloating(Consumer<? super String> floatingWindowHandler) {
      return this;
    }

    public ToolWindowProvider handleWindowed(Consumer<? super String> windowedWindowHandler) {
      return this;
    }

    public ToolWindowProvider withEscAction() {
      return this;
    }

    public static boolean isInActiveToolWindow(Object component) {
      JComponent source = (component instanceof JComponent ? ((JComponent)component) : null);

      ToolWindow activeToolWindow = ToolWindowManager.getActiveToolWindow();
      if (activeToolWindow != null) {
        JComponent activeToolWindowComponent = activeToolWindow.getComponent();
        if (activeToolWindowComponent != null) {
          while (source != null && source != activeToolWindowComponent) {
            source = ((source.getParent() != null) && (source.getParent() instanceof JComponent)) ? ((JComponent)source.getParent()) : null;
          }
        }
        return source != null;
      }

      return false;
    }

    public static boolean isInToolWindow(Component component) {
      Container c = component.getParent();
      while (c != null) {
        if (c instanceof ToolWindow) {
          return true;
        }
        c = c.getParent();
      }
      return false;
    }

    public Shortcut[] findShortcuts(String actionId) {
      return KeymapManager.getInstance().getActiveKeymap().getShortcuts(actionId);
    }

    public void bind(Project project) {

      AWTEventListener listener = new AWTEventListener() {
        @Override
        public void eventDispatched(AWTEvent event) {
          if (mySignal.isAppropriatePredicate.test(event)) {
            // Find toolwindows from the event
            // pass the toolwindow to the appropriate consumers

            // FocusEvent
            // for now we are interested in focus lost events
            String id = ToolWindowManager.getActiveId();
            if (event.getID() == FocusEvent.FOCUS_LOST) {
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
          }
        }
      };

      Toolkit.getDefaultToolkit()
        .addAWTEventListener(listener, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.FOCUS_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);

      Disposable listenerDisposer = new Disposable() {

        @Override
        public void dispose() {
          Toolkit.getDefaultToolkit().removeAWTEventListener(listener);
        }
      };

      Disposer.register(project, listenerDisposer);
    }
  }

  private static final String HEAVYWEIGHT_WINDOW_CLASS_NAME = "HeavyWeightWindow";

  private static boolean isHeavyWeightPopup(AWTEvent event) {
    Object source = event.getSource();
    if (source != null) {
      if (event.getSource().getClass().getName().contains(HEAVYWEIGHT_WINDOW_CLASS_NAME)) {
        return true;
      }
      Window ancestor = SwingUtilities.getWindowAncestor((Component)source);
      if (ancestor != null) {
        if (ancestor.getClass().getName().contains(HEAVYWEIGHT_WINDOW_CLASS_NAME)) {
          return true;
        }
      }
    }
    return false;
  }

  static class Signal {
    private final Predicate<? super AWTEvent> isAppropriatePredicate;

    Signal(Predicate<? super AWTEvent> isAppropriatePredicate) {
      this.isAppropriatePredicate = isAppropriatePredicate;
    }

    public boolean appropriate(AWTEvent event) {
      return isAppropriatePredicate.test(event);
    }
  }

  static class ToolWindowFilter {
    static ToolWindowFilter INSTANCE = new ToolWindowFilter();

    private ToolWindowFilter() {}

    static ToolWindowProvider filterBySignal(Signal signal) {
      return new ToolWindowProvider(signal);
    }
  }

  static ToolWindowFilter toolWindows() {
    // Might be we should store and create algorithms specific to toolwindows here
    return ToolWindowFilter.INSTANCE;
  }
}
