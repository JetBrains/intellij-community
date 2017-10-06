/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class Windows {

  static class ToolWindowProvider {

    private final Signal mySignal;

    private Consumer<String> dockedWindowHandler;
    private Consumer<String> pinnedWindowFocusLostHandler;
    private Consumer<String> floatingWindowHandler;
    private Consumer<String> windowedWindowHandler;
    private Consumer<String> deactivationShortcutHandler;
    private ActionManager myActionManager;

    private ToolWindowProvider(Signal signal) {
      mySignal = signal;
    }

    public ToolWindowProvider handleDocked(Consumer<String> dockedWindowHandler) {
      this.dockedWindowHandler = dockedWindowHandler;
      return this;
    }

    public ToolWindowProvider handleFocusLostOnPinned(Consumer<String> pinnedWindowHandler) {
      this.pinnedWindowFocusLostHandler = pinnedWindowHandler;
      return this;
    }

    public ToolWindowProvider handleFloating(Consumer<String> floatingWindowHandler) {
      this.floatingWindowHandler = floatingWindowHandler;
      return this;
    }

    public ToolWindowProvider handleWindowed(Consumer<String> windowedWindowHandler) {
      this.windowedWindowHandler = windowedWindowHandler;
      return this;
    }

    public ToolWindowProvider handleDeactivatingShortcut(Consumer<String> deactivationShortcutHandler) {
      this.deactivationShortcutHandler = deactivationShortcutHandler;
      return this;
    }

    public ToolWindowProvider withEscAction(ActionManager actionManager) {
      myActionManager = actionManager;
      return this;
    }

    public static boolean isInActiveToolWindow (Object component) {
      JComponent source = ((component != null) && (component instanceof JComponent)) ? ((JComponent)component) : null;

      ToolWindow activeToolWindow = ToolWindowManager.getActiveToolWindow();
      if (activeToolWindow != null) {
        JComponent activeToolWindowComponent = activeToolWindow.getComponent();
        if (activeToolWindowComponent != null) {
          while (source != null && source != activeToolWindowComponent) {
            source = ((source.getParent() != null) && (source.getParent() instanceof JComponent)) ? ((JComponent)source.getParent()) : null;
          }
        }
      }

      return source != null;
    }

    public Shortcut[] findShortcuts (String actionId) {
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

              if (isInActiveToolWindow(focusEvent.getSource()) && !isInActiveToolWindow(focusEvent.getOppositeComponent())) {
                //System.err.println("Tool window is loosing focus: " + ToolWindowManager.getActiveToolWindow().getStripeTitle());

                // A toolwindow lost focus
                if (!focusEvent.isTemporary() && ToolWindowManager.getActiveToolWindow() != null && ToolWindowManager.getActiveToolWindow().isAutoHide()) {
                  pinnedWindowFocusLostHandler.accept(id);
                }
              }
            }

            if (event.getID() == KeyEvent.KEY_PRESSED && !isHeavyWeightPopup(event) && !("Terminal").equals(id))
            {
              if (Arrays.stream(findShortcuts("EditorEscape"))
                .anyMatch(shortcut -> shortcut.equals(new KeyboardShortcut(KeyStroke.getKeyStrokeForEvent((KeyEvent)event), null))))
              {
                deactivationShortcutHandler.accept(id);
              }
            }
          }
        }
      };

      Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.FOCUS_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);

      Disposable listenerDisposer = new Disposable() {

        @Override
        public void dispose() {
          Toolkit.getDefaultToolkit().removeAWTEventListener(listener);
        }
      };

      Disposer.register(project, listenerDisposer);

    }
  }

  private static String HEAVYWEIGHT_WINDOW_CLASS_NAME = "HeavyWeightWindow";

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
    private final Predicate<AWTEvent> isAppropriatePredicate;

    public Signal(Predicate<AWTEvent> isAppropriatePredicate) {
      this.isAppropriatePredicate = isAppropriatePredicate;
    }

    public boolean appropriate (AWTEvent event) {
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

  static ToolWindowFilter toolWindows () {
    // Might be we should store and create algorithms specific to toolwindows here
    return ToolWindowFilter.INSTANCE;
  }
}
