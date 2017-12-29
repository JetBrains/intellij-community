/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.keymap.impl;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.impl.ui.MouseShortcutPanel;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.impl.FocusManagerImpl;
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.intellij.Patches.JDK_BUG_ID_8147994;
import static java.awt.event.MouseEvent.*;

/**
 * Current implementation of the dispatcher is intended to filter mouse event addressed to
 * the editor. Also it allows to map middle mouse's button to some action.
 *
 * @author Vladimir Kondratyev
 * @author Konstantin Bulenkov
 */
public final class IdeMouseEventDispatcher {
  private final PresentationFactory myPresentationFactory = new PresentationFactory();
  private final ArrayList<AnAction> myActions = new ArrayList<>(1);
  private final Map<Container, BlockState> myRootPane2BlockedId = new HashMap<>();
  private int myLastHorScrolledComponentHash;
  private boolean myPressedModifiersStored;
  @JdkConstants.InputEventMask
  private int myModifiers;
  @JdkConstants.InputEventMask
  private int myModifiersEx;

  private static boolean myForceTouchIsAllowed = true;

  public static void forbidForceTouch () {
    myForceTouchIsAllowed = false;
  }

  public static boolean isForceTouchAllowed () {
    return myForceTouchIsAllowed;
  }

  // Don't compare MouseEvent ids. Swing has wrong sequence of events: first is mouse_clicked(500)
  // then mouse_pressed(501), mouse_released(502) etc. Here, mouse events sorted so we can compare
  // theirs ids to properly use method blockNextEvents(MouseEvent)
  private static final List<Integer> SWING_EVENTS_PRIORITY = Arrays.asList(MOUSE_PRESSED,
                                                                           MOUSE_ENTERED,
                                                                           MOUSE_EXITED,
                                                                           MOUSE_MOVED,
                                                                           MOUSE_DRAGGED,
                                                                           MOUSE_WHEEL,
                                                                           MOUSE_RELEASED,
                                                                           MOUSE_CLICKED);

  public IdeMouseEventDispatcher() {
  }

  private void fillActionsList(Component component, MouseShortcut mouseShortcut, boolean isModalContext) {
    myActions.clear();

    // here we try to find "local" shortcuts
    for (; component != null; component = component.getParent()) {
      if (component instanceof JComponent) {
        for (AnAction action : ActionUtil.getActions((JComponent)component)) {
          for (Shortcut shortcut : action.getShortcutSet().getShortcuts()) {
            if (mouseShortcut.equals(shortcut) && !myActions.contains(action)) {
              myActions.add(action);
            }
          }
        }
        // once we've found a proper local shortcut(s), we exit
        if (!myActions.isEmpty()) {
          return;
        }
      }
    }

    // search in main keymap
    KeymapManager keymapManager = KeymapManagerImpl.ourKeymapManagerInitialized ? KeymapManager.getInstance() : null;
    if (keymapManager != null) {
      Keymap keymap = keymapManager.getActiveKeymap();
      ActionManager actionManager = ActionManager.getInstance();
      for (String actionId : keymap.getActionIds(mouseShortcut)) {
        AnAction action = actionManager.getAction(actionId);
        if (action == null || isModalContext && !action.isEnabledInModalContext()) {
          continue;
        }

        if (!myActions.contains(action)) {
          myActions.add(action);
        }
      }
    }
  }

