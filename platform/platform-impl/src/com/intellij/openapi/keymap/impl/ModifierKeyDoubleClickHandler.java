// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl;

import com.intellij.codeWithMe.ClientId;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.wm.IdeFocusManager;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts;

/**
 * Support for keyboard shortcuts like Control-double-click or Control-double-click+A
 * <p>
 * Timings that are used in the implementation to detect double click were tuned for SearchEverywhere
 * functionality (invoked on double Shift), so if you need to change them, please make sure
 * SearchEverywhere behaviour remains intact.
 *
 * @author Dmitry Batrak
 * @author Konstantin Bulenkov
 */
@Service(Service.Level.APP)
public final class ModifierKeyDoubleClickHandler {
  private static final Logger LOG = Logger.getInstance(ModifierKeyDoubleClickHandler.class);
  private static final Int2IntMap KEY_CODE_TO_MODIFIER_MAP = new Int2IntOpenHashMap();

  static {
    KEY_CODE_TO_MODIFIER_MAP.put(KeyEvent.VK_ALT, InputEvent.ALT_MASK);
    KEY_CODE_TO_MODIFIER_MAP.put(KeyEvent.VK_CONTROL, InputEvent.CTRL_MASK);
    KEY_CODE_TO_MODIFIER_MAP.put(KeyEvent.VK_META, InputEvent.META_MASK);
    KEY_CODE_TO_MODIFIER_MAP.put(KeyEvent.VK_SHIFT, InputEvent.SHIFT_MASK);
  }

  private final ConcurrentMap<String, MyDispatcher> myDispatchers = new ConcurrentHashMap<>();
  private boolean myIsRunningAction;

