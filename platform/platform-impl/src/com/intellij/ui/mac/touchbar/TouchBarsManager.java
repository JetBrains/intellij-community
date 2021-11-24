// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SimpleTimer;
import com.intellij.openapi.util.SimpleTimerTask;
import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;

final class TouchBarsManager {
  private static final Logger LOG = Logger.getInstance(TouchBarsManager.class);
  private static final boolean LOG_FOCUS_PROCESSING = Boolean.getBoolean("touchbar.TouchBarsManager.debug.focus.processing");
  private static final boolean LOG_INPUT_PROCESSING = Boolean.getBoolean("touchbar.TouchBarsManager.debug.input.processing");
  private static final boolean FORCE_UPDATE_ON_SHOW = Boolean.getBoolean("touchbar.TouchBarsManager.force.update.on.show"); // to debug/experiment
  private static final long CHANGE_DELAY = Long.getLong("touchbar.TouchBarsManager.change.delay", 200);

  private static final Map<Window, Stack> ourStacks = new WeakHashMap<>();
  private static final Map<Component, ComponentActions> ourComp2Actions = new WeakHashMap<>();

  private static int ourLastModifiersEx = 0;

  synchronized static void processAWTEvent(AWTEvent e) {
    if (e instanceof InputEvent) {
      processInputEvent((InputEvent)e);
      return;
    }

    final int eid = e.getID();
    if (eid == FocusEvent.FOCUS_GAINED ||
               eid == FocusEvent.FOCUS_LOST ||
               eid == WindowEvent.WINDOW_ACTIVATED ||
               eid == WindowEvent.WINDOW_DEACTIVATED ||
               eid == WindowEvent.WINDOW_LOST_FOCUS ||
               eid == WindowEvent.WINDOW_GAINED_FOCUS
    ) {
      processFocusEvent(e);
    }
  }

  private static void processInputEvent(InputEvent e) {
    final int usedKeyMask = InputEvent.ALT_DOWN_MASK |
                            InputEvent.META_DOWN_MASK |
                            InputEvent.CTRL_DOWN_MASK |
                            InputEvent.SHIFT_DOWN_MASK |
                            InputEvent.ALT_GRAPH_DOWN_MASK;
    final int oldLastModifiersEx = ourLastModifiersEx;
    ourLastModifiersEx = e.getModifiersEx() & usedKeyMask;
    final boolean areModifiersChanged = ourLastModifiersEx != oldLastModifiersEx;

    if (e instanceof MouseEvent && !areModifiersChanged) {
      // NOTE: to increase stability of switching normal/alt layouts
      // we process changes of modifiers mask even from mouse events
      return;
    }

    // check esc button
    if (Helpers.isPhisycalEsc()
        && e instanceof KeyEvent
        && e.getID() == KeyEvent.KEY_RELEASED
        && ((KeyEvent)e).getKeyCode() == KeyEvent.VK_ESCAPE
        && ourLastModifiersEx == 0
    ) {
      // find current (showing) component corresponding to event window
      final Window windowOfEvent = getWindow(e.getComponent());
      if (windowOfEvent == null) {
        if (LOG_INPUT_PROCESSING) LOG.debug("INPUT: can't find window of component %s (during isPhisycalEsc processing)", e.getComponent());
        return;
      }

      final Stack s = ourStacks.get(windowOfEvent);
      if (s != null) {
        final @Nullable ComponentActions ca = s.getTopActions();
        if (ca != null && ca.getCurrent() != null && ca.getCurrent().isCrossEsc()) {
          s.pop();
        }
      }
    }

    if (LOG_INPUT_PROCESSING) {
      final Window windowOfEvent = getWindow(e.getComponent());
      final @Nullable ComponentActions componentActions = getShownActions(windowOfEvent);
      if (componentActions == null) {
        LOG.debug("INPUT: no touchbar actions are shown for window '%s', component '%s'", windowOfEvent, e.getComponent());
      } else {
        LOG.debug("INPUT: show actions of component %s, keymask=%d", componentActions.component.get(), ourLastModifiersEx);
      }
    }

    if (areModifiersChanged) {
      // change to alt for all registered components
      for (ComponentActions ca : ourComp2Actions.values()) {
        if (ca != null)
          ca.setCurrent(ourLastModifiersEx);
      }

      // NOTE: mask-change can be received from popup window, but we must update stack for popup and also at least for main-frame.
      // So update for all windows.
      for (Stack s : ourStacks.values())
        s.updateIfNecessary();
    }
  }