  /**
   * @return {@code true} if and only if the passed event is already dispatched by the
   *         {@code IdeMouseEventDispatcher} and there is no need for any other processing of the event.
   *         If the method returns {@code false} then it means that the event should be delivered
   *         to normal event dispatching.
   */
  public boolean dispatchMouseEvent(MouseEvent e) {
    Component c = e.getComponent();

    //frame activation by mouse click
    if (e.getID() == MOUSE_PRESSED && c instanceof IdeFrame && !c.hasFocus()) {
      IdeFocusManager focusManager = IdeFocusManager.getGlobalInstance();
      if (focusManager instanceof FocusManagerImpl) {
        Component at = SwingUtilities.getDeepestComponentAt(c, e.getX(), e.getY());
        if (at != null && at.isFocusable()) {
          ((FocusManagerImpl)focusManager).setLastFocusedAtDeactivation((IdeFrame)c, at);
        }
      }
    }

    if (e.isPopupTrigger()) {
      if (BUTTON3 == e.getButton()) {
        if (Registry.is("ide.mouse.popup.trigger.modifiers.disabled") && (~BUTTON3_DOWN_MASK & e.getModifiersEx()) != 0) {
          // it allows to use our mouse shortcuts for Ctrl+Button3, for example
          resetPopupTrigger(e);
        }
      }
      else if (SystemInfo.isXWindow) {
        // we can do better than silly triggering popup on everything but left click
        resetPopupTrigger(e);
      }
    }

    boolean ignore = false;
    if (!(e.getID() == MOUSE_PRESSED ||
          e.getID() == MOUSE_RELEASED ||
          e.getID() == MOUSE_WHEEL && 0 < e.getModifiersEx() ||
          e.getID() == MOUSE_CLICKED)) {
      ignore = true;
    }

    patchClickCount(e);

    int clickCount = e.getClickCount();
    int button = MouseShortcut.getButton(e);
    if (button == MouseShortcut.BUTTON_WHEEL_UP || button == MouseShortcut.BUTTON_WHEEL_DOWN) {
      clickCount = 1;
    }

    if (e.isConsumed()
        || e.isPopupTrigger()
        || (button > 3 ? e.getID() != MOUSE_PRESSED && e.getID() != MOUSE_WHEEL : e.getID() != MOUSE_RELEASED)
        || clickCount < 1
        || button == NOBUTTON) { // See #16995. It did happen
      ignore = true;
    }

    @JdkConstants.InputEventMask
    int modifiers = e.getModifiers();
    @JdkConstants.InputEventMask
    int modifiersEx = e.getModifiersEx();
    if (e.getID() == MOUSE_PRESSED) {
      myPressedModifiersStored = true;
      myModifiers = modifiers;
      myModifiersEx = modifiersEx;
    }
    else if (e.getID() == MOUSE_RELEASED) {
      myForceTouchIsAllowed = true;
      if (myPressedModifiersStored) {
        myPressedModifiersStored = false;
        modifiers = myModifiers;
        modifiersEx = myModifiersEx;
      }
    }

    final JRootPane root = findRoot(e);
    if (root != null) {
      BlockState blockState = myRootPane2BlockedId.get(root);
      if (blockState != null) {
        if (SWING_EVENTS_PRIORITY.indexOf(blockState.currentEventId) < SWING_EVENTS_PRIORITY.indexOf(e.getID())) {
          blockState.currentEventId = e.getID();
          if (blockState.blockMode == IdeEventQueue.BlockMode.COMPLETE) {
            return true;
          }
          else {
            ignore = true;
          }
        } else {
          myRootPane2BlockedId.remove(root);
        }
      }
    }

    if (c == null) {
      throw new IllegalStateException("component cannot be null");
    }
    c = SwingUtilities.getDeepestComponentAt(c, e.getX(), e.getY());

    if (c instanceof IdeGlassPaneImpl) {
      c = ((IdeGlassPaneImpl)c).getTargetComponentFor(e);
    }

    if (c == null) { // do nothing if component doesn't contains specified point
      return false;
    }

    if (c instanceof MouseShortcutPanel || c.getParent() instanceof MouseShortcutPanel) {
      return false; // forward mouse processing to the special shortcut panel
    }

    if (doVerticalDiagramScrolling(c, e)) {
      return true;
    }

    if (isHorizontalScrolling(c, e)) {
      boolean done = doHorizontalScrolling(c, (MouseWheelEvent)e);
      if (done) return true;
    }

    if (ignore) return false;

    // avoid "cyclic component initialization error" in case of dialogs shown because of component initialization failure
    if (!KeymapManagerImpl.ourKeymapManagerInitialized) {
      return false;
    }

    final MouseShortcut shortcut = new MouseShortcut(button, modifiersEx, clickCount);
    fillActionsList(c, shortcut, IdeKeyEventDispatcher.isModalContext(c));
    ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    if (actionManager != null) {
      AnAction[] actions = myActions.toArray(new AnAction[myActions.size()]);
      for (AnAction action : actions) {
        DataContext dataContext = DataManager.getInstance().getDataContext(c);
        Presentation presentation = myPresentationFactory.getPresentation(action);
        AnActionEvent actionEvent = new AnActionEvent(e, dataContext, ActionPlaces.MAIN_MENU, presentation,
                                                      ActionManager.getInstance(),
                                                      modifiers);
        if (ActionUtil.lastUpdateAndCheckDumb(action, actionEvent, false)) {
          actionManager.fireBeforeActionPerformed(action, dataContext, actionEvent);
          final Component context = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext);

          if (context != null && !context.isShowing()) continue;

          ActionUtil.performActionDumbAware(action, actionEvent);
          actionManager.fireAfterActionPerformed(action, dataContext, actionEvent);
          e.consume();
        }
      }
    }
    return e.getButton() > 3;
  }

  private static void resetPopupTrigger(final MouseEvent e) {
    ReflectionUtil.setField(MouseEvent.class, e, boolean.class, "popupTrigger", false);
  }

  /**
   * This method patches event if it concerns side buttons like 4 (Backward) or 5 (Forward)
   * AND it's not single-click event. We won't support double-click for side buttons.
   * Also some JDK bugs produce zero-click events for side buttons.

   * @return true if event was patched
   */
  public static boolean patchClickCount(final MouseEvent e) {
    if (e.getClickCount() != 1 && e.getButton() > 3) {
      ReflectionUtil.setField(MouseEvent.class, e, int.class, "clickCount", 1);
      return true;
    }
    return false;
  }

  private boolean doHorizontalScrolling(Component c, MouseWheelEvent me) {
    final JScrollBar scrollBar = findHorizontalScrollBar(c);
    if (scrollBar != null) {
      if (scrollBar.hashCode() != myLastHorScrolledComponentHash) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed("ui.horizontal.scrolling");
        myLastHorScrolledComponentHash = scrollBar.hashCode();
      }
      scrollBar.setValue(scrollBar.getValue() + getScrollAmount(me, scrollBar));
      return true;
    }
    return false;
  }

  private static boolean doVerticalDiagramScrolling(@Nullable Component component, @NotNull MouseEvent event) {
    if (component != null && event instanceof MouseWheelEvent && isDiagramViewComponent(component.getParent())) {
      MouseWheelEvent mwe = (MouseWheelEvent)event;
      if (!mwe.isShiftDown() && mwe.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL && JBScrollPane.isScrollEvent(mwe)) {
        JScrollBar scrollBar = findVerticalScrollBar(component);
        if (scrollBar != null) {
          scrollBar.setValue(scrollBar.getValue() + getScrollAmount(mwe, scrollBar));
          return true;
        }
      }
    }
    return false;
  }

  @Nullable
  private static JScrollBar findVerticalScrollBar(@Nullable Component component) {
    if (component == null) {
      return null;
    }
    if (component instanceof JScrollPane) {
      JScrollBar scrollBar = ((JScrollPane)component).getVerticalScrollBar();
      return scrollBar != null && scrollBar.isVisible() ? scrollBar : null;
    }
    if (isDiagramViewComponent(component)) {
      JComponent view = (JComponent)component;
      for (int i = 0; i < view.getComponentCount(); i++) {
        if (view.getComponent(i) instanceof JScrollBar) {
          JScrollBar scrollBar = (JScrollBar)view.getComponent(i);
          if (scrollBar.getOrientation() == Adjustable.VERTICAL) {
            return scrollBar.isVisible() ? scrollBar : null;
          }
        }
      }
    }
    return findVerticalScrollBar(component.getParent());
  }

  public void resetHorScrollingTracker() {
    myLastHorScrolledComponentHash = 0;
  }

  private static int getScrollAmount(MouseWheelEvent me, JScrollBar scrollBar) {
    return me.getUnitsToScroll() * scrollBar.getUnitIncrement();
  }

  private static boolean isHorizontalScrolling(Component c, MouseEvent e) {
    if ( c != null
         && e instanceof MouseWheelEvent
         && (JDK_BUG_ID_8147994 || isDiagramViewComponent(c.getParent()))) {
      final MouseWheelEvent mwe = (MouseWheelEvent)e;
      return mwe.isShiftDown()
             && mwe.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL
             && JBScrollPane.isScrollEvent(mwe)
             && findHorizontalScrollBar(c) != null;
    }
    return false;
  }

  @Nullable
  private static JScrollBar findHorizontalScrollBar(Component c) {
    if (c == null) return null;
    if (c instanceof JScrollPane) {
      JScrollBar scrollBar = ((JScrollPane)c).getHorizontalScrollBar();
      return scrollBar != null && scrollBar.isVisible() ? scrollBar : null;
    }

    if (isDiagramViewComponent(c)) {
      final JComponent view = (JComponent)c;
      for (int i = 0; i < view.getComponentCount(); i++) {
         if (view.getComponent(i) instanceof JScrollBar) {
           final JScrollBar scrollBar = (JScrollBar)view.getComponent(i);
           if (scrollBar.getOrientation() == Adjustable.HORIZONTAL) {
             return scrollBar.isVisible() ? scrollBar : null;
           }
         }
      }
    }
    return findHorizontalScrollBar(c.getParent());
  }

  private static boolean isDiagramViewComponent(@Nullable Component component) {
    // in production yfiles classes is obfuscated
    return UIUtil.isClientPropertyTrue(component, "y.view.Graph2DView");
  }

  public void blockNextEvents(@NotNull MouseEvent e, @NotNull IdeEventQueue.BlockMode blockMode) {
    final JRootPane root = findRoot(e);
    if (root == null) return;

    myRootPane2BlockedId.put(root, new BlockState(e.getID(), blockMode));
  }

  @Nullable
  private static JRootPane findRoot(MouseEvent e) {
    final Component parent = UIUtil.findUltimateParent(e.getComponent());
    JRootPane root = null;

    if (parent instanceof JWindow) {
      root = ((JWindow)parent).getRootPane();
    }
    else if (parent instanceof JDialog) {
      root = ((JDialog)parent).getRootPane();
    }
    else if (parent instanceof JFrame) {
      root = ((JFrame)parent).getRootPane();
    }

    return root;
  }

  private static class BlockState {
    private int currentEventId;
    private final IdeEventQueue.BlockMode blockMode;

    private BlockState(int id, IdeEventQueue.BlockMode mode) {
      currentEventId = id;
      blockMode = mode;
    }
  }
}
