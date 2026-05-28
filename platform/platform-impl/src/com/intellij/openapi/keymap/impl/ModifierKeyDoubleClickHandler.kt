// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl;

import com.intellij.codeWithMe.ClientId;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionResult;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.KeyboardGestureAction;
import com.intellij.openapi.actionSystem.KeyboardModifierGestureShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.ActionRuntimeRegistrar;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapManagerListener;
import com.intellij.openapi.keymap.impl.ui.ShortcutTextField;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.JavaCoroutines;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
  @SuppressWarnings("deprecation")
  private static final int SUPPORTED_MODIFIER_MASKS =
    InputEvent.SHIFT_MASK | InputEvent.ALT_MASK | InputEvent.CTRL_MASK | InputEvent.META_MASK;
  private static final int SUPPORTED_MODIFIER_DOWN_MASKS =
    InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK;
  private static final int SUPPORTED_MODIFIER_INPUT_MASKS = SUPPORTED_MODIFIER_MASKS | SUPPORTED_MODIFIER_DOWN_MASKS;

  static {
    KEY_CODE_TO_MODIFIER_MAP.put(KeyEvent.VK_ALT, InputEvent.ALT_MASK);
    KEY_CODE_TO_MODIFIER_MAP.put(KeyEvent.VK_CONTROL, InputEvent.CTRL_MASK);
    KEY_CODE_TO_MODIFIER_MAP.put(KeyEvent.VK_META, InputEvent.META_MASK);
    KEY_CODE_TO_MODIFIER_MAP.put(KeyEvent.VK_SHIFT, InputEvent.SHIFT_MASK);
  }

  private final ConcurrentMap<DispatcherKey, MyDispatcher> myDispatchers = new ConcurrentHashMap<>();
  private final Set<DispatcherKey> myKeymapDispatcherKeys = ConcurrentHashMap.newKeySet();
  private final Set<String> mySuppressedActionIds = ConcurrentHashMap.newKeySet();
  private final Set<DispatcherKey> mySuppressedShortcuts = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean myKeymapShortcutTrackerInstalled = new AtomicBoolean();
  private final AtomicBoolean myKeymapShortcutSyncScheduled = new AtomicBoolean();
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

  @Internal
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
      if (isShortcutTextFieldEvent(keyEvent)) {
        return false;
      }

      ModifierKeyDoubleClickHandler doubleClickHandler = getInstance();

      Collection<MyDispatcher> dispatchers = doubleClickHandler.myDispatchers.values();
      if (dispatchers.isEmpty()) {
        return false;
      }

      boolean innerResult = false;
      for (MyDispatcher dispatcher : doubleClickHandler.myDispatchers.values()) {
        if (dispatcher.dispatch(keyEvent)) {
          innerResult = true;
        }
      }
      return innerResult;
    }

    private static boolean isShortcutTextFieldEvent(@NotNull KeyEvent event) {
      if (event.getSource() instanceof ShortcutTextField) {
        return true;
      }
      return KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() instanceof ShortcutTextField;
    }
  }

  public static ModifierKeyDoubleClickHandler getInstance() {
    return ApplicationManager.getApplication().getService(ModifierKeyDoubleClickHandler.class);
  }

  @Internal
  public static void scheduleKeymapShortcutSyncIfCreated() {
    Application application = ApplicationManager.getApplication();
    if (application == null) {
      return;
    }

    ModifierKeyDoubleClickHandler handler = application.getServiceIfCreated(ModifierKeyDoubleClickHandler.class);
    if (handler != null) {
      handler.scheduleKeymapShortcutSync();
    }
  }

  static final class ShortcutTracker implements ActionConfigurationCustomizer,
                                                       ActionConfigurationCustomizer.AsyncLightCustomizeStrategy {
    @Override
    public @Nullable Object customize(@NotNull ActionRuntimeRegistrar actionRegistrar,
                                      @NotNull Continuation<? super Unit> $completion) {
      return JavaCoroutines.suspendJava(jc -> {
        getInstance().installKeymapShortcutTracker();
        jc.resume(Unit.INSTANCE);
      }, $completion);
    }
  }

  private void installKeymapShortcutTracker() {
    if (!myKeymapShortcutTrackerInstalled.compareAndSet(false, true)) {
      return;
    }

    scheduleKeymapShortcutSync();
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(KeymapManagerListener.TOPIC, new KeymapManagerListener() {
      @Override
      public void activeKeymapChanged(@Nullable Keymap keymap) {
        scheduleKeymapShortcutSync();
      }

      @Override
      public void shortcutsChanged(@NotNull Keymap keymap, @NonNls @NotNull Collection<String> actionIds, boolean fromSettings) {
        scheduleKeymapShortcutSync();
      }
    });
  }

  private void scheduleKeymapShortcutSync() {
    if (!myKeymapShortcutSyncScheduled.compareAndSet(false, true)) {
      return;
    }
    ApplicationManager.getApplication().invokeLater(() -> {
      myKeymapShortcutSyncScheduled.set(false);
      syncKeymapShortcuts();
    });
  }

  private void syncKeymapShortcuts() {
    clearKeymapShortcuts();

    KeymapManager keymapManager = ApplicationManager.getApplication().getServiceIfCreated(KeymapManager.class);
    Keymap activeKeymap = keymapManager == null ? null : keymapManager.getActiveKeymap();
    if (activeKeymap == null) {
      return;
    }

    ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    ActionRuntimeRegistrar actionRegistrar = actionManager.asActionRuntimeRegistrar();
    List<String> actionIds = new ArrayList<>(activeKeymap.getActionIdList());
    actionIds.sort(actionManager.getRegistrationOrderComparator());
    for (String actionId : actionIds) {
      if (actionRegistrar.getActionOrStub(actionId) == null) {
        continue;
      }
      for (Shortcut shortcut : activeKeymap.getShortcuts(actionId)) {
        if (shortcut instanceof KeyboardModifierGestureShortcut gestureShortcut) {
          registerKeymapShortcut(actionId, gestureShortcut);
        }
      }
    }
  }

  private synchronized void clearKeymapShortcuts() {
    for (DispatcherKey key : myKeymapDispatcherKeys) {
      myDispatchers.remove(key);
    }
    myKeymapDispatcherKeys.clear();
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
    registerAction(actionId, modifierKeyCode, actionKeyCode, -1, skipIfActionHasShortcut);
  }

  /**
   * @param actionId                Id of action to be triggered on modifier+modifier[+actionKey]
   * @param modifierKeyCode         keyCode for modifier, e.g. KeyEvent.VK_SHIFT
   * @param actionKeyCode           keyCode for actionKey, or -1 if action should be triggered on bare modifier double click
   * @param requiredModifierKeyCode keyCode for an additional modifier that must be held, or -1
   * @param skipIfActionHasShortcut do not invoke action if a shortcut is already bound to it in keymap
   */
  @Internal
  public synchronized void registerAction(@NotNull String actionId,
                                          int modifierKeyCode,
                                          int actionKeyCode,
                                          int requiredModifierKeyCode,
                                          boolean skipIfActionHasShortcut) {
    int requiredModifierMask = getRequiredModifierMask(requiredModifierKeyCode);
    DispatcherKey key = new DispatcherKey(actionId, modifierKeyCode, actionKeyCode, requiredModifierMask);
    if (mySuppressedActionIds.contains(actionId) || mySuppressedShortcuts.contains(key)) {
      return;
    }
    myDispatchers.put(key, new MyDispatcher(actionId, modifierKeyCode, actionKeyCode, requiredModifierMask, skipIfActionHasShortcut));
  }

  @Internal
  public synchronized boolean registerShortcut(@NotNull String actionId,
                                               @NotNull KeyboardModifierGestureShortcut shortcut,
                                               boolean skipIfActionHasShortcut) {
    DoubleClickShortcut doubleClickShortcut = DoubleClickShortcut.from(shortcut);
    if (doubleClickShortcut == null) {
      LOG.warn("Cannot register modifier double-click shortcut for action '" + actionId +
               "': unsupported shortcut " + shortcut);
      return false;
    }
    return registerShortcut(actionId, doubleClickShortcut, skipIfActionHasShortcut, false);
  }

  private synchronized void registerKeymapShortcut(@NotNull String actionId,
                                                   @NotNull KeyboardModifierGestureShortcut shortcut) {
    DoubleClickShortcut doubleClickShortcut = DoubleClickShortcut.from(shortcut);
    if (doubleClickShortcut != null) {
      registerShortcut(actionId, doubleClickShortcut, false, true);
    }
  }

  private synchronized boolean registerShortcut(@NotNull String actionId,
                                                @NotNull DoubleClickShortcut doubleClickShortcut,
                                                boolean skipIfActionHasShortcut,
                                                boolean keymapShortcut) {
    DispatcherKey key = doubleClickShortcut.toDispatcherKey(actionId);
    if (mySuppressedActionIds.contains(actionId) || mySuppressedShortcuts.contains(key)) {
      LOG.debug("Skipped modifier double-click registration for '", actionId, "': shortcut is suppressed");
      return false;
    }
    if (keymapShortcut) {
      unregisterConflictingKeymapShortcut(doubleClickShortcut);
    }
    myDispatchers.put(key, new MyDispatcher(actionId,
                                            doubleClickShortcut.modifierKeyCode,
                                            -1,
                                            doubleClickShortcut.requiredModifierMask,
                                            skipIfActionHasShortcut));
    if (keymapShortcut) {
      myKeymapDispatcherKeys.add(key);
    }
    return true;
  }

  private void unregisterConflictingKeymapShortcut(@NotNull DoubleClickShortcut shortcut) {
    myKeymapDispatcherKeys.removeIf(key -> {
      if (!shortcut.matches(key)) {
        return false;
      }
      myDispatchers.remove(key);
      return true;
    });
  }

  /**
   * @param actionId        Id of action to be triggered on modifier+modifier[+actionKey]
   * @param modifierKeyCode keyCode for modifier, e.g. KeyEvent.VK_SHIFT
   * @param actionKeyCode   keyCode for actionKey, or -1 if action should be triggered on bare modifier double click
   */
  public void registerAction(@NotNull String actionId, int modifierKeyCode, int actionKeyCode) {
    registerAction(actionId, modifierKeyCode, actionKeyCode, true);
  }

  public synchronized void unregisterAction(@NotNull String actionId) {
    myDispatchers.keySet().removeIf(key -> key.myActionId.equals(actionId));
    myKeymapDispatcherKeys.removeIf(key -> key.myActionId.equals(actionId));
  }

  @Internal
  public synchronized void suppressShortcut(@NotNull String actionId, @NotNull KeyboardModifierGestureShortcut shortcut) {
    DoubleClickShortcut doubleClickShortcut = DoubleClickShortcut.from(shortcut);
    if (doubleClickShortcut == null) {
      return;
    }
    DispatcherKey key = doubleClickShortcut.toDispatcherKey(actionId);
    mySuppressedShortcuts.add(key);
    myDispatchers.remove(key);
    myKeymapDispatcherKeys.remove(key);
  }

  @Internal
  public synchronized void suppressAction(@NotNull String actionId) {
    mySuppressedActionIds.add(actionId);
    unregisterAction(actionId);
  }

  @Internal
  public synchronized void unsuppressAction(@NotNull String actionId) {
    mySuppressedActionIds.remove(actionId);
    mySuppressedShortcuts.removeIf(key -> key.myActionId.equals(actionId));
  }

  @Internal
  public synchronized boolean isActionRegistered(@NotNull String actionId) {
    for (DispatcherKey key : myDispatchers.keySet()) {
      if (key.myActionId.equals(actionId)) {
        return true;
      }
    }
    return false;
  }

  @Internal
  public synchronized boolean isShortcutRegistered(@NotNull String actionId, @NotNull KeyboardModifierGestureShortcut shortcut) {
    DoubleClickShortcut doubleClickShortcut = DoubleClickShortcut.from(shortcut);
    return doubleClickShortcut != null && myDispatchers.containsKey(doubleClickShortcut.toDispatcherKey(actionId));
  }

  public boolean isRunningAction() {
    return myIsRunningAction;
  }

  private static final class DispatcherKey {
    private final String myActionId;
    private final int myModifierKeyCode;
    private final int myActionKeyCode;
    private final int myRequiredModifierMask;

    private DispatcherKey(@NotNull String actionId, int modifierKeyCode, int actionKeyCode, int requiredModifierMask) {
      myActionId = actionId;
      myModifierKeyCode = modifierKeyCode;
      myActionKeyCode = actionKeyCode;
      myRequiredModifierMask = requiredModifierMask;
    }

    @Override
    public boolean equals(Object object) {
      if (this == object) return true;
      if (!(object instanceof DispatcherKey key)) return false;
      return myModifierKeyCode == key.myModifierKeyCode &&
             myActionKeyCode == key.myActionKeyCode &&
             myRequiredModifierMask == key.myRequiredModifierMask &&
             myActionId.equals(key.myActionId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myActionId, myModifierKeyCode, myActionKeyCode, myRequiredModifierMask);
    }
  }

  private static final class DoubleClickShortcut {
    private final int modifierKeyCode;
    private final int requiredModifierMask;

    private DoubleClickShortcut(int modifierKeyCode, int requiredModifierMask) {
      this.modifierKeyCode = modifierKeyCode;
      this.requiredModifierMask = requiredModifierMask;
    }

    private static DoubleClickShortcut from(@NotNull KeyboardModifierGestureShortcut shortcut) {
      if (shortcut.getType() != KeyboardGestureAction.ModifierType.dblClick) {
        return null;
      }

      int modifierKeyCode = shortcut.getStroke().getKeyCode();
      int modifierMask = KEY_CODE_TO_MODIFIER_MAP.get(modifierKeyCode);
      if (modifierMask == 0) {
        return null;
      }

      int strokeModifiers = shortcut.getStroke().getModifiers();
      if (hasUnsupportedModifiers(strokeModifiers)) {
        return null;
      }

      int shortcutModifiers = normalizeModifiers(strokeModifiers) & SUPPORTED_MODIFIER_MASKS;
      if ((shortcutModifiers & modifierMask) == 0) {
        return null;
      }

      int requiredModifiers = shortcutModifiers & ~modifierMask;
      return new DoubleClickShortcut(modifierKeyCode, requiredModifiers);
    }

    private @NotNull DispatcherKey toDispatcherKey(@NotNull String actionId) {
      return new DispatcherKey(actionId, modifierKeyCode, -1, requiredModifierMask);
    }

    private boolean matches(@NotNull DispatcherKey key) {
      return key.myModifierKeyCode == modifierKeyCode &&
             key.myActionKeyCode == -1 &&
             key.myRequiredModifierMask == requiredModifierMask;
    }
  }

  @SuppressWarnings("deprecation")
  private static int normalizeModifiers(int modifiers) {
    if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) modifiers |= InputEvent.SHIFT_MASK;
    if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) modifiers |= InputEvent.ALT_MASK;
    if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) modifiers |= InputEvent.CTRL_MASK;
    if ((modifiers & InputEvent.META_DOWN_MASK) != 0) modifiers |= InputEvent.META_MASK;
    return modifiers;
  }

  private static boolean hasUnsupportedModifiers(int modifiers) {
    return (modifiers & ~SUPPORTED_MODIFIER_INPUT_MASKS) != 0;
  }

  @SuppressWarnings("deprecation")
  private static int getEventModifiers(@NotNull KeyEvent event) {
    return event.getModifiers() | event.getModifiersEx();
  }

  private static int getRequiredModifierMask(int requiredModifierKeyCode) {
    if (requiredModifierKeyCode == -1) {
      return 0;
    }
    int modifierMask = getModifierMask(requiredModifierKeyCode);
    if (modifierMask == 0) {
      throw new IllegalArgumentException("Unsupported required modifier keyCode: " + requiredModifierKeyCode);
    }
    return modifierMask;
  }

  private static int getModifierMask(int keyCode) {
    return KEY_CODE_TO_MODIFIER_MAP.get(keyCode);
  }

  private final class MyDispatcher {
    private final String myActionId;
    private final int myModifierKeyCode;
    private final int myActionKeyCode;
    private final int myRequiredModifierMask;
    private final boolean mySkipIfActionHasShortcut;

    private final Couple<AtomicBoolean> ourPressed = Couple.of(new AtomicBoolean(false), new AtomicBoolean(false));
    private final Couple<AtomicBoolean> ourReleased = Couple.of(new AtomicBoolean(false), new AtomicBoolean(false));
    private final AtomicBoolean ourOtherKeyWasPressed = new AtomicBoolean(false);
    private final AtomicLong ourLastTimePressed = new AtomicLong(0);

    MyDispatcher(@NotNull String actionId,
                 int modifierKeyCode,
                 int actionKeyCode,
                 int requiredModifierMask,
                 boolean skipIfActionHasShortcut) {
      myActionId = actionId;
      myModifierKeyCode = modifierKeyCode;
      myActionKeyCode = actionKeyCode;
      myRequiredModifierMask = requiredModifierMask;
      mySkipIfActionHasShortcut = skipIfActionHasShortcut;
    }

    public boolean dispatch(@NotNull KeyEvent event) {
      int keyCode = event.getKeyCode();
      if (LOG.isTraceEnabled()) {
        LOG.trace(this + " " + event);
      }
      if (keyCode == myModifierKeyCode) {
        if (hasOtherModifiers(event) || !hasRequiredModifier(event)) {
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
      else if (isRequiredModifierKey(keyCode)) {
        if (hasOtherModifiers(event)) {
          resetState();
        }
        return false;
      }
      else if (ourPressed.first.get() && ourReleased.first.get() && ourPressed.second.get() && myActionKeyCode != -1) {
        if (keyCode == myActionKeyCode && !hasOtherModifiers(event) && hasRequiredModifier(event)) {
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
      int eventModifiers = getEventModifiers(keyEvent);
      if (hasUnsupportedModifiers(eventModifiers)) {
        return true;
      }

      int modifiers = normalizeModifiers(eventModifiers);
      int allowedModifiers = getModifierMask(myModifierKeyCode) | myRequiredModifierMask;
      for (Int2IntMap.Entry entry : KEY_CODE_TO_MODIFIER_MAP.int2IntEntrySet()) {
        if ((modifiers & entry.getIntValue()) != 0 && (allowedModifiers & entry.getIntValue()) == 0) {
          return true;
        }
      }
      return false;
    }

    private boolean isRequiredModifierKey(int keyCode) {
      int modifierMask = getModifierMask(keyCode);
      return modifierMask != 0 && (myRequiredModifierMask & modifierMask) != 0;
    }

    private boolean hasRequiredModifier(KeyEvent event) {
      return myRequiredModifierMask == 0 || (normalizeModifiers(getEventModifiers(event)) & myRequiredModifierMask) == myRequiredModifierMask;
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

    @SuppressWarnings("removal")
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
             ",actionKeyCode=" + myActionKeyCode +
             ",requiredModifierMask=" + myRequiredModifierMask +
             ",actionId=" + myActionId + "]";
    }
  }
}