  public ModifierKeyDoubleClickHandler() {
    int modifierKeyCode = getMultiCaretActionModifier();

    registerAction(IdeActions.ACTION_EDITOR_CLONE_CARET_ABOVE, modifierKeyCode, KeyEvent.VK_UP);
    registerAction(IdeActions.ACTION_EDITOR_CLONE_CARET_BELOW, modifierKeyCode, KeyEvent.VK_DOWN);
    registerAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION, modifierKeyCode, KeyEvent.VK_LEFT);
    registerAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION, modifierKeyCode, KeyEvent.VK_RIGHT);
    registerAction(IdeActions.ACTION_EDITOR_MOVE_LINE_START_WITH_SELECTION, modifierKeyCode, KeyEvent.VK_HOME);
    registerAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END_WITH_SELECTION, modifierKeyCode, KeyEvent.VK_END);
  }

  public static final class MyAnActionListener implements AnActionListener {
    @Override
    public void beforeActionPerformed(@NotNull AnAction action,
                                      @NotNull AnActionEvent event) {
      ModifierKeyDoubleClickHandler doubleClickHandler = getInstance();
      if (doubleClickHandler.myIsRunningAction) {
        return;
      }

      for (MyDispatcher dispatcher : doubleClickHandler.myDispatchers.values()) {
        dispatcher.resetState();
      }
    }
  }

  @ApiStatus.Internal
  public static final class MyEventDispatcher implements IdeEventQueue.NonLockedEventDispatcher {
    @Override
    public boolean dispatch(@NotNull AWTEvent event) {
      if (!(event instanceof KeyEvent keyEvent)) {
        return false;
      }

      Application application = ApplicationManager.getApplication();
      if ((application != null && !application.isHeadlessEnvironment()) &&
          KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow() == null) {
        return false; // on macOS, we can receive modifier key events even if app isn't in focus (e.g. when Spotlight popup is shown)
      }

      ModifierKeyDoubleClickHandler doubleClickHandler = getInstance();

      Collection<MyDispatcher> dispatchers = doubleClickHandler.myDispatchers.values();
      if (dispatchers.isEmpty()) {
        return false;
      }
      return WriteIntentReadAction.<Boolean, RuntimeException>compute(() -> {
        boolean innerResult = false;
        for (MyDispatcher dispatcher : doubleClickHandler.myDispatchers.values()) {
          if (dispatcher.dispatch(keyEvent)) {
            innerResult = true;
          }
        }
        return innerResult;
      });
    }
  }

  public static ModifierKeyDoubleClickHandler getInstance() {
    return ApplicationManager.getApplication().getService(ModifierKeyDoubleClickHandler.class);
  }

  public static int getMultiCaretActionModifier() {
    return SystemInfoRt.isMac ? KeyEvent.VK_ALT : KeyEvent.VK_CONTROL;
  }

  /**
   * @param actionId                Id of action to be triggered on modifier+modifier[+actionKey]
   * @param modifierKeyCode         keyCode for modifier, e.g. KeyEvent.VK_SHIFT
   * @param actionKeyCode           keyCode for actionKey, or -1 if action should be triggered on bare modifier double click
   * @param skipIfActionHasShortcut do not invoke action if a shortcut is already bound to it in keymap
   */
  public void registerAction(@NotNull String actionId,
                             int modifierKeyCode,
                             int actionKeyCode,
                             boolean skipIfActionHasShortcut) {
    myDispatchers.put(actionId, new MyDispatcher(actionId, modifierKeyCode, actionKeyCode, skipIfActionHasShortcut));
  }

  /**
   * @param actionId        Id of action to be triggered on modifier+modifier[+actionKey]
   * @param modifierKeyCode keyCode for modifier, e.g. KeyEvent.VK_SHIFT
   * @param actionKeyCode   keyCode for actionKey, or -1 if action should be triggered on bare modifier double click
   */
  public void registerAction(@NotNull String actionId, int modifierKeyCode, int actionKeyCode) {
    registerAction(actionId, modifierKeyCode, actionKeyCode, true);
  }

  public void unregisterAction(@NotNull String actionId) {
    myDispatchers.remove(actionId);
  }

  public boolean isRunningAction() {
    return myIsRunningAction;
  }

  private final class MyDispatcher {
    private final String myActionId;
    private final int myModifierKeyCode;
    private final int myActionKeyCode;
    private final boolean mySkipIfActionHasShortcut;

    private final Couple<AtomicBoolean> ourPressed = Couple.of(new AtomicBoolean(false), new AtomicBoolean(false));
    private final Couple<AtomicBoolean> ourReleased = Couple.of(new AtomicBoolean(false), new AtomicBoolean(false));
    private final AtomicBoolean ourOtherKeyWasPressed = new AtomicBoolean(false);
    private final AtomicLong ourLastTimePressed = new AtomicLong(0);

    MyDispatcher(@NotNull String actionId, int modifierKeyCode, int actionKeyCode, boolean skipIfActionHasShortcut) {
      myActionId = actionId;
      myModifierKeyCode = modifierKeyCode;
      myActionKeyCode = actionKeyCode;
      mySkipIfActionHasShortcut = skipIfActionHasShortcut;
    }

    public boolean dispatch(@NotNull KeyEvent event) {
      int keyCode = event.getKeyCode();
      if (LOG.isTraceEnabled()) {
        LOG.trace(this + " " + event);
      }
      if (keyCode == myModifierKeyCode) {
        if (hasOtherModifiers(event)) {
          resetState();
          return false;
        }

        if (myActionKeyCode == -1 && ourOtherKeyWasPressed.get() && event.getWhen() - ourLastTimePressed.get() < 100) {
          resetState();
          return false;
        }

        ourOtherKeyWasPressed.set(false);
        if (ourPressed.first.get() && event.getWhen() - ourLastTimePressed.get() > 500) {
          resetState();
        }

        return handleModifier(event);
      }
      else if (ourPressed.first.get() && ourReleased.first.get() && ourPressed.second.get() && myActionKeyCode != -1) {
        if (keyCode == myActionKeyCode && !hasOtherModifiers(event)) {
          if (event.getID() == KeyEvent.KEY_PRESSED) {
            return run(event);
          }
          return true;
        }
        return false;
      }
      else {
        ourLastTimePressed.set(event.getWhen());
        ourOtherKeyWasPressed.set(true);
        if (keyCode == KeyEvent.VK_ESCAPE || keyCode == KeyEvent.VK_TAB) {
          ourLastTimePressed.set(0);
        }
      }
      resetState();
      return false;
    }

    private boolean hasOtherModifiers(KeyEvent keyEvent) {
      int modifiers = keyEvent.getModifiers();
      for (Int2IntMap.Entry entry : KEY_CODE_TO_MODIFIER_MAP.int2IntEntrySet()) {
        if (!(entry.getIntKey() == myModifierKeyCode || (modifiers & entry.getIntValue()) == 0)) {
          return true;
        }
      }
      return false;
    }

    private boolean handleModifier(KeyEvent event) {
      if (ourPressed.first.get() && event.getWhen() - ourLastTimePressed.get() > 300) {
        resetState();
        return false;
      }

      if (event.getID() == KeyEvent.KEY_PRESSED) {
        if (!ourPressed.first.get()) {
          resetState();
          ourPressed.first.set(true);
          ourLastTimePressed.set(event.getWhen());
          return false;
        }
        else {
          if (ourPressed.first.get() && ourReleased.first.get()) {
            ourPressed.second.set(true);
            ourLastTimePressed.set(event.getWhen());
            return false;
          }
        }
      }
      else if (event.getID() == KeyEvent.KEY_RELEASED) {
        if (ourPressed.first.get() && !ourReleased.first.get()) {
          ourReleased.first.set(true);
          ourLastTimePressed.set(event.getWhen());
          return false;
        }
        else if (ourPressed.first.get() && ourReleased.first.get() && ourPressed.second.get()) {
          resetState();
          if (myActionKeyCode == -1 && !shouldSkipIfActionHasShortcut()) {
            if (!ClientId.isCurrentlyUnderLocalId()) {
              return false;
            }

            run(event);
            return true;
          }
          return false;
        }
      }
      resetState();
      return false;
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
        ActionManagerEx ex = ActionManagerEx.getInstanceEx();
        AnAction action = ex.getAction(myActionId);
        if (action == null) return false;

        if (!action.isEnabledInModalContext()) {
          // This check is copied IdeKeyEventDispatcher#dispatchKeyEvent method
          Window focusedWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
          if (focusedWindow != null && IdeKeyEventDispatcher.isModalContext(focusedWindow)) {
            return false;
          }
        }

        DataContext context = calculateContext();
        AnActionEvent actionEvent = AnActionEvent.createFromAnAction(action, event, ActionPlaces.KEYBOARD_SHORTCUT, context);
        AnActionResult result = ActionUtil.performAction(action, actionEvent);
        return !result.isIgnored();
      }
      finally {
        myIsRunningAction = false;
      }
    }

    private static @NotNull DataContext calculateContext() {
      IdeFocusManager focusManager = IdeFocusManager.findInstance();
      Component focusedComponent = focusManager.getFocusOwner();
      Window ideWindow = focusManager.getLastFocusedIdeWindow();
      return ideWindow == focusedComponent || focusedComponent == focusManager.getLastFocusedFor(ideWindow)
             ? DataManager.getInstance().getDataContext(focusedComponent)
             : DataManager.getInstance().getDataContext();
    }

    private boolean shouldSkipIfActionHasShortcut() {
      return mySkipIfActionHasShortcut && getActiveKeymapShortcuts(myActionId).hasShortcuts();
    }

    @Override
    public String toString() {
      return "modifier double-click dispatcher [modifierKeyCode=" + myModifierKeyCode +
             ",actionKeyCode=" + myActionKeyCode + ",actionId=" + myActionId + "]";
    }
  }
}
