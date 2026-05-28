// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION")

package com.intellij.openapi.keymap.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.ide.DataManager
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.KeyboardGestureAction
import com.intellij.openapi.actionSystem.KeyboardModifierGestureShortcut
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.ex.ActionRuntimeRegistrar
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.keymap.impl.ui.ShortcutTextField
import com.intellij.openapi.util.Couple
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import it.unimi.dsi.fastutil.ints.Int2IntMap
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import java.awt.AWTEvent
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

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
@Service(Service.Level.APP)
class ModifierKeyDoubleClickHandler {
  private val myDispatchers: MutableMap<DispatcherKey, MyDispatcher> = ConcurrentHashMap()
  private val myKeymapDispatcherKeys: MutableSet<DispatcherKey> = ConcurrentHashMap.newKeySet()
  private val mySuppressedActionIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
  private val mySuppressedShortcuts: MutableSet<DispatcherKey> = ConcurrentHashMap.newKeySet()
  private val myKeymapShortcutTrackerInstalled = AtomicBoolean()
  private val myKeymapShortcutSyncScheduled = AtomicBoolean()
  private var myIsRunningAction = false

  init {
    val modifierKeyCode = getMultiCaretActionModifier()

    registerAction(IdeActions.ACTION_EDITOR_CLONE_CARET_ABOVE, modifierKeyCode, KeyEvent.VK_UP)
    registerAction(IdeActions.ACTION_EDITOR_CLONE_CARET_BELOW, modifierKeyCode, KeyEvent.VK_DOWN)
    registerAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION, modifierKeyCode, KeyEvent.VK_LEFT)
    registerAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION, modifierKeyCode, KeyEvent.VK_RIGHT)
    registerAction(IdeActions.ACTION_EDITOR_MOVE_LINE_START_WITH_SELECTION, modifierKeyCode, KeyEvent.VK_HOME)
    registerAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END_WITH_SELECTION, modifierKeyCode, KeyEvent.VK_END)
  }

  class MyAnActionListener : AnActionListener {
    override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
      val doubleClickHandler = getInstance()
      if (doubleClickHandler.myIsRunningAction) {
        return
      }

      for (dispatcher in doubleClickHandler.myDispatchers.values) {
        dispatcher.resetState()
      }
    }
  }

  @Internal
  class MyEventDispatcher : IdeEventQueue.NonLockedEventDispatcher {
    override fun dispatch(e: AWTEvent): Boolean {
      if (e !is KeyEvent) {
        return false
      }

      val application = ApplicationManager.getApplication()
      if (application != null && !application.isHeadlessEnvironment &&
          KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow == null) {
        return false // on macOS, we can receive modifier key events even if app isn't in focus (e.g. when Spotlight popup is shown)
      }
      if (isShortcutTextFieldEvent(e)) {
        return false
      }

      val doubleClickHandler = getInstance()

      val dispatchers = doubleClickHandler.myDispatchers.values
      if (dispatchers.isEmpty()) {
        return false
      }

      var innerResult = false
      for (dispatcher in dispatchers) {
        if (dispatcher.dispatch(e)) {
          innerResult = true
        }
      }
      return innerResult
    }

    private fun isShortcutTextFieldEvent(event: KeyEvent): Boolean {
      return event.source is ShortcutTextField || KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner is ShortcutTextField
    }
  }

  @Internal
  class ShortcutTracker : ActionConfigurationCustomizer, ActionConfigurationCustomizer.AsyncLightCustomizeStrategy {
    override suspend fun customize(actionRegistrar: ActionRuntimeRegistrar) {
      serviceAsync<ModifierKeyDoubleClickHandler>().installKeymapShortcutTracker()
    }
  }

  private fun installKeymapShortcutTracker() {
    if (!myKeymapShortcutTrackerInstalled.compareAndSet(false, true)) {
      return
    }

    scheduleKeymapShortcutSync()
    ApplicationManager.getApplication().messageBus.connect().subscribe(KeymapManagerListener.TOPIC, object : KeymapManagerListener {
      override fun activeKeymapChanged(keymap: Keymap?) {
        scheduleKeymapShortcutSync()
      }

      override fun shortcutsChanged(keymap: Keymap, @NonNls actionIds: Collection<String>, fromSettings: Boolean) {
        scheduleKeymapShortcutSync()
      }
    })
  }

  private fun scheduleKeymapShortcutSync() {
    if (!myKeymapShortcutSyncScheduled.compareAndSet(false, true)) {
      return
    }
    service<CoreUiCoroutineScopeHolder>().coroutineScope.launch {
      myKeymapShortcutSyncScheduled.set(false)
      syncKeymapShortcuts()
    }
  }

  private fun syncKeymapShortcuts() {
    val keymapManager = ApplicationManager.getApplication().getServiceIfCreated(KeymapManager::class.java)
    val activeKeymap = keymapManager?.activeKeymap
    if (activeKeymap == null) {
      clearKeymapShortcuts()
      return
    }

    val actionManager = ActionManagerEx.getInstanceEx()
    val actionRegistrar = actionManager.asActionRuntimeRegistrar()
    val actionIds = activeKeymap.actionIdList.toMutableList()
    val newKeymapDispatcherKeys = HashSet<DispatcherKey>()
    actionIds.sortWith(actionManager.registrationOrderComparator)
    for (actionId in actionIds) {
      if (actionRegistrar.getActionOrStub(actionId) == null) {
        continue
      }
      for (shortcut in activeKeymap.getShortcuts(actionId)) {
        if (shortcut is KeyboardModifierGestureShortcut) {
          collectKeymapShortcut(actionId, shortcut, newKeymapDispatcherKeys)
        }
      }
    }

    retainKeymapShortcuts(newKeymapDispatcherKeys)
  }

  @Synchronized
  private fun clearKeymapShortcuts() {
    for (key in myKeymapDispatcherKeys) {
      myDispatchers.remove(key)
    }
    myKeymapDispatcherKeys.clear()
  }

  /**
   * @param actionId                Id of action to be triggered on modifier+modifier[+actionKey]
   * @param modifierKeyCode         keyCode for modifier, e.g. KeyEvent.VK_SHIFT
   * @param actionKeyCode           keyCode for actionKey, or -1 if action should be triggered on bare modifier double click
   * @param skipIfActionHasShortcut do not invoke action if a shortcut is already bound to it in keymap
   */
  fun registerAction(
    @NonNls actionId: String,
    modifierKeyCode: Int,
    actionKeyCode: Int,
    skipIfActionHasShortcut: Boolean,
  ) {
    registerAction(actionId, modifierKeyCode, actionKeyCode, -1, skipIfActionHasShortcut)
  }

  /**
   * @param actionId                Id of action to be triggered on modifier+modifier[+actionKey]
   * @param modifierKeyCode         keyCode for modifier, e.g. KeyEvent.VK_SHIFT
   * @param actionKeyCode           keyCode for actionKey, or -1 if action should be triggered on bare modifier double click
   * @param requiredModifierKeyCode keyCode for an additional modifier that must be held, or -1
   * @param skipIfActionHasShortcut do not invoke action if a shortcut is already bound to it in keymap
   */
  @Internal
  @Synchronized
  fun registerAction(
    @NonNls actionId: String,
    modifierKeyCode: Int,
    actionKeyCode: Int,
    requiredModifierKeyCode: Int,
    skipIfActionHasShortcut: Boolean,
  ) {
    val requiredModifierMask = getRequiredModifierMask(requiredModifierKeyCode)
    val key = DispatcherKey(actionId, modifierKeyCode, actionKeyCode, requiredModifierMask)
    if (mySuppressedActionIds.contains(actionId) || mySuppressedShortcuts.contains(key)) {
      return
    }
    myKeymapDispatcherKeys.remove(key)
    myDispatchers[key] = MyDispatcher(actionId, modifierKeyCode, actionKeyCode, requiredModifierMask, skipIfActionHasShortcut)
  }

  @Internal
  @Synchronized
  fun registerShortcut(
    @NonNls actionId: String,
    shortcut: KeyboardModifierGestureShortcut,
    skipIfActionHasShortcut: Boolean,
  ): Boolean {
    val doubleClickShortcut = DoubleClickShortcut.from(shortcut)
    if (doubleClickShortcut == null) {
      LOG.warn("Cannot register modifier double-click shortcut for action '$actionId': unsupported shortcut $shortcut")
      return false
    }
    return registerShortcut(actionId, doubleClickShortcut, skipIfActionHasShortcut)
  }

  private fun collectKeymapShortcut(
    @NonNls actionId: String,
    shortcut: KeyboardModifierGestureShortcut,
    newKeymapDispatcherKeys: MutableSet<DispatcherKey>,
  ) {
    val doubleClickShortcut = DoubleClickShortcut.from(shortcut)
    if (doubleClickShortcut != null) {
      val key = doubleClickShortcut.toDispatcherKey(actionId)
      if (isShortcutRegistrationSuppressed(actionId, key)) {
        return
      }
      newKeymapDispatcherKeys.removeIf { existingKey -> doubleClickShortcut.matches(existingKey) }
      newKeymapDispatcherKeys.add(key)
    }
  }

  @Synchronized
  private fun retainKeymapShortcuts(newKeymapDispatcherKeys: Set<DispatcherKey>) {
    for (key in myKeymapDispatcherKeys) {
      if (!newKeymapDispatcherKeys.contains(key)) {
        myDispatchers.remove(key)
      }
    }
    for (key in newKeymapDispatcherKeys) {
      if (!myKeymapDispatcherKeys.contains(key)) {
        myDispatchers[key] = MyDispatcher(key.actionId, key.modifierKeyCode, key.actionKeyCode, key.requiredModifierMask, false)
      }
    }
    myKeymapDispatcherKeys.clear()
    myKeymapDispatcherKeys.addAll(newKeymapDispatcherKeys)
  }

  @Synchronized
  private fun registerShortcut(
    @NonNls actionId: String,
    doubleClickShortcut: DoubleClickShortcut,
    skipIfActionHasShortcut: Boolean,
  ): Boolean {
    val key = doubleClickShortcut.toDispatcherKey(actionId)
    if (isShortcutRegistrationSuppressed(actionId, key)) {
      return false
    }
    myKeymapDispatcherKeys.remove(key)
    myDispatchers[key] = MyDispatcher(
      actionId,
      doubleClickShortcut.modifierKeyCode,
      -1,
      doubleClickShortcut.requiredModifierMask,
      skipIfActionHasShortcut,
    )
    return true
  }

  private fun isShortcutRegistrationSuppressed(@NonNls actionId: String, key: DispatcherKey): Boolean {
    if (!mySuppressedActionIds.contains(actionId) && !mySuppressedShortcuts.contains(key)) {
      return false
    }
    LOG.debug("Skipped modifier double-click registration for '", actionId, "': shortcut is suppressed")
    return true
  }

  /**
   * @param actionId        Id of action to be triggered on modifier+modifier[+actionKey]
   * @param modifierKeyCode keyCode for modifier, e.g. KeyEvent.VK_SHIFT
   * @param actionKeyCode   keyCode for actionKey, or -1 if action should be triggered on bare modifier double click
   */
  fun registerAction(@NonNls actionId: String, modifierKeyCode: Int, actionKeyCode: Int) {
    registerAction(actionId, modifierKeyCode, actionKeyCode, true)
  }

  @Synchronized
  fun unregisterAction(@NonNls actionId: String) {
    myDispatchers.keys.removeIf { key -> key.actionId == actionId }
    myKeymapDispatcherKeys.removeIf { key -> key.actionId == actionId }
  }

  @Internal
  @Synchronized
  fun suppressShortcut(@NonNls actionId: String, shortcut: KeyboardModifierGestureShortcut) {
    val doubleClickShortcut = DoubleClickShortcut.from(shortcut) ?: return
    val key = doubleClickShortcut.toDispatcherKey(actionId)
    mySuppressedShortcuts.add(key)
    myDispatchers.remove(key)
    myKeymapDispatcherKeys.remove(key)
  }

  @Internal
  @Synchronized
  fun suppressAction(@NonNls actionId: String) {
    mySuppressedActionIds.add(actionId)
    unregisterAction(actionId)
  }

  @Internal
  @Synchronized
  fun unsuppressAction(@NonNls actionId: String) {
    mySuppressedActionIds.remove(actionId)
    mySuppressedShortcuts.removeIf { key -> key.actionId == actionId }
  }

  @Internal
  @Synchronized
  fun isActionRegistered(@NonNls actionId: String): Boolean {
    for ((registeredActionId) in myDispatchers.keys) {
      if (registeredActionId == actionId) {
        return true
      }
    }
    return false
  }

  @Internal
  @Synchronized
  fun isShortcutRegistered(@NonNls actionId: String, shortcut: KeyboardModifierGestureShortcut): Boolean {
    val doubleClickShortcut = DoubleClickShortcut.from(shortcut)
    return doubleClickShortcut != null && myDispatchers.containsKey(doubleClickShortcut.toDispatcherKey(actionId))
  }

  fun isRunningAction(): Boolean = myIsRunningAction

  private data class DispatcherKey(
    val actionId: String,
    val modifierKeyCode: Int,
    val actionKeyCode: Int,
    val requiredModifierMask: Int,
  )

  private data class DoubleClickShortcut(
    val modifierKeyCode: Int,
    val requiredModifierMask: Int,
  ) {
    fun toDispatcherKey(actionId: String): DispatcherKey {
      return DispatcherKey(actionId, modifierKeyCode, -1, requiredModifierMask)
    }

    fun matches(key: DispatcherKey): Boolean {
      return key.modifierKeyCode == modifierKeyCode &&
             key.actionKeyCode == -1 &&
             key.requiredModifierMask == requiredModifierMask
    }

    companion object {
      fun from(shortcut: KeyboardModifierGestureShortcut): DoubleClickShortcut? {
        if (shortcut.type != KeyboardGestureAction.ModifierType.dblClick) {
          return null
        }

        val modifierKeyCode = shortcut.stroke.keyCode
        val modifierMask = KEY_CODE_TO_MODIFIER_MAP.get(modifierKeyCode)
        if (modifierMask == 0) {
          return null
        }

        val strokeModifiers = shortcut.stroke.modifiers
        if (hasUnsupportedModifiers(strokeModifiers)) {
          return null
        }

        val shortcutModifiers = normalizeModifiers(strokeModifiers) and SUPPORTED_MODIFIER_MASKS
        if ((shortcutModifiers and modifierMask) == 0) {
          return null
        }

        val requiredModifiers = shortcutModifiers and modifierMask.inv()
        return DoubleClickShortcut(modifierKeyCode, requiredModifiers)
      }
    }
  }

  private inner class MyDispatcher(
    private val myActionId: String,
    private val myModifierKeyCode: Int,
    private val myActionKeyCode: Int,
    private val myRequiredModifierMask: Int,
    private val mySkipIfActionHasShortcut: Boolean,
  ) {
    private val ourPressed = Couple.of(AtomicBoolean(false), AtomicBoolean(false))
    private val ourReleased = Couple.of(AtomicBoolean(false), AtomicBoolean(false))
    private val ourOtherKeyWasPressed = AtomicBoolean(false)
    private val ourLastTimePressed = AtomicLong(0)

    fun dispatch(event: KeyEvent): Boolean {
      val keyCode = event.keyCode
      if (LOG.isTraceEnabled) {
        LOG.trace("$this $event")
      }
      if (keyCode == myModifierKeyCode) {
        if (hasOtherModifiers(event) || !hasRequiredModifier(event)) {
          resetState()
          return false
        }

        if (myActionKeyCode == -1 && ourOtherKeyWasPressed.get() && event.`when` - ourLastTimePressed.get() < 100) {
          resetState()
          return false
        }

        ourOtherKeyWasPressed.set(false)
        if (ourPressed.first.get() && event.`when` - ourLastTimePressed.get() > 500) {
          resetState()
        }

        return handleModifier(event)
      }
      else if (isRequiredModifierKey(keyCode)) {
        if (hasOtherModifiers(event)) {
          resetState()
        }
        return false
      }
      else if (ourPressed.first.get() && ourReleased.first.get() && ourPressed.second.get() && myActionKeyCode != -1) {
        return keyCode == myActionKeyCode &&
               !hasOtherModifiers(event) &&
               hasRequiredModifier(event) &&
               (event.id != KeyEvent.KEY_PRESSED || run(event))
      }
      else {
        ourLastTimePressed.set(event.`when`)
        ourOtherKeyWasPressed.set(true)
        if (keyCode == KeyEvent.VK_ESCAPE || keyCode == KeyEvent.VK_TAB) {
          ourLastTimePressed.set(0)
        }
      }
      resetState()
      return false
    }

    private fun hasOtherModifiers(keyEvent: KeyEvent): Boolean {
      val eventModifiers = getEventModifiers(keyEvent)
      if (hasUnsupportedModifiers(eventModifiers)) {
        return true
      }

      val modifiers = normalizeModifiers(eventModifiers)
      val allowedModifiers = getModifierMask(myModifierKeyCode) or myRequiredModifierMask
      for (entry in KEY_CODE_TO_MODIFIER_MAP.int2IntEntrySet()) {
        if ((modifiers and entry.intValue) != 0 && (allowedModifiers and entry.intValue) == 0) {
          return true
        }
      }
      return false
    }

    private fun isRequiredModifierKey(keyCode: Int): Boolean {
      val modifierMask = getModifierMask(keyCode)
      return modifierMask != 0 && (myRequiredModifierMask and modifierMask) != 0
    }

    private fun hasRequiredModifier(event: KeyEvent): Boolean {
      return myRequiredModifierMask == 0 || (normalizeModifiers(getEventModifiers(event)) and myRequiredModifierMask) == myRequiredModifierMask
    }

    private fun handleModifier(event: KeyEvent): Boolean {
      if (ourPressed.first.get() && event.`when` - ourLastTimePressed.get() > 300) {
        resetState()
        return false
      }

      if (event.id == KeyEvent.KEY_PRESSED) {
        if (!ourPressed.first.get()) {
          resetState()
          ourPressed.first.set(true)
          ourLastTimePressed.set(event.`when`)
          return false
        }
        else {
          if (ourPressed.first.get() && ourReleased.first.get()) {
            ourPressed.second.set(true)
            ourLastTimePressed.set(event.`when`)
            return false
          }
        }
      }
      else if (event.id == KeyEvent.KEY_RELEASED) {
        if (ourPressed.first.get() && !ourReleased.first.get()) {
          ourReleased.first.set(true)
          ourLastTimePressed.set(event.`when`)
          return false
        }
        else if (ourPressed.first.get() && ourReleased.first.get() && ourPressed.second.get()) {
          resetState()
          if (myActionKeyCode == -1 && !shouldSkipIfActionHasShortcut()) {
            if (!ClientId.isCurrentlyUnderLocalId) {
              return false
            }

            run(event)
            return true
          }
          return false
        }
      }
      resetState()
      return false
    }

    fun resetState() {
      ourPressed.first.set(false)
      ourPressed.second.set(false)
      ourReleased.first.set(false)
      ourReleased.second.set(false)
    }

    private fun run(event: KeyEvent): Boolean {
      myIsRunningAction = true
      try {
        val ex = ActionManagerEx.getInstanceEx()
        val action = ex.getAction(myActionId) ?: return false

        if (!action.isEnabledInModalContext) {
          // This check is copied IdeKeyEventDispatcher#dispatchKeyEvent method
          val focusedWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
          if (focusedWindow != null && IdeKeyEventDispatcher.isModalContext(focusedWindow)) {
            return false
          }
        }

        val context = calculateContext()
        val actionEvent = AnActionEvent.createEvent(action, context, null, ActionPlaces.KEYBOARD_SHORTCUT, ActionUiKind.NONE, event)
        val result = ActionUtil.performAction(action, actionEvent)
        return !result.isIgnored
      }
      finally {
        myIsRunningAction = false
      }
    }

    private fun shouldSkipIfActionHasShortcut(): Boolean {
      return mySkipIfActionHasShortcut && KeymapUtil.getActiveKeymapShortcuts(myActionId).hasShortcuts()
    }

    override fun toString(): String {
      return "modifier double-click dispatcher [modifierKeyCode=$myModifierKeyCode" +
             ",actionKeyCode=$myActionKeyCode" +
             ",requiredModifierMask=$myRequiredModifierMask" +
             ",actionId=$myActionId]"
    }
  }

  companion object {
    private val LOG = logger<ModifierKeyDoubleClickHandler>()
    private val KEY_CODE_TO_MODIFIER_MAP: Int2IntMap = Int2IntOpenHashMap().apply {
      put(KeyEvent.VK_ALT, InputEvent.ALT_MASK)
      put(KeyEvent.VK_CONTROL, InputEvent.CTRL_MASK)
      put(KeyEvent.VK_META, InputEvent.META_MASK)
      put(KeyEvent.VK_SHIFT, InputEvent.SHIFT_MASK)
    }

    private const val SUPPORTED_MODIFIER_MASKS =
      InputEvent.SHIFT_MASK or InputEvent.ALT_MASK or InputEvent.CTRL_MASK or InputEvent.META_MASK
    private const val SUPPORTED_MODIFIER_DOWN_MASKS =
      InputEvent.SHIFT_DOWN_MASK or InputEvent.ALT_DOWN_MASK or InputEvent.CTRL_DOWN_MASK or InputEvent.META_DOWN_MASK
    private const val SUPPORTED_MODIFIER_INPUT_MASKS = SUPPORTED_MODIFIER_MASKS or SUPPORTED_MODIFIER_DOWN_MASKS

    @JvmStatic
    fun getInstance(): ModifierKeyDoubleClickHandler = service()

    @Internal
    @JvmStatic
    fun scheduleKeymapShortcutSyncIfCreated() {
      val application = ApplicationManager.getApplication() ?: return
      val handler = application.getServiceIfCreated(ModifierKeyDoubleClickHandler::class.java)
      handler?.scheduleKeymapShortcutSync()
    }

    @JvmStatic
    fun getMultiCaretActionModifier(): Int = if (SystemInfoRt.isMac) KeyEvent.VK_ALT else KeyEvent.VK_CONTROL

    private fun normalizeModifiers(modifiers: Int): Int {
      var normalized = modifiers
      if ((normalized and InputEvent.SHIFT_DOWN_MASK) != 0) normalized = normalized or InputEvent.SHIFT_MASK
      if ((normalized and InputEvent.ALT_DOWN_MASK) != 0) normalized = normalized or InputEvent.ALT_MASK
      if ((normalized and InputEvent.CTRL_DOWN_MASK) != 0) normalized = normalized or InputEvent.CTRL_MASK
      if ((normalized and InputEvent.META_DOWN_MASK) != 0) normalized = normalized or InputEvent.META_MASK
      return normalized
    }

    private fun hasUnsupportedModifiers(modifiers: Int): Boolean {
      return (modifiers and SUPPORTED_MODIFIER_INPUT_MASKS.inv()) != 0
    }

    private fun getEventModifiers(event: KeyEvent): Int {
      return event.modifiers or event.modifiersEx
    }

    private fun getRequiredModifierMask(requiredModifierKeyCode: Int): Int {
      if (requiredModifierKeyCode == -1) {
        return 0
      }
      val modifierMask = getModifierMask(requiredModifierKeyCode)
      if (modifierMask == 0) {
        throw IllegalArgumentException("Unsupported required modifier keyCode: $requiredModifierKeyCode")
      }
      return modifierMask
    }

    private fun getModifierMask(keyCode: Int): Int {
      return KEY_CODE_TO_MODIFIER_MAP.get(keyCode)
    }

    private fun calculateContext(): DataContext {
      val focusManager = IdeFocusManager.findInstance()
      val focusedComponent: Component? = focusManager.focusOwner
      val ideWindow: Window? = focusManager.lastFocusedIdeWindow
      return if (ideWindow === focusedComponent || focusedComponent === focusManager.getLastFocusedFor(ideWindow)) {
        DataManager.getInstance().getDataContext(focusedComponent)
      }
      else {
        DataManager.getInstance().dataContext
      }
    }
  }
}