  private static void processFocusEvent(AWTEvent e) {
    if (!(e.getSource() instanceof Component)) {
      return;
    }

    final Component src = (Component)e.getSource();

    // NOTE: WindowEvent.WINDOW_GAINED_FOCUS can be fired when frame focused
    if (e.getID() == FocusEvent.FOCUS_GAINED) {
      // find current (showing) component/touchbar corresponding to event window
      final Window windowOfEvent = getWindow(src);
      final @Nullable ComponentActions shownActions = getShownActions(windowOfEvent);
      final boolean isShownPersistent = shownActions != null && shownActions.isPersistent();

      for(Component p = src; p!=null; p=p.getParent()) {
        final @Nullable ComponentActions candidateActions = ourComp2Actions.get(p);
        if (candidateActions == null) {
          continue;
        }

        if (shownActions == candidateActions) {
          // actions of component p are already shown, nothing to do
          return;
        }

        if (!p.isVisible()) {
          if (LOG_FOCUS_PROCESSING) {
            LOG.debug("FOCUS GAINED: skip actions of invisible component: %s, child (source): %s", p, src);
          }
        }

        if (isShownPersistent && !candidateActions.isPersistent()) {
          // FIXME: except when focused subcomponent of current component (with persistent-touchbar)
          if (LOG_FOCUS_PROCESSING) {
            LOG.debug("FOCUS GAINED: skip not-persistent actions of parent: %s, child (source): %s", p, src);
          }
          continue;
        }

        if (LOG_FOCUS_PROCESSING) {
          LOG.debug("FOCUS GAINED: show actions of parent: %s, child (source): %s", p, src);
        }

        final TBPanel toShow = candidateActions.getTouchbar(0/*ourLastModifiersEx*/, true);
        if (toShow instanceof TBPanelActionGroup && ((TBPanelActionGroup)toShow).updateAutoCloseAndCheck()) {
          // touchbar of component p is autoclosed, skip this candidate
          continue;
        }

        showTouchbar(candidateActions);
        return;
      }

      if (LOG_FOCUS_PROCESSING) {
        LOG.debug("FOCUS GAINED: components chain hasn't any actions, initial child (source): %s", src);
      }
    }
  }

  static int getLastModifiersEx() { return ourLastModifiersEx; }

  synchronized static void register(@NotNull Component component, @NotNull ActionGroup actions, @Nullable Customizer customizations) {
    LOG.debug("register actions '%s' for component %s", actions, component);
    unregister(component); // cleanup for insurance
    ourComp2Actions.put(component, new ComponentActions(component, actions, null, customizations));
  }

  synchronized static void register(@NotNull Component component, @NotNull ActionGroup actions) {
    register(component, actions, null);
  }

  synchronized static void register(@NotNull Component component, @NotNull Map<Long, ActionGroup> actions, @Nullable Customizer customizations) {
    final ActionGroup mainLayout = actions.get(0L);
    if (mainLayout == null) {
      LOG.debug("can't find main layout for component: %s (actions will not be added)", component);
      return;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("register actions '" + mainLayout + "' for component " + component);
      for (Long key: actions.keySet()) {
        if (key != 0)
          LOG.debug("\talt action '" + actions.get(key));
      }
    }
    unregister(component); // cleanup for insurance
    ourComp2Actions.put(component, new ComponentActions(component, mainLayout, actions, customizations));
  }

  synchronized static void register(@NotNull Component component, @NotNull Map<Long, ActionGroup> actions) {
    register(component, actions, null);
  }

