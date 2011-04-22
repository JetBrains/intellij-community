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
package com.intellij.openapi.keymap.impl;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.Map;

/**
 * Current implementation of the dispatcher is intended to filter mouse event addressed to
 * the editor. Also it allows to map middle mouse's button to some action.
 *
 * @author Vladimir Kondratyev
 * @author Konstantin Bulenkov
 */
public final class IdeMouseEventDispatcher {
  private final PresentationFactory myPresentationFactory = new PresentationFactory();
  private final ArrayList<AnAction> myActions = new ArrayList<AnAction>(1);
  private final Map<Container, Integer> myRootPane2BlockedId = new HashMap<Container, Integer>();
  private int myLastHorScrolledComponentHash = 0;

  public IdeMouseEventDispatcher() {
  }

  private void fillActionsList(Component component, MouseShortcut mouseShortcut, boolean isModalContext) {
    myActions.clear();

    // here we try to find "local" shortcuts
    if (component instanceof JComponent) {
      final ArrayList<AnAction> listOfActions = (ArrayList<AnAction>)((JComponent)component).getClientProperty(AnAction.ourClientProperty);
      if (listOfActions != null) {
        for (AnAction action : listOfActions) {
          final Shortcut[] shortcuts = action.getShortcutSet().getShortcuts();
          for (Shortcut shortcut : shortcuts) {
            if (mouseShortcut.equals(shortcut) && !myActions.contains(action)) {
              myActions.add(action);
            }
          }
        }
        // once we've found a proper local shortcut(s), we exit
        if (! myActions.isEmpty()) {
          return;
        }
      }
    }

    // search in main keymap
    if (KeymapManagerImpl.ourKeymapManagerInitialized) {
      final KeymapManager keymapManager = KeymapManager.getInstance();
      if (keymapManager != null) {
        final Keymap keymap = keymapManager.getActiveKeymap();
        final String[] actionIds = keymap.getActionIds(mouseShortcut);

        ActionManager actionManager = ActionManager.getInstance();
        for (String actionId : actionIds) {
          AnAction action = actionManager.getAction(actionId);

          if (action == null) continue;

          if (isModalContext && !action.isEnabledInModalContext()) continue;

          if (!myActions.contains(action)) {
            myActions.add(action);
          }
        }
      }
    }
  }

