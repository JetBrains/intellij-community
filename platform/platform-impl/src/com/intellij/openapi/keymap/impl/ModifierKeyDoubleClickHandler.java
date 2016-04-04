/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.util.Couple;
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

/**
 * Support for keyboard shortcuts like Control-double-click or Control-double-click+A
 *
 * Timings that are used in the implementation to detect double click were tuned for SearchEverywhere
 * functionality (invoked on double Shift), so if you need to change them, please make sure
 * SearchEverywhere behaviour remains intact.
 */
public class ModifierKeyDoubleClickHandler {
  private static final TIntIntHashMap KEY_CODE_TO_MODIFIER_MAP = new TIntIntHashMap();
  static {
    KEY_CODE_TO_MODIFIER_MAP.put(KeyEvent.VK_ALT, InputEvent.ALT_MASK);
    KEY_CODE_TO_MODIFIER_MAP.put(KeyEvent.VK_CONTROL, InputEvent.CTRL_MASK);
    KEY_CODE_TO_MODIFIER_MAP.put(KeyEvent.VK_META, InputEvent.META_MASK);
    KEY_CODE_TO_MODIFIER_MAP.put(KeyEvent.VK_SHIFT, InputEvent.SHIFT_MASK);
  }

  private static final ModifierKeyDoubleClickHandler INSTANCE = new ModifierKeyDoubleClickHandler();

  private final ConcurrentMap<String, IdeEventQueue.EventDispatcher> myDispatchers =
    ContainerUtil.newConcurrentMap();
  private boolean myIsRunningAction;
  
  private ModifierKeyDoubleClickHandler() { }

  public static ModifierKeyDoubleClickHandler getInstance() {
    return INSTANCE;
  }

  /**
   * @param actionId Id of action to be triggered on modifier+modifier[+actionKey]
   * @param modifierKeyCode keyCode for modifier, e.g. KeyEvent.VK_SHIFT
   * @param actionKeyCode keyCode for actionKey, or -1 if action should be triggered on bare modifier double click
   */
  public void registerAction(@NotNull String actionId,
                             int modifierKeyCode,
                             int actionKeyCode) {
    final MyDispatcher dispatcher = new MyDispatcher(actionId, modifierKeyCode, actionKeyCode);
    IdeEventQueue.EventDispatcher oldDispatcher = myDispatchers.put(actionId, dispatcher);
    IdeEventQueue.getInstance().addDispatcher(dispatcher, null);
    if (oldDispatcher != null) {
      IdeEventQueue.getInstance().removeDispatcher(oldDispatcher);
    }
  }

  public void unregisterAction(@NotNull String actionId) {
    IdeEventQueue.EventDispatcher oldDispatcher = myDispatchers.remove(actionId);
    if (oldDispatcher != null) {
      IdeEventQueue.getInstance().removeDispatcher(oldDispatcher);
    }
  }

  public boolean isRunningAction() {
    return myIsRunningAction;
  }

  private class MyDispatcher implements IdeEventQueue.EventDispatcher {
    private final String myActionId;
    private final int myModifierKeyCode;
    private final int myActionKeyCode;

    private final Couple<AtomicBoolean> ourPressed = Couple.of(new AtomicBoolean(false), new AtomicBoolean(false));
    private final Couple<AtomicBoolean> ourReleased = Couple.of(new AtomicBoolean(false), new AtomicBoolean(false));
    private final AtomicBoolean ourOtherKeyWasPressed = new AtomicBoolean(false);
    private final AtomicLong ourLastTimePressed = new AtomicLong(0);

    public MyDispatcher(@NotNull String actionId, int modifierKeyCode, int actionKeyCode) {
      myActionId = actionId;
      myModifierKeyCode = modifierKeyCode;
      myActionKeyCode = actionKeyCode;
    }

    @Override
    public boolean dispatch(AWTEvent event) {
      if (event instanceof KeyEvent) {
        final KeyEvent keyEvent = (KeyEvent)event;
        final int keyCode = keyEvent.getKeyCode();

        if (keyCode == myModifierKeyCode) {
          if (hasOtherModifiers(keyEvent)) {
            resetState();
            return false;
          }
          if (myActionKeyCode == -1 && ourOtherKeyWasPressed.get() && Clock.getTime() - ourLastTimePressed.get() < 500) {
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
          if (keyCode == myActionKeyCode) {
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
          if (myActionKeyCode == -1 && !isActionBound()) {
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
        ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
        AnAction action = actionManager.getAction(myActionId);
        DataContext context = DataManager.getInstance().getDataContext(IdeFocusManager.findInstance().getFocusOwner());
        AnActionEvent anActionEvent = AnActionEvent.createFromAnAction(action, event, ActionPlaces.MAIN_MENU, context);
        action.update(anActionEvent);
        if (!anActionEvent.getPresentation().isEnabled()) return false;

        actionManager.fireBeforeActionPerformed(action, anActionEvent.getDataContext(), anActionEvent);
        action.actionPerformed(anActionEvent);
        actionManager.fireAfterActionPerformed(action, anActionEvent.getDataContext(), anActionEvent);
        return true;
      }
      finally {
        myIsRunningAction = false;
      }
    }

    private boolean isActionBound() {
      return KeymapManager.getInstance().getActiveKeymap().getShortcuts(myActionId).length > 0;
    }
  }
}
