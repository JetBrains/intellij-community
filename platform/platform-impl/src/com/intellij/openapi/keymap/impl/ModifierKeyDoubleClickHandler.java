// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.internal.statistic.customUsageCollectors.ui.ShortcutsCollector;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntIntProcedure;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts;

/**
 * Support for keyboard shortcuts like Control-double-click or Control-double-click+A
 *
 * Timings that are used in the implementation to detect double click were tuned for SearchEverywhere
 * functionality (invoked on double Shift), so if you need to change them, please make sure
 * SearchEverywhere behaviour remains intact.
 *
 * @author Dmitry Batrak
 * @author Konstantin Bulenkov
 */
public class ModifierKeyDoubleClickHandler implements Disposable, ApplicationComponent {
  private static final Logger LOG = Logger.getInstance(ModifierKeyDoubleClickHandler.class);
  private static final TIntIntHashMap KEY_CODE_TO_MODIFIER_MAP = new TIntIntHashMap();
  static {
    KEY_CODE_TO_MODIFIER_MAP.put(KeyEvent.VK_ALT, InputEvent.ALT_MASK);
    KEY_CODE_TO_MODIFIER_MAP.put(KeyEvent.VK_CONTROL, InputEvent.CTRL_MASK);
    KEY_CODE_TO_MODIFIER_MAP.put(KeyEvent.VK_META, InputEvent.META_MASK);
    KEY_CODE_TO_MODIFIER_MAP.put(KeyEvent.VK_SHIFT, InputEvent.SHIFT_MASK);
  }

  private final ActionManagerEx myActionManagerEx;
  private final ConcurrentMap<String, MyDispatcher> myDispatchers = ContainerUtil.newConcurrentMap();
  private boolean myIsRunningAction;
  
  private ModifierKeyDoubleClickHandler(ActionManagerEx actionManagerEx) {
    myActionManagerEx = actionManagerEx;
  }

  @Override
  public void initComponent() {
    int modifierKeyCode = getMultiCaretActionModifier();
    registerAction(IdeActions.ACTION_EDITOR_CLONE_CARET_ABOVE, modifierKeyCode, KeyEvent.VK_UP);
    registerAction(IdeActions.ACTION_EDITOR_CLONE_CARET_BELOW, modifierKeyCode, KeyEvent.VK_DOWN);
    registerAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION, modifierKeyCode, KeyEvent.VK_LEFT);
    registerAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION, modifierKeyCode, KeyEvent.VK_RIGHT);
    registerAction(IdeActions.ACTION_EDITOR_MOVE_LINE_START_WITH_SELECTION, modifierKeyCode, KeyEvent.VK_HOME);
    registerAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END_WITH_SELECTION, modifierKeyCode, KeyEvent.VK_END);
  }

  @Override
  public void dispose() {
    for (MyDispatcher dispatcher : myDispatchers.values()) {
      Disposer.dispose(dispatcher);
    }
    myDispatchers.clear();
  }

  public static ModifierKeyDoubleClickHandler getInstance() {
    return ApplicationManager.getApplication().getComponent(ModifierKeyDoubleClickHandler.class);
  }

  public static int getMultiCaretActionModifier() {
    return SystemInfo.isMac ? KeyEvent.VK_ALT : KeyEvent.VK_CONTROL;
  }

  /**
   * @param actionId Id of action to be triggered on modifier+modifier[+actionKey]
   * @param modifierKeyCode keyCode for modifier, e.g. KeyEvent.VK_SHIFT
   * @param actionKeyCode keyCode for actionKey, or -1 if action should be triggered on bare modifier double click
   * @param skipIfActionHasShortcut do not invoke action if a shortcut is already bound to it in keymap
   */
  public void registerAction(@NotNull String actionId,
                             int modifierKeyCode,
                             int actionKeyCode,
                             boolean skipIfActionHasShortcut) {
    final MyDispatcher dispatcher = new MyDispatcher(actionId, modifierKeyCode, actionKeyCode, skipIfActionHasShortcut);
    MyDispatcher oldDispatcher = myDispatchers.put(actionId, dispatcher);
    IdeEventQueue.getInstance().addDispatcher(dispatcher, dispatcher);
    myActionManagerEx.addAnActionListener(dispatcher, dispatcher);
    if (oldDispatcher != null) {
      Disposer.dispose(oldDispatcher);
    }
  }