  synchronized static void registerAndShow(@NotNull Component component, @NotNull ActionGroup actions) {
    register(component, actions);
    showActionsOfComponent(component);
  }

  synchronized static void registerAndShow(@NotNull Component component, @NotNull Map<Long, ActionGroup> actions) {
    register(component, actions);
    showActionsOfComponent(component);
  }

  synchronized static void registerAndShow(@NotNull Component component, @NotNull Map<Long, ActionGroup> actions, @Nullable Customizer customizations) {
    register(component, actions, customizations);
    showActionsOfComponent(component);
  }

  synchronized static void registerAndShow(@NotNull Component component, @NotNull ActionGroup actions, @Nullable Customizer customizations) {
    register(component, actions, customizations);
    showActionsOfComponent(component);
  }

  synchronized static void registerAndShow(@NotNull Component component, @NotNull TBPanel tb) {
    LOG.debug("registerAndShow non-action touchbar '%s' for component %s", tb, component);
    unregister(component); // cleanup for insurance
    ourComp2Actions.put(component, new ComponentActions(component, tb));
    showActionsOfComponent(component);
  }

  synchronized static void unregister(@NotNull Component component) {
    LOG.debug("UNREGISTER: component %s", component);

    final @Nullable ComponentActions componentActions = ourComp2Actions.remove(component);
    if (componentActions == null) {
      LOG.debug("UNREGISTER: component '%s' hasn't any actions", component);
      return;
    }

    componentActions.clearCachedTouchbars();
  }

  synchronized static void showActionsOfComponent(@NotNull Component component) {
    final @Nullable ComponentActions componentActions = ourComp2Actions.get(component);
    if (componentActions == null) {
      LOG.debug("SHOW: can't find actions info for component: %s (nothing to show)", component);
      return;
    }

    showTouchbar(componentActions);
  }

  synchronized static void clearAll() {
    LOG.debug("Clear all actions (disable touchbar suppoprt)");

    ourStacks.forEach((w, s) -> {
      NST.setTouchBar(w, null);
    });
    ourStacks.clear();

    ourComp2Actions.forEach((c, ca) -> {
      if (ca == null) {
        LOG.debug("clearAll: component '%s' hasn't any actions", c);
        return;
      }
      ca.clearCachedTouchbars();
    });
    ourComp2Actions.clear();
  }

  private static @Nullable Window getWindow(@NotNull Component component) {
    if (component instanceof Window) {
      return (Window)component;
    }

    final Window window = SwingUtilities.windowForComponent(component);
    if (window == null) {
      // NOTE: it's possible for SheetMessage$JPanel
      LOG.debug("can't find window for component: %s", component);
    }
    return window;
  }

  private static void showTouchbar(@NotNull ComponentActions componentActions) {
    componentActions.setCurrent(ourLastModifiersEx);
    getWindowStack(getWindow(componentActions.component.get())).push(componentActions);
  }

  private static @NotNull Stack getWindowStack(@Nullable Window window) {
    Stack stack = ourStacks.get(window);
    if (stack == null) {
      stack = new Stack(window);
      ourStacks.put(window, stack);
      if (window != null) {
        window.addWindowListener(new WindowAdapter() {
          @Override
          public void windowClosed(WindowEvent e) {
            ourStacks.remove(window);
          }
        });
      }
    }
    return stack;
  }

  synchronized static void hideTouchbar(@NotNull TBPanel tb) {
    for (Stack stack: ourStacks.values()) {
      stack.removeTouchbar(tb);
    }
  }

  private static @Nullable ComponentActions getShownActions(@Nullable Window window) {
    if (window != null) {
      Stack stack = ourStacks.get(window);
      if (stack == null && window.getType() == Window.Type.POPUP) {
        // no touchbars stack presented for passed window
        // if window is Popup then check parent window
        stack = ourStacks.get(window.getParent());
      }
      if (stack != null) {
        return stack.getTopActions();
      }
    }
    return null;
  }

  //
  // ComponentActions
  //

