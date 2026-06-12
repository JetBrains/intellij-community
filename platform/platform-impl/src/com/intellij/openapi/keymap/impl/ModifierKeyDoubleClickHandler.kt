// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION", "ReplacePutWithAssignment")

package com.intellij.openapi.keymap.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.ide.DataManager
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.KeyboardGestureAction
import com.intellij.openapi.actionSystem.KeyboardModifierGestureShortcut
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.ex.ActionRuntimeRegistrar
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.keymap.impl.ui.ShortcutTextField
import com.intellij.openapi.project.DumbService
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Supplier

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
@Internal
class ModifierKeyDoubleClickHandler {
  private val dispatchers: ConcurrentHashMap<ShortcutDispatcherKey, Dispatcher> = ConcurrentHashMap()
  private val keymapDispatcherKeys: MutableSet<DispatcherKey> = ConcurrentHashMap.newKeySet()
  private val suppressedActionIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
  private val suppressedShortcuts: MutableSet<DispatcherKey> = ConcurrentHashMap.newKeySet()
  private val keymapShortcutSyncScheduled = AtomicBoolean()
  private var runningAction = false

  init {
    val modifierKeyCode = getMultiCaretActionModifier()

    registerAction(IdeActions.ACTION_EDITOR_CLONE_CARET_ABOVE, modifierKeyCode, KeyEvent.VK_UP)
    registerAction(IdeActions.ACTION_EDITOR_CLONE_CARET_BELOW, modifierKeyCode, KeyEvent.VK_DOWN)
    registerAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION, modifierKeyCode, KeyEvent.VK_LEFT)
    registerAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION, modifierKeyCode, KeyEvent.VK_RIGHT)
    registerAction(IdeActions.ACTION_EDITOR_MOVE_LINE_START_WITH_SELECTION, modifierKeyCode, KeyEvent.VK_HOME)
    registerAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END_WITH_SELECTION, modifierKeyCode, KeyEvent.VK_END)
  }

  @Internal
  class MyAnActionListener : AnActionListener {
    override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
      val doubleClickHandler = getInstance()
      if (doubleClickHandler.runningAction) {
        return
      }

      for (dispatcher in doubleClickHandler.dispatchers.values) {
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

      val dispatchers = doubleClickHandler.dispatchers.values
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

  internal class InitialShortcutSync : ActionConfigurationCustomizer, ActionConfigurationCustomizer.AsyncLightCustomizeStrategy {
    override suspend fun customize(actionRegistrar: ActionRuntimeRegistrar) {
      serviceAsync<ModifierKeyDoubleClickHandler>().scheduleKeymapShortcutSync()
    }
  }

  internal class KeymapShortcutChangeListener : KeymapManagerListener {
    override fun activeKeymapChanged(keymap: Keymap?) {
      scheduleKeymapShortcutSyncIfCreated()
    }

    override fun shortcutsChanged(keymap: Keymap, @NonNls actionIds: Collection<String>, fromSettings: Boolean) {
      scheduleKeymapShortcutSyncIfCreated()
    }
  }

  private fun scheduleKeymapShortcutSync() {
    if (!keymapShortcutSyncScheduled.compareAndSet(false, true)) {
      return
    }
    service<CoreUiCoroutineScopeHolder>().coroutineScope.launch {
      keymapShortcutSyncScheduled.set(false)
      syncKeymapShortcuts()
    }
  }

  @Synchronized
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
    val newKeymapDispatcherKeys = LinkedHashSet<DispatcherKey>()
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
    for (key in keymapDispatcherKeys) {
      removeDispatcherRegistration(key)
    }
    keymapDispatcherKeys.clear()
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
    if (suppressedActionIds.contains(actionId) || suppressedShortcuts.contains(key)) {
      return
    }
    keymapDispatcherKeys.remove(key)
    putDispatcherRegistration(key, skipIfActionHasShortcut)
  }

  @Internal
  @Synchronized
  fun registerShortcut(
    @NonNls actionId: String,
    shortcut: KeyboardModifierGestureShortcut,
    skipIfActionHasShortcut: Boolean,
  ): Boolean {
    val doubleClickShortcut = shortcut.toDoubleClickShortcut()
    if (doubleClickShortcut == null) {
      LOG.warn("Cannot register modifier double-click shortcut for action '$actionId': unsupported shortcut $shortcut")
      return false
    }
    return registerDoubleClickShortcut(actionId, doubleClickShortcut, skipIfActionHasShortcut)
  }

  private fun collectKeymapShortcut(
    @NonNls actionId: String,
    shortcut: KeyboardModifierGestureShortcut,
    newKeymapDispatcherKeys: MutableSet<DispatcherKey>,
  ) {
    val doubleClickShortcut = shortcut.toDoubleClickShortcut()
    if (doubleClickShortcut != null) {
      val key = doubleClickShortcut.toDispatcherKey(actionId)
      if (isShortcutRegistrationSuppressed(actionId, key)) {
        return
      }
      newKeymapDispatcherKeys.add(key)
    }
  }

  @Synchronized
  private fun retainKeymapShortcuts(newKeymapDispatcherKeys: Set<DispatcherKey>) {
    // Preserve unchanged dispatchers so an async keymap resync does not reset an in-progress double-click gesture.
    for (key in keymapDispatcherKeys) {
      if (!newKeymapDispatcherKeys.contains(key)) {
        removeDispatcherRegistration(key)
      }
    }
    for (key in newKeymapDispatcherKeys) {
      if (!keymapDispatcherKeys.contains(key)) {
        putDispatcherRegistration(key, false)
      }
    }
    keymapDispatcherKeys.clear()
    keymapDispatcherKeys.addAll(newKeymapDispatcherKeys)
  }

  @Synchronized
  private fun registerDoubleClickShortcut(
    @NonNls actionId: String,
    doubleClickShortcut: DoubleClickShortcut,
    skipIfActionHasShortcut: Boolean,
  ): Boolean {
    val key = doubleClickShortcut.toDispatcherKey(actionId)
    if (isShortcutRegistrationSuppressed(actionId, key)) {
      return false
    }
    keymapDispatcherKeys.remove(key)
    putDispatcherRegistration(key, skipIfActionHasShortcut)
    return true
  }

  private fun putDispatcherRegistration(key: DispatcherKey, skipIfActionHasShortcut: Boolean) {
    dispatchers.computeIfAbsent(key.shortcutKey) { shortcutKey ->
      Dispatcher(shortcutKey.modifierKeyCode, shortcutKey.actionKeyCode, shortcutKey.requiredModifierMask)
    }.addOrReplaceRegistration(key.actionId, skipIfActionHasShortcut)
  }

  private fun removeDispatcherRegistration(key: DispatcherKey) {
    val shortcutKey = key.shortcutKey
    val dispatcher = dispatchers[shortcutKey] ?: return
    dispatcher.removeRegistration(key.actionId)
    if (dispatcher.isEmpty()) {
      dispatchers.remove(shortcutKey, dispatcher)
    }
  }

  private fun isShortcutRegistrationSuppressed(@NonNls actionId: String, key: DispatcherKey): Boolean {
    if (!suppressedActionIds.contains(actionId) && !suppressedShortcuts.contains(key)) {
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
    for ((shortcutKey, dispatcher) in dispatchers) {
      dispatcher.removeRegistration(actionId)
      if (dispatcher.isEmpty()) {
        dispatchers.remove(shortcutKey, dispatcher)
      }
    }
    keymapDispatcherKeys.removeIf { key -> key.actionId == actionId }
  }

  @Internal
  @Synchronized
  fun suppressShortcut(@NonNls actionId: String, shortcut: KeyboardModifierGestureShortcut) {
    val doubleClickShortcut = shortcut.toDoubleClickShortcut() ?: return
    val key = doubleClickShortcut.toDispatcherKey(actionId)
    suppressedShortcuts.add(key)
    removeDispatcherRegistration(key)
    keymapDispatcherKeys.remove(key)
  }

  @Internal
  @Synchronized
  fun suppressAction(@NonNls actionId: String) {
    suppressedActionIds.add(actionId)
    unregisterAction(actionId)
  }

  @Internal
  @Synchronized
  fun unsuppressAction(@NonNls actionId: String) {
    suppressedActionIds.remove(actionId)
    suppressedShortcuts.removeIf { key -> key.actionId == actionId }
  }

  @Internal
  @Synchronized
  fun isActionRegistered(@NonNls actionId: String): Boolean {
    for (dispatcher in dispatchers.values) {
      if (dispatcher.hasRegistration(actionId)) {
        return true
      }
    }
    return false
  }

  @Internal
  @Synchronized
  fun isShortcutRegistered(@NonNls actionId: String, shortcut: KeyboardModifierGestureShortcut): Boolean {
    val doubleClickShortcut = shortcut.toDoubleClickShortcut()
    return doubleClickShortcut != null &&
           dispatchers[doubleClickShortcut.shortcutKey]?.hasRegistration(actionId) == true
  }

  fun isRunningAction(): Boolean = runningAction

  private inner class Dispatcher(
    private val modifierKeyCode: Int,
    private val actionKeyCode: Int,
    private val requiredModifierMask: Int,
  ) {
    private val registrations = CopyOnWriteArrayList<ActionRegistration>()
    private val firstPressed = AtomicBoolean(false)
    private val secondPressed = AtomicBoolean(false)
    private val firstReleased = AtomicBoolean(false)
    private val otherKeyWasPressed = AtomicBoolean(false)
    private val lastTimePressed = AtomicLong(0)

    fun addOrReplaceRegistration(actionId: String, skipIfActionHasShortcut: Boolean) {
      removeRegistration(actionId)
      registrations.add(ActionRegistration(actionId, skipIfActionHasShortcut))
    }

    fun removeRegistration(actionId: String) {
      registrations.removeIf { registration -> registration.actionId == actionId }
    }

    fun hasRegistration(actionId: String): Boolean {
      return registrations.any { registration -> registration.actionId == actionId }
    }

    fun isEmpty(): Boolean = registrations.isEmpty()

    fun dispatch(event: KeyEvent): Boolean {
      val keyCode = event.keyCode
      if (LOG.isTraceEnabled) {
        LOG.trace("$this $event")
      }
      if (keyCode == modifierKeyCode) {
        if (hasOtherModifiers(event) || !hasRequiredModifier(event)) {
          resetState()
          return false
        }

        if (actionKeyCode == -1 && otherKeyWasPressed.get() && event.`when` - lastTimePressed.get() < 100) {
          resetState()
          return false
        }

        otherKeyWasPressed.set(false)
        if (firstPressed.get() && event.`when` - lastTimePressed.get() > 500) {
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
      else if (firstPressed.get() && firstReleased.get() && secondPressed.get() && actionKeyCode != -1) {
        return keyCode == actionKeyCode &&
               !hasOtherModifiers(event) &&
               hasRequiredModifier(event) &&
               (event.id != KeyEvent.KEY_PRESSED || run(event))
      }
      else {
        lastTimePressed.set(event.`when`)
        otherKeyWasPressed.set(true)
        if (keyCode == KeyEvent.VK_ESCAPE || keyCode == KeyEvent.VK_TAB) {
          lastTimePressed.set(0)
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
      val allowedModifiers = getModifierMask(modifierKeyCode) or requiredModifierMask
      for (entry in KEY_CODE_TO_MODIFIER_MAP.int2IntEntrySet()) {
        if ((modifiers and entry.intValue) != 0 && (allowedModifiers and entry.intValue) == 0) {
          return true
        }
      }
      return false
    }

    private fun isRequiredModifierKey(keyCode: Int): Boolean {
      val modifierMask = getModifierMask(keyCode)
      return modifierMask != 0 && (requiredModifierMask and modifierMask) != 0
    }

    private fun hasRequiredModifier(event: KeyEvent): Boolean {
      return requiredModifierMask == 0 || (normalizeModifiers(getEventModifiers(event)) and requiredModifierMask) == requiredModifierMask
    }

    private fun handleModifier(event: KeyEvent): Boolean {
      if (firstPressed.get() && event.`when` - lastTimePressed.get() > 300) {
        resetState()
        return false
      }

      if (event.id == KeyEvent.KEY_PRESSED) {
        if (!firstPressed.get()) {
          resetState()
          firstPressed.set(true)
          lastTimePressed.set(event.`when`)
          return false
        }
        else {
          if (firstPressed.get() && firstReleased.get()) {
            secondPressed.set(true)
            lastTimePressed.set(event.`when`)
            return false
          }
        }
      }
      else if (event.id == KeyEvent.KEY_RELEASED) {
        if (firstPressed.get() && !firstReleased.get()) {
          firstReleased.set(true)
          lastTimePressed.set(event.`when`)
          return false
        }
        else if (firstPressed.get() && firstReleased.get() && secondPressed.get()) {
          resetState()
          if (actionKeyCode == -1) {
            return ClientId.isCurrentlyUnderLocalId && run(event)
          }
          return false
        }
      }
      resetState()
      return false
    }

    fun resetState() {
      firstPressed.set(false)
      secondPressed.set(false)
      firstReleased.set(false)
    }

    private fun run(event: KeyEvent): Boolean {
      val actions = getActions() ?: return false
      val context = calculateContext()
      val chosen = chooseAction(actions, event, context) ?: return false

      runningAction = true
      try {
        val result = ActionUtil.performAction(chosen.action, chosen.event)
        return !result.isIgnored
      }
      finally {
        runningAction = false
      }
    }

    private fun getActions(): List<AnAction>? {
      val actionManager = ActionManagerEx.getInstanceEx()
      val focusedWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
      val isModalContext = focusedWindow != null && IdeKeyEventDispatcher.isModalContext(focusedWindow)
      val actions = ArrayList<AnAction>()
      val orderedRegistrations = registrations.sortedWith(compareBy(actionManager.registrationOrderComparator) { it.actionId })
      for ((actionId, skipIfActionHasShortcut) in orderedRegistrations) {
        if (skipIfActionHasShortcut && KeymapUtil.getActiveKeymapShortcuts(actionId).hasShortcuts()) {
          continue
        }
        val action = actionManager.getAction(actionId) ?: continue
        if (isModalContext && !action.isEnabledInModalContext) {
          continue
        }
        actions.add(action)
      }
      return actions.takeIf { it.isNotEmpty() }
    }

    private fun chooseAction(actions: List<AnAction>, event: KeyEvent, dataContext: DataContext): ActionChoice? {
      val context = Utils.createAsyncDataContext(dataContext)
      val project = CommonDataKeys.PROJECT.getData(context)
      val dumb = project != null && DumbService.getInstance(project).isDumb
      return runInReadActionConditionally(actions) {
        Utils.runUpdateSessionForInputEvent(
          actions,
          event,
          context,
          ActionPlaces.KEYBOARD_SHORTCUT,
          MODIFIER_KEY_ACTION_PROCESSOR,
          PresentationFactory(),
        ) { rearranged, updater, events ->
          for (action in rearranged) {
            val presentation = updater(action)
            if (dumb && !action.isDumbAware) {
              continue
            }
            val actionEvent = events[presentation]
            if (actionEvent != null && presentation.isEnabled) {
              return@runUpdateSessionForInputEvent ActionChoice(action, actionEvent)
            }
          }
          null
        }
      }
    }

    override fun toString(): String {
      return "modifier double-click dispatcher [modifierKeyCode=$modifierKeyCode" +
             ",actionKeyCode=$actionKeyCode" +
             ",requiredModifierMask=$requiredModifierMask" +
             ",actions=${registrations.map { it.actionId }}]"
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): ModifierKeyDoubleClickHandler = service()

    @Internal
    fun scheduleKeymapShortcutSyncIfCreated() {
      val application = ApplicationManager.getApplication() ?: return
      application.getServiceIfCreated(ModifierKeyDoubleClickHandler::class.java)?.scheduleKeymapShortcutSync()
    }

    @JvmStatic
    fun getMultiCaretActionModifier(): Int = if (SystemInfoRt.isMac) KeyEvent.VK_ALT else KeyEvent.VK_CONTROL
  }
}

private val LOG = logger<ModifierKeyDoubleClickHandler>()

private val MODIFIER_KEY_ACTION_PROCESSOR = object : ActionProcessor() {
  override fun createEvent(
    inputEvent: InputEvent,
    context: DataContext,
    place: String,
    presentation: Presentation,
    manager: ActionManager,
  ): AnActionEvent {
    return AnActionEvent(inputEvent, context, place, presentation, manager, 0)
  }
}

private fun <T> runInReadActionConditionally(actions: List<AnAction>, supplier: Supplier<T>): T {
  return if (actions.any(Utils::isLockRequired)) {
    ReadAction.computeBlocking<T, Throwable>(supplier::get)
  }
  else {
    supplier.get()
  }
}

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

private data class DispatcherKey(
  val actionId: String,
  val modifierKeyCode: Int,
  val actionKeyCode: Int,
  val requiredModifierMask: Int,
) {
  val shortcutKey: ShortcutDispatcherKey
    get() = ShortcutDispatcherKey(modifierKeyCode, actionKeyCode, requiredModifierMask)
}

private data class ShortcutDispatcherKey(
  val modifierKeyCode: Int,
  val actionKeyCode: Int,
  val requiredModifierMask: Int,
)

private data class ActionRegistration(
  val actionId: String,
  val skipIfActionHasShortcut: Boolean,
)

private data class ActionChoice(
  val action: AnAction,
  val event: AnActionEvent,
)

private data class DoubleClickShortcut(
  val modifierKeyCode: Int,
  val requiredModifierMask: Int,
) {
  val shortcutKey: ShortcutDispatcherKey
    get() = ShortcutDispatcherKey(modifierKeyCode, -1, requiredModifierMask)

  fun toDispatcherKey(actionId: String): DispatcherKey {
    return DispatcherKey(actionId, modifierKeyCode, -1, requiredModifierMask)
  }
}

private fun KeyboardModifierGestureShortcut.toDoubleClickShortcut(): DoubleClickShortcut? {
  if (this.type != KeyboardGestureAction.ModifierType.dblClick) {
    return null
  }

  val modifierKeyCode = this.stroke.keyCode
  val modifierMask = KEY_CODE_TO_MODIFIER_MAP.get(modifierKeyCode)
  if (modifierMask == 0) {
    return null
  }

  val strokeModifiers = this.stroke.modifiers
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