/**
 * @param actionId Id of action to be triggered on modifier+modifier[+actionKey]
 * @param modifierKeyCode keyCode for modifier, e.g. KeyEvent.VK_SHIFT
 * @param actionKeyCode keyCode for actionKey, or -1 if action should be triggered on bare modifier double click
 */
  public void registerAction(@NotNull String actionId,
                             int modifierKeyCode,
                             int actionKeyCode) {
    registerAction(actionId, modifierKeyCode, actionKeyCode, true);
  }

  public void unregisterAction(@NotNull String actionId) {
    MyDispatcher oldDispatcher = myDispatchers.remove(actionId);
    if (oldDispatcher != null) {
      Disposer.dispose(oldDispatcher);
    }
  }

  public boolean isRunningAction() {
    return myIsRunningAction;
  }

  private class MyDispatcher extends AnActionListener.Adapter implements IdeEventQueue.EventDispatcher, Disposable {
    private final String myActionId;
    private final int myModifierKeyCode;
    private final int myActionKeyCode;
    private final boolean mySkipIfActionHasShortcut;

    private final Couple<AtomicBoolean> ourPressed = Couple.of(new AtomicBoolean(false), new AtomicBoolean(false));
    private final Couple<AtomicBoolean> ourReleased = Couple.of(new AtomicBoolean(false), new AtomicBoolean(false));
    private final AtomicBoolean ourOtherKeyWasPressed = new AtomicBoolean(false);
    private final AtomicLong ourLastTimePressed = new AtomicLong(0);

    public MyDispatcher(@NotNull String actionId, int modifierKeyCode, int actionKeyCode, boolean skipIfActionHasShortcut) {
      myActionId = actionId;
      myModifierKeyCode = modifierKeyCode;
      myActionKeyCode = actionKeyCode;
      mySkipIfActionHasShortcut = skipIfActionHasShortcut;
    }

    @Override
    public boolean dispatch(@NotNull AWTEvent event) {
      if (event instanceof KeyEvent) {
        final KeyEvent keyEvent = (KeyEvent)event;
        final int keyCode = keyEvent.getKeyCode();
        LOG.debug("", this, event);
        if (keyCode == myModifierKeyCode) {
          if (hasOtherModifiers(keyEvent)) {
            resetState();
            return false;
          }
          if (myActionKeyCode == -1 && ourOtherKeyWasPressed.get() && Clock.getTime() - ourLastTimePressed.get() < 100) {
            resetState();
            return false;
          }
          ourOtherKeyWasPressed.set(false);
          if (ourPressed.first.get() && Clock.getTime() - ourLastTimePressed.get() > 500) {
            resetState();
          }
          handleModifier((KeyEvent)event);
          return false;
        } else if (ourPressed.first.get() && ourReleased.first.get() && ourPressed.second.get() && myActionKeyCode != -1) {
          if (keyCode == myActionKeyCode && !hasOtherModifiers(keyEvent)) {
            if (event.getID() == KeyEvent.KEY_PRESSED) {
              return run(keyEvent);
            }
            return true;
          }
          return false;
        } else {
          ourLastTimePressed.set(Clock.getTime());
          ourOtherKeyWasPressed.set(true);
          if (keyCode == KeyEvent.VK_ESCAPE || keyCode == KeyEvent.VK_TAB)  {
            ourLastTimePressed.set(0);
          }
        }
        resetState();
      }
      return false;
    }

    private boolean hasOtherModifiers(KeyEvent keyEvent) {
      final int modifiers = keyEvent.getModifiers();
      return !KEY_CODE_TO_MODIFIER_MAP.forEachEntry(new TIntIntProcedure() {
        @Override
        public boolean execute(int keyCode, int modifierMask) {
          return keyCode == myModifierKeyCode || (modifiers & modifierMask) == 0;
        }
      });
    }

    private void handleModifier(KeyEvent event) {
      if (ourPressed.first.get() && Clock.getTime() - ourLastTimePressed.get() > 300) {
        resetState();
        return;
      }

      if (event.getID() == KeyEvent.KEY_PRESSED) {
        if (!ourPressed.first.get()) {
          resetState();
          ourPressed.first.set(true);
          ourLastTimePressed.set(Clock.getTime());
          return;
        } else {
          if (ourPressed.first.get() && ourReleased.first.get()) {
            ourPressed.second.set(true);
            ourLastTimePressed.set(Clock.getTime());
            return;
          }
        }
      } else if (event.getID() == KeyEvent.KEY_RELEASED) {
        if (ourPressed.first.get() && !ourReleased.first.get()) {
          ourReleased.first.set(true);
          ourLastTimePressed.set(Clock.getTime());
          return;
        } else if (ourPressed.first.get() && ourReleased.first.get() && ourPressed.second.get()) {
          resetState();
          if (myActionKeyCode == -1 && !shouldSkipIfActionHasShortcut()) {
            run(event);
          }
          return;
        }
      }
      resetState();
    }

    private void resetState() {
      ourPressed.first.set(false);
      ourPressed.second.set(false);
      ourReleased.first.set(false);
      ourReleased.second.set(false);
    }

    private boolean run(KeyEvent event) {
      myIsRunningAction = true;
      try {
        AnAction action = myActionManagerEx.getAction(myActionId);
        if (action == null) return false;
        DataContext context = DataManager.getInstance().getDataContext(IdeFocusManager.findInstance().getFocusOwner());
        AnActionEvent anActionEvent = AnActionEvent.createFromAnAction(action, event, ActionPlaces.MAIN_MENU, context);
        action.update(anActionEvent);
        if (!anActionEvent.getPresentation().isEnabled()) return false;

        myActionManagerEx.fireBeforeActionPerformed(action, anActionEvent.getDataContext(), anActionEvent);
        action.actionPerformed(anActionEvent);
        myActionManagerEx.fireAfterActionPerformed(action, anActionEvent.getDataContext(), anActionEvent);
        ShortcutsCollector.recordDoubleShortcut(anActionEvent);
        return true;
      }
      finally {
        myIsRunningAction = false;
      }
    }

    private boolean shouldSkipIfActionHasShortcut() {
      return mySkipIfActionHasShortcut && getActiveKeymapShortcuts(myActionId).getShortcuts().length > 0;
    }

    @Override
    public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
      if (!myIsRunningAction) resetState();
    }

    @Override
    public void dispose() {
    }

    @Override
    public String toString() {
      return "modifier double-click dispatcher [modifierKeyCode=" + myModifierKeyCode +
             ",actionKeyCode=" + myActionKeyCode + ",actionId=" + myActionId + "]";
    }
  }
}