  private static class ComponentActions {
    private static final Map<ActionGroup, TBPanel> ourActions2Touchbar = new WeakHashMap<>(); // Cached touchbars (per ActionGroup)

    final @NotNull WeakReference<Component> component;
    final @Nullable ActionGroup actions;
    final @Nullable Map<Long, ActionGroup> altActions;
    final @Nullable Customizer customizer;

    final @Nullable TBPanel customTouchbar; // for non-action touchbars (like popup scrubbers)

    private @Nullable TBPanel current;

    ComponentActions(@NotNull Component component,
                     @NotNull ActionGroup actions,
                     @Nullable Map<Long, ActionGroup> altActions,
                     @Nullable Customizer customizer) {
      this.component = new WeakReference<>(component);
      this.actions = actions;
      this.altActions = altActions;
      this.customizer = customizer;
      this.customTouchbar = null;
    }

    ComponentActions(@NotNull Component component,
                     @NotNull TBPanel customTouchbar) {
      this.component = new WeakReference<>(component);
      this.actions = null;
      this.altActions = null;
      this.customizer = null;
      this.customTouchbar = customTouchbar;
    }

    boolean isPersistent() { return customizer != null && customizer.getCrossEscInfo() != null && customizer.getCrossEscInfo().persistent; }

    boolean isHidden() { return customizer != null && customizer.getCrossEscInfo() != null && customizer.getCrossEscInfo().persistent; }

    void setCurrent(long altKeyMask) {
      @Nullable TBPanel alt = getTouchbar(altKeyMask, false);
      if (alt != null && alt != TBPanel.EMPTY) // don't change touchbar when alt-layout wasn't defined (keep previous)
        current = alt;
    }

    @Nullable TBPanel getCurrent() { return current; }

    @Nullable TBPanel getTouchbar(long altKeyMask, boolean cachedOnly) {
      if (customTouchbar != null) {
        return customTouchbar;
      }

      final @Nullable ActionGroup actions = getAltActions(altKeyMask);
      if (actions == null) {
        return null;
      }

      // find cached (or create)
      TBPanel tb = ourActions2Touchbar.get(actions);
      if (tb == null && !cachedOnly) {
        final Component cmp = component.get();
        tb = new TBPanelActionGroup(actions
                                    + " | "
                                    + (cmp == null ? "disposed_component" : cmp.getClass().getSimpleName()),
                                    actions, customizer);
        ourActions2Touchbar.put(actions, tb);
      }
      return tb;
    }

    private static boolean isEmptyGroup(@NotNull ActionGroup actions) {
      AnAction[] kids = actions.getChildren(null);
      if (kids == null || kids.length < 1)
        return true;
      for (AnAction kid: kids)
        if (!(kid instanceof Separator))
          return false;
      return true;
    }

    void clearCachedTouchbars() {
      if (current != null) {
        current.release();
        current = null;
      }
      if (actions != null) {
        final TBPanel tb = ourActions2Touchbar.remove(actions);
        if (tb != null) tb.release();
      }

      if (altActions != null) {
        for (ActionGroup ag: altActions.values()) {
          final TBPanel tb = ourActions2Touchbar.remove(ag);
          if (tb != null) tb.release();
        }
      }

      if (customTouchbar != null) {
        customTouchbar.release();
      }
    }

    @Nullable ActionGroup getAltActions(long mask) {
      if (mask == 0) {
        return actions;
      }
      if (altActions == null || altActions.isEmpty()) {
        return null;
      }
      return altActions.get(mask);
    }
  }

  //
  // NOTE: use stack (per-window) to simplify such events as touchbar closing (we must show previous touchbar, so must remember it in stack)
  //
  private static class Stack {
    private final @Nullable WeakReference<Window> myWindow;
    private final ArrayDeque<ComponentActions> myStack = new ArrayDeque<>();

    private TBPanel myLastShownTouchbar = null;
    private SimpleTimerTask myNativeUpdateTask = null;