  /**
   * @return <code>true</code> if and only if the passed event is already dispatched by the
   *         <code>IdeMouseEventDispatcher</code> and there is no need for any other processing of the event.
   *         If the method returns <code>false</code> then it means that the event should be delivered
   *         to normal event dispatching.
   */
  public boolean dispatchMouseEvent(MouseEvent e) {
    boolean ignore = false;

    if (!(e.getID() == MouseEvent.MOUSE_PRESSED ||
          e.getID() == MouseEvent.MOUSE_RELEASED ||
          e.getID() == MouseEvent.MOUSE_CLICKED)) {
      ignore = true;
    }


    if (e.isConsumed()
        || e.isPopupTrigger()
        || MouseEvent.MOUSE_RELEASED != e.getID()
        || e.getClickCount() < 1 // TODO[vova,anton] is it possible. it seems that yes! but how???
        || e.getButton() == MouseEvent.NOBUTTON) { // See #16995. It did happen
      ignore = true;
    }

    final JRootPane root = findRoot(e);
    if (root != null) {
      final Integer lastId = myRootPane2BlockedId.get(root);
      if (lastId != null) {
        if (e.getID() <= lastId.intValue()) {
          myRootPane2BlockedId.remove(root);
        }
        else {
          myRootPane2BlockedId.put(root, e.getID());
          return true;
        }
      }
    }


    Component component = e.getComponent();
    if (component == null) {
      throw new IllegalStateException("component cannot be null");
    }
    component = SwingUtilities.getDeepestComponentAt(component, e.getX(), e.getY());

    if (component instanceof IdeGlassPaneImpl) {
      component = ((IdeGlassPaneImpl)component).getTargetComponentFor(e);
    }

    if (component == null) { // do nothing if component doesn't contains specified point
      return false;
    }

    if (isHorizontalScrolling(component, e)) {
      boolean done = doHorizontalScrolling(component, (MouseWheelEvent)e);
      if (done) return true;
    }

    if (ignore) return false;

    // avoid "cyclic component initialization error" in case of dialogs shown because of component initialization failure
    if (!KeymapManagerImpl.ourKeymapManagerInitialized) {
      return false;
    }

    final MouseShortcut shortcut = new MouseShortcut(e.getButton(), e.getModifiersEx(), e.getClickCount());
    fillActionsList(component, shortcut, IdeKeyEventDispatcher.isModalContext(component));
    ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    if (actionManager != null) {
      AnAction[] actions = myActions.toArray(new AnAction[myActions.size()]);
      for (AnAction action : actions) {
        DataContext dataContext = DataManager.getInstance().getDataContext(component);
        Presentation presentation = myPresentationFactory.getPresentation(action);
        AnActionEvent actionEvent = new AnActionEvent(e, dataContext, ActionPlaces.MAIN_MENU, presentation,
                                                      ActionManager.getInstance(),
                                                      e.getModifiers());
        action.beforeActionPerformedUpdate(actionEvent);

        if (presentation.isEnabled()) {
          actionManager.fireBeforeActionPerformed(action, dataContext, actionEvent);
          final Component c = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext);

          if (c != null && !c.isShowing()) continue;

          action.actionPerformed(actionEvent);
          e.consume();
        }
      }
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
      scrollBar.setValue(scrollBar.getValue() + getScrollAmount(c, me, scrollBar));
      return true;
    }
    return false;
  }

  public void resetHorScrollingTracker() {
    myLastHorScrolledComponentHash = 0;
  }

  private static int getScrollAmount(Component c, MouseWheelEvent me, JScrollBar scrollBar) {
    final int scrollBarWidth = scrollBar.getWidth();
    final int ratio = Registry.is("ide.smart.horizontal.scrolling") && scrollBarWidth > 0
                      ? Math.max((int)Math.pow(c.getWidth() / scrollBarWidth, 2), 10) : 10; // do annoying scrolling faster if smart scrolling is on
    return me.getUnitsToScroll() * scrollBar.getUnitIncrement() * ratio;
  }

  private static boolean isHorizontalScrolling(Component c, MouseEvent e) {
    if ( c != null
         && e instanceof MouseWheelEvent
         && (!SystemInfo.isMac || isDiagramViewComponent(c.getParent()))) {
      final MouseWheelEvent mwe = (MouseWheelEvent)e;
      return mwe.isShiftDown()
             && mwe.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL
             && findHorizontalScrollBar(c) != null;
    }
    return false;
  }

  @Nullable
  private static JScrollBar findHorizontalScrollBar(Component c) {
    if (c == null) return null;
    if (c instanceof JScrollPane) {
      return ((JScrollPane)c).getHorizontalScrollBar();
    }

    if (isDiagramViewComponent(c)) {
      final JComponent view = (JComponent)c;
      for (int i = 0; i < view.getComponentCount(); i++) {
         if (view.getComponent(i) instanceof JScrollBar) {
           final JScrollBar scrollBar = (JScrollBar)view.getComponent(i);
           if (scrollBar.getOrientation() == Adjustable.HORIZONTAL) {
            return scrollBar;
           }
         }
      }
    }
    return findHorizontalScrollBar(c.getParent());
  }

  private static boolean isDiagramViewComponent(Component c) {
    return c != null && "y.view.Graph2DView".equals(c.getClass().getName());
  }

  public void blockNextEvents(final MouseEvent e) {
    final JRootPane root = findRoot(e);
    if (root == null) return;

    myRootPane2BlockedId.put(root, e.getID());
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
}