    private Stack(@Nullable Window window) {
      myWindow = window != null ? new WeakReference<>(window) : null;

      if (window != null) {
        window.addWindowListener(new WindowAdapter() {
          @Override
          public void windowActivated(WindowEvent e) {
            final @Nullable ComponentActions ca = getTopActions();
            if (ca != null && ca.getCurrent() instanceof TBPanelActionGroup) {
              ((TBPanelActionGroup)ca.getCurrent()).startUpdateTimer();
            }
          }
          @Override
          public void windowDeactivated(WindowEvent e) {
            final @Nullable ComponentActions ca = getTopActions();
            if (ca != null && ca.getCurrent() instanceof TBPanelActionGroup) {
              ((TBPanelActionGroup)ca.getCurrent()).stopUpdateTimer();
            }
          }
        });
      }
    }

    synchronized void push(@NotNull ComponentActions ca) {
      if (!myStack.isEmpty() && myStack.peek() == ca) {
        return;
      }

      myStack.removeIf((other) -> ca == other); // don't store copies (just for insurance)
      myStack.push(ca);

      if (FORCE_UPDATE_ON_SHOW && ca.getCurrent() instanceof TBPanelActionGroup) { // just experimental possibility
        ((TBPanelActionGroup)ca.getCurrent()).updateActionItems();
      }

      _scheduleUpdateNative();
    }

    synchronized void pop() {
      if (myStack.isEmpty()) {
        return;
      }

      myStack.poll();
      _scheduleUpdateNative();
    }

    synchronized void removeTouchbar(@NotNull TBPanel tb) {
      if (myStack.isEmpty()) {
        return;
      }

      final @Nullable ComponentActions topCA = myStack.peek();
      final @Nullable TBPanel top = topCA != null ? topCA.getCurrent() : null;
      myStack.removeIf((ca) -> ca.getCurrent() == tb);

      if (top == tb)
        _scheduleUpdateNative();
    }

    synchronized void updateIfNecessary() {
      if (myStack.isEmpty()) {
        return;
      }
      _scheduleUpdateNative();
    }

    synchronized @Nullable ComponentActions getTopActions() {
      return myStack.isEmpty() ? null : myStack.peek();
    }

    private void _scheduleUpdateNative() {
      // cancel prev task
      if (myNativeUpdateTask != null)
        myNativeUpdateTask.cancel();

      // ensure that top of stack has current touchbar
      final @Nullable ComponentActions ca = myStack.peek();
      if (ca == null) {
        // stack is empty
        myLastShownTouchbar = null;
        NST.setTouchBar(myWindow != null ? myWindow.get() : null, null);
        return;
      }

      if (ca.getCurrent() == null) { // current touchbar wasn't set yet, do it now
        ca.setCurrent(ourLastModifiersEx);
        if (ca.getCurrent() == null)
          ca.setCurrent(0);
      }

      // schedule new task
      myNativeUpdateTask = SimpleTimer.getInstance().setUp(() -> {
        final @Nullable ComponentActions topCA = myStack.peek();
        final @Nullable TBPanel tb = topCA != null ? topCA.getCurrent() : null;
        if (tb == myLastShownTouchbar) {
          return;
        }

        if (myLastShownTouchbar instanceof TBPanelActionGroup) {
          ((TBPanelActionGroup)myLastShownTouchbar).stopUpdateTimer();
        }

        myLastShownTouchbar = tb;

        if (myLastShownTouchbar instanceof TBPanelActionGroup) {
          final TBPanelActionGroup atb = (TBPanelActionGroup)myLastShownTouchbar;
          atb.startUpdateTimer();

          // timer can "sleep" sometimes (when user doesn't send input for expamle)
          // so always do force update before showing
          ApplicationManager.getApplication().invokeLater(atb::updateActionItems);
        }

        Window window = myWindow != null ? myWindow.get() : null;
        if (myLastShownTouchbar != null)
          myLastShownTouchbar.setTo(window);
        else
          NST.setTouchBar(window, ID.NIL);
      }, CHANGE_DELAY);
    }
  }
}
