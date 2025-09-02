// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.openapi.keymap.impl

import com.intellij.diagnostic.EventWatcher
import com.intellij.diagnostic.LoadingState
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.KeyboardAwareFocusOwner
import com.intellij.openapi.MnemonicHelper
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.TransactionGuardImpl
import com.intellij.openapi.client.ClientSystemInfo
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.keymap.KeyMapBundle
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.keymap.impl.keyGestures.KeyboardGestureProcessor
import com.intellij.openapi.keymap.impl.ui.ShortcutTextField
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.StatusBar.Info.set
import com.intellij.openapi.wm.impl.FloatingDecorator
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.openapi.wm.impl.IdeGlassPaneEx
import com.intellij.ui.ComponentUtil
import com.intellij.ui.ComponentWithMnemonics
import com.intellij.ui.KeyStrokeAdapter
import com.intellij.ui.speedSearch.SpeedSearchSupply
import com.intellij.util.ReflectionUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.text.matching.KeyboardLayoutUtil
import com.intellij.util.ui.MacUIUtil
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.AWTKeyStroke
import java.awt.Component
import java.awt.Container
import java.awt.KeyboardFocusManager
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.*
import javax.swing.*
import javax.swing.plaf.basic.ComboPopup
import javax.swing.text.JTextComponent

private val LOG = logger<IdeKeyEventDispatcher>()

/**
 * This class is automaton with a finite number of states.
 */
@OptIn(DelicateCoroutinesApi::class)
class IdeKeyEventDispatcher(private val queue: IdeEventQueue?) {
  private var firstKeyStroke: KeyStroke? = null

  /**
   * When we "dispatch" key event via keymap, i.e., when registered action has been executed
   * instead of event dispatching, then we have to consume all the following KEY_RELEASED and
   * KEY_TYPED event because they are not valid.
   */
  var isPressedWasProcessed: Boolean = false
    private set

  private var ignoreNextKeyTypedEvent = false
  private var keyState = KeyState.STATE_INIT
  private val presentationFactory = PresentationFactory()
  private var leftCtrlPressed = false
  private var rightAltPressed = false
  private val keyGestureProcessor = KeyboardGestureProcessor(this)

  var state: KeyState
    @ApiStatus.Internal 
    get() = keyState
    @ApiStatus.Internal
    set(state) {
      keyState = state
      queue?.maybeReady()
    }

  private fun resetState() {
    state = KeyState.STATE_INIT
    isPressedWasProcessed = false
  }

  val isReady: Boolean
    get() = keyState == KeyState.STATE_INIT || keyState == KeyState.STATE_PROCESSED

  @get:ApiStatus.Internal
  val context: KeyProcessorContext = KeyProcessorContext()

  private val secondStrokeTimeout = MutableSharedFlow<Unit>(replay=1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  val isWaitingForSecondKeyStroke: Boolean
    get() = state == KeyState.STATE_WAIT_FOR_SECOND_KEYSTROKE || isPressedWasProcessed

  init {
    GlobalScope.launch {
      secondStrokeTimeout
        .collectLatest {
          delay(Registry.intValue("actionSystem.secondKeystrokeTimeout", 2_000).toLong())
          if (keyState == KeyState.STATE_WAIT_FOR_SECOND_KEYSTROKE) {
            resetState()
            set(text = null, project = context.project)
          }
        }
    }
  }

  companion object {
    /**
     * @return `true` if and only if the `component` represents modal context.
     * @throws IllegalArgumentException if `component` is `null`.
     */
    @JvmStatic
    fun isModalContext(component: Component): Boolean = isModalContextOrNull(component) ?: true

    /**
     * Check whether the `component` represents a modal context.
     * @return `null` if it's impossible to deduce.
     */
    @ApiStatus.Internal
    @JvmStatic
    fun isModalContextOrNull(component: Component): Boolean? {
      val window = ComponentUtil.getWindow(component) ?: return null
      if (window is IdeFrameImpl) {
        val pane = window.rootPane?.glassPane
        if (pane is IdeGlassPaneEx) {
          return pane.isInModalContext
        }
      }

      if (window is JDialog) {
        if (!window.isModal) {
          val owner = window.getOwner()
          return owner != null && isModalContext(owner)
        }
      }
      if (window is JFrame) {
        return false
      }

      val isFloatingDecorator = window is FloatingDecorator
      val isPopup = component !is JFrame && component !is JDialog
      if (isPopup && component is JWindow) {
        val popup = component.rootPane.getClientProperty(JBPopup.KEY) as? JBPopup
        if (popup != null) {
          return popup.isModalContext
        }
      }
      return !isFloatingDecorator
    }

    @JvmStatic
    fun hasMnemonicInWindow(focusOwner: Component?, event: KeyEvent): Boolean {
      return (KeyEvent.KEY_TYPED == event.id && hasMnemonicInWindow(focusOwner, event.keyChar.code)) ||
             (KeyEvent.KEY_PRESSED == event.id && hasMnemonicInWindow(focusOwner, event.keyCode))
    }

    fun removeAltGraph(e: InputEvent): Boolean {
      if (e.isAltGraphDown) {
        try {
          val field = InputEvent::class.java.getDeclaredField("modifiers")
          field.isAccessible = true
          @Suppress("DEPRECATION")
          field.setInt(e, InputEvent.ALT_GRAPH_MASK.inv() and InputEvent.ALT_GRAPH_DOWN_MASK.inv() and field.getInt(e))
          return true
        }
        catch (ignored: Exception) {
        }
      }
      return false
    }

    fun isAltGrLayout(component: Component?): Boolean {
      val locale = component?.inputContext?.locale ?: return false
      val language = locale.language
      val contains = if (language == "en") ALT_GR_COUNTRIES.contains(locale.country) else ALT_GR_LANGUAGES.contains(language)
      LOG.debug("AltGr", if (contains) "" else " not", " supported for ", locale)
      return contains
    }
  }

  /**
   * @return `true` if and only if the passed event is already dispatched by the
   * `IdeKeyEventDispatcher` and there is no need for any other processing of the event.
   */
  fun dispatchKeyEvent(e: KeyEvent): Boolean {
    if (e.id == KeyEvent.KEY_PRESSED) {
      storeAsciiForChar(e)
    }
    if (e.isConsumed) {
      return false
    }

    val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    val focusOwner = focusManager.focusOwner
    if (focusOwner is KeyboardAwareFocusOwner && (focusOwner as KeyboardAwareFocusOwner).skipKeyEventDispatcher(e)) {
      LOG.debug { "Key event not processed because ${focusOwner} is in focus and implements ${KeyboardAwareFocusOwner::class.java}" }
      return false
    }

    val id = e.id
    if (ignoreNextKeyTypedEvent) {
      if (KeyEvent.KEY_TYPED == id) {
        return true
      }
      ignoreNextKeyTypedEvent = false
    }

    // IDEA-35760
    if (e.keyCode == KeyEvent.VK_CONTROL) {
      if (id == KeyEvent.KEY_PRESSED) {
        leftCtrlPressed = e.keyLocation == KeyEvent.KEY_LOCATION_LEFT
      }
      else if (id == KeyEvent.KEY_RELEASED) {
        leftCtrlPressed = false
      }
    }
    else if (e.keyCode == KeyEvent.VK_ALT) {
      if (id == KeyEvent.KEY_PRESSED) {
        rightAltPressed = e.keyLocation == KeyEvent.KEY_LOCATION_RIGHT
      }
      else if (id == KeyEvent.KEY_RELEASED) {
        rightAltPressed = false
      }
    }

    // shortcuts should not work in shortcut setup fields
    if (focusOwner is ShortcutTextField) {
      // remove AltGr modifier to show a shortcut without AltGr in Settings
      if (SystemInfoRt.isWindows && KeyEvent.KEY_PRESSED == id) {
        removeAltGraph(e)
      }
      return false
    }

    if (state == KeyState.STATE_INIT && e.keyChar != KeyEvent.CHAR_UNDEFINED && e.modifiersEx == 0 &&
        (e.keyCode == KeyEvent.VK_BACK_SPACE || e.keyCode == KeyEvent.VK_SPACE || Character.isLetterOrDigit(e.keyChar))) {
      val supply = if (focusOwner is JComponent) SpeedSearchSupply.getSupply(focusOwner) else null
      if (supply != null) {
        return false
      }
    }

    if (state == KeyState.STATE_INIT && e.keyChar != KeyEvent.CHAR_UNDEFINED && focusOwner is JTextComponent && focusOwner.isEditable) {
      if (id == KeyEvent.KEY_PRESSED && e.keyCode != KeyEvent.VK_ESCAPE) {
        MacUIUtil.hideCursor()
      }
      if (e.modifiersEx == 0 && Character.isLetterOrDigit(e.keyChar) &&
          focusOwner.getClientProperty(ActionUtil.ALLOW_PlAIN_LETTER_SHORTCUTS) != true) {
        return false
      }
    }

    val menuSelectionManager = MenuSelectionManager.defaultManager()
    val selectedPath = menuSelectionManager.selectedPath
    if (selectedPath.isNotEmpty()) {
      if (selectedPath[0] !is ComboPopup) {
        // The following lines of code are a PATCH!!!
        // It is needed to ignore ENTER KEY_TYPED events, which sometimes can reach the editor when an action
        // is invoked from the main menu via an Enter key.
        state = KeyState.STATE_PROCESSED
        return processMenuActions(e, selectedPath[0])
      }
    }

    // Keymap shortcuts (i.e., not local shortcuts) should work only in:
    // - main frame
    // - floating focusedWindow
    // - when there's an editor in contexts
    val focusedWindow = focusManager.focusedWindow
    val isModalContext = focusedWindow != null && isModalContext(focusedWindow)
    @Suppress("DEPRECATION")
    val dataContext = (ApplicationManager.getApplication()?.serviceIfCreated<DataManager>() ?: return false).dataContext
    context.dataContext = dataContext
    context.focusOwner = focusOwner
    context.isModalContext = isModalContext
    context.inputEvent = e
    context.project = CommonDataKeys.PROJECT.getData(dataContext)
    try {
      return when (state) {
        KeyState.STATE_INIT -> inInitState()
        KeyState.STATE_PROCESSED -> inProcessedState()
        KeyState.STATE_WAIT_FOR_SECOND_KEYSTROKE -> inWaitForSecondStrokeState()
        KeyState.STATE_SECOND_STROKE_IN_PROGRESS -> inSecondStrokeInProgressState()
        KeyState.STATE_KEY_GESTURE_PROCESSOR -> keyGestureProcessor.process()
        KeyState.STATE_WAIT_FOR_POSSIBLE_ALT_GR -> inWaitForPossibleAltGr()
      }
    }
    finally {
      context.clear()
    }
  }

  private fun inWaitForSecondStrokeState(): Boolean {
    // a key pressed means that the user starts to enter the second stroke...
    if (KeyEvent.KEY_PRESSED == context.inputEvent.id) {
      state = KeyState.STATE_SECOND_STROKE_IN_PROGRESS
      return inSecondStrokeInProgressState()
    }

    // it looks like RELEASEEs (from the first stroke) go here... skip them
    return true
  }

  private fun inWaitForPossibleAltGr(): Boolean {
    val e = context.inputEvent
    val keyStroke = firstKeyStroke
    firstKeyStroke = null
    state = KeyState.STATE_INIT

    // processing altGr
    val eventId = e.id
    return when {
      KeyEvent.KEY_TYPED == eventId && e.isAltGraphDown -> false
      KeyEvent.KEY_RELEASED == eventId -> {
        updateCurrentContext(context.foundComponent, KeyboardShortcut(keyStroke!!, null))
        if (context.actions.isEmpty()) false else processActionOrWaitSecondStroke(keyStroke)
      }
      else -> false
    }
  }

  private fun inSecondStrokeInProgressState(): Boolean {
    val e = context.inputEvent

    // when any key is released, we stop waiting for the second stroke
    if (KeyEvent.KEY_RELEASED == e.id) {
      firstKeyStroke = null
      state = KeyState.STATE_INIT
      val project = context.project
      set(null, project)
      return false
    }

    val originalKeyStroke = KeyStrokeAdapter.getDefaultKeyStroke(e) ?: return false
    val keyStroke = getKeyStrokeWithoutMouseModifiers(originalKeyStroke)
    updateCurrentContext(context.foundComponent, KeyboardShortcut(firstKeyStroke!!, keyStroke))

    // consume the wrong second stroke and keep on waiting
    return when {
      context.actions.isEmpty() -> true
      processAction(e, actionProcessor) -> {
        set(text = null, project = context.project)
        true
      }
      else -> false
    }
  }

  private fun inProcessedState(): Boolean {
    val e = context.inputEvent
    // ignore typed events which come after processed pressed event
    return when {
      KeyEvent.KEY_TYPED == e.id && isPressedWasProcessed -> true
      //see IDEADEV-8615
      KeyEvent.KEY_RELEASED == e.id && KeyEvent.VK_ALT == e.keyCode && isPressedWasProcessed -> true
      else -> {
        state = KeyState.STATE_INIT
        isPressedWasProcessed = false
        inInitState()
      }
    }
  }

  private fun inInitState(): Boolean {
    val focusOwner = context.focusOwner
    val e = context.inputEvent
    if (SystemInfoRt.isWindows && KeyEvent.KEY_PRESSED == e.id && removeAltGraph(e) && e.isControlDown) {
      firstKeyStroke = KeyStrokeAdapter.getDefaultKeyStroke(e)
      if (firstKeyStroke == null) {
        return false
      }

      state = KeyState.STATE_WAIT_FOR_POSSIBLE_ALT_GR
      return true
    }

    // http://www.jetbrains.net/jira/browse/IDEADEV-12372 (a.k.a. IDEA-35760)
    @Suppress("DEPRECATION")
    val isCandidateForAltGr = leftCtrlPressed &&
                              rightAltPressed && focusOwner != null &&
                              (e.modifiers and InputEvent.SHIFT_MASK.inv() == InputEvent.CTRL_MASK or InputEvent.ALT_MASK)
    if (isCandidateForAltGr) {
      if (Registry.`is`("actionSystem.force.alt.gr", false)) {
        return false
      }
      if (isAltGrLayout(focusOwner)) {
        // don't search for shortcuts
        return false
      }
    }

    val keyStroke = getKeyStrokeWithoutMouseModifiers(originalKeyStroke = KeyStrokeAdapter.getDefaultKeyStroke(e) ?: return false)
    if (keyGestureProcessor.processInitState()) {
      return true
    }

    if (InputEvent.ALT_DOWN_MASK == e.modifiersEx && (!SystemInfoRt.isMac || Registry.`is`("ide.mac.alt.mnemonic.without.ctrl", true))) {
      // the ignoreNextKeyTypedEvent changes event processing to support Alt-based mnemonics
      if ((KeyEvent.KEY_TYPED == e.id && !IdeEventQueue.getInstance().isInputMethodEnabled) || hasMnemonicInWindow(focusOwner, e)) {
        ignoreNextKeyTypedEvent = true
        return false
      }
    }

    updateCurrentContext(component = focusOwner, shortcut = KeyboardShortcut(keyStroke, null))
    if (context.actions.isEmpty()) {
      // there's nothing mapped for this stroke
      return false
    }

    // workaround for IDEA-177327
    if (isCandidateForAltGr && SystemInfoRt.isWindows && Registry.`is`("actionSystem.fix.alt.gr", true)) {
      firstKeyStroke = keyStroke
      state = KeyState.STATE_WAIT_FOR_POSSIBLE_ALT_GR
      return true
    }

    return processActionOrWaitSecondStroke(keyStroke) || keyStroke == F10
  }

  private fun processActionOrWaitSecondStroke(keyStroke: KeyStroke?): Boolean {
    if (!context.secondStrokeActions.isEmpty()) {
      firstKeyStroke = keyStroke
    }
    return processAction(event = context.inputEvent, processor = actionProcessor)
  }

  private fun waitSecondStroke(chosenAction: AnAction, presentation: Presentation) {
    set(text = getSecondStrokeMessage(chosenAction, presentation), project = context.project)
    check(secondStrokeTimeout.tryEmit(Unit))
    state = KeyState.STATE_WAIT_FOR_SECOND_KEYSTROKE
  }

  private fun getSecondStrokeMessage(chosenAction: AnAction, presentation: Presentation): @NlsContexts.StatusBarText String {
    val message: @NlsContexts.StatusBarText StringBuilder = StringBuilder()
    message.append(KeyMapBundle.message("prefix.key.pressed.message"))
    message.append(": ")
    message.append(presentation.text)
    message.append(" (")
    message.append(KeymapUtil.getKeystrokeText(getSecondKeystroke(chosenAction, firstKeyStroke!!)))
    message.append(")")
    for (action in context.secondStrokeActions) {
      if (action === chosenAction) {
        continue
      }
      message.append(", ")
      message.append(action.templatePresentation.text)
      message.append(" (")
      message.append(KeymapUtil.getKeystrokeText(getSecondKeystroke(action, firstKeyStroke!!)))
      message.append(")")
    }
    @Suppress("HardCodedStringLiteral")
    return message.toString()
  }

  private val actionProcessor: ActionProcessor = object : ActionProcessor() {
    override fun createEvent(inputEvent: InputEvent,
                             context: DataContext,
                             place: String,
                             presentation: Presentation,
                             manager: ActionManager): AnActionEvent {
      // Mouse modifiers are 0 because they have no any sense when action is invoked via keyboard
      return AnActionEvent(inputEvent, context, place, presentation, manager, 0)
    }

    override fun onUpdatePassed(inputEvent: InputEvent, action: AnAction, event: AnActionEvent) {
      state = KeyState.STATE_PROCESSED
      isPressedWasProcessed = inputEvent.id == KeyEvent.KEY_PRESSED
    }

    override fun performAction(inputEvent: InputEvent, action: AnAction, event: AnActionEvent) {
      try {
        super.performAction(inputEvent, action, event)
      }
      finally {
        if (Registry.`is`("actionSystem.fixLostTyping", true)) {
          IdeEventQueue.getInstance().doWhenReady {
            IdeEventQueue.getInstance().keyEventDispatcher.resetState()
          }
        }
      }
    }
  }

  fun processAction(event: InputEvent, processor: ActionProcessor): Boolean {
    return processAction(e = event,
                         place = ActionPlaces.KEYBOARD_SHORTCUT,
                         context = context.dataContext,
                         actions = context.actions.toList(),
                         processor = processor,
                         presentationFactory = presentationFactory,
                         shortcut = context.shortcut)
  }

  @JvmName("processAction")
  internal fun processAction(e: InputEvent,
                             place: String,
                             context: DataContext,
                             actions: List<AnAction>,
                             processor: ActionProcessor,
                             presentationFactory: PresentationFactory,
                             shortcut: Shortcut): Boolean {
    if (actions.isEmpty()) {
      return false
    }
    LOG.trace { "processAction(shortcut=$shortcut, actions=$actions)" }
    val wrappedContext = Utils.createAsyncDataContext(context)
    val project = CommonDataKeys.PROJECT.getData(wrappedContext)
    val dumb = project != null && DumbService.getInstance(project).isDumb
    val wouldBeEnabledIfNotDumb = ContainerUtil.createLockFreeCopyOnWriteList<AnAction>()

    fireBeforeShortcutTriggered(shortcut, actions, context)

    val chosen = Utils.runUpdateSessionForInputEvent(
      actions, e, wrappedContext, place, processor, presentationFactory
    ) { rearranged, updater, events ->
      doUpdateActionsInner(rearranged, updater, events, dumb, wouldBeEnabledIfNotDumb)
    }
    val doPerform = chosen != null && !this@IdeKeyEventDispatcher.context.secondStrokeActions.contains(chosen.action)

    LOG.trace { "updateResult: chosen=$chosen, doPerform=$doPerform" }
    val hasSecondStroke = chosen != null && this.context.secondStrokeActions.contains(chosen.action)
    if (e.id == KeyEvent.KEY_PRESSED && !hasSecondStroke && (chosen != null || !wouldBeEnabledIfNotDumb.isEmpty())) {
      ignoreNextKeyTypedEvent = true
    }

    if (doPerform) {
      doPerformActionInner(e, processor, chosen.action, chosen.event)
      logTimeMillis(chosen.startedAt, chosen.action)
    }
    else if (hasSecondStroke) {
      waitSecondStroke(chosen.action, chosen.event.presentation)
    }
    else if (!wouldBeEnabledIfNotDumb.isEmpty()) {
      val actionManager = ActionManager.getInstance()
      showDumbModeBalloonLater(project = project,
                               message = getActionUnavailableMessage(wouldBeEnabledIfNotDumb),
                               expired = { e.isConsumed },
                               actionIds = actions.mapNotNull { action -> actionManager.getId(action) }) {
        // invokeLater to make sure correct dataContext is taken from focus
        ApplicationManager.getApplication().invokeLater {
          DataManager.getInstance().dataContextFromFocusAsync.onSuccess { dataContext ->
            processAction(e, place, dataContext, actions, processor, presentationFactory, shortcut)
          }
        }
      }
    }
    return chosen != null
  }

  /**
   * This method fills `myActions` list.
   */
  private var lastKeyEventForCurrentContext: KeyEvent? = null

  fun updateCurrentContext(component: Component?, shortcut: Shortcut) {
    @Suppress("NAME_SHADOWING")
    var component = component
    context.foundComponent = null
    context.secondStrokeActions.clear()
    context.actions.clear()
    context.shortcut = null
    if (Registry.`is`("ide.edt.update.context.only.on.key.pressed.event")) {
      val keyEvent = context.inputEvent
      if (keyEvent == null || keyEvent.id != KeyEvent.KEY_PRESSED || keyEvent === lastKeyEventForCurrentContext) {
        return
      }

      lastKeyEventForCurrentContext = keyEvent
    }
    if (isControlEnterOnDialog(component, shortcut)) {
      return
    }

    // here we try to find "local" shortcuts
    while (component != null) {
      if (component !is JComponent) {
        component = component.parent
        continue
      }

      val listOfActions = ActionUtil.getActions(component)
      if (listOfActions.isEmpty()) {
        component = component.getParent()
        continue
      }

      for (action in listOfActions) {
        addAction(action, shortcut)
      }
      // once we've found a proper local shortcut(s), we continue with non-local shortcuts
      if (!context.actions.isEmpty()) {
        context.foundComponent = component
        break
      }
      component = component.getParent()
    }

    if (LoadingState.COMPONENTS_LOADED.isOccurred) {
      addActionsFromActiveKeymap(shortcut)
      if (context.secondStrokeActions.isEmpty() && shortcut is KeyboardShortcut) {
        // little trick to invoke action which second stroke is a key w/o modifiers, but the user still
        // holds the modifier key(s) of the first stroke
        val firstKeyStroke = shortcut.firstKeyStroke
        val secondKeyStroke = shortcut.secondKeyStroke
        if (secondKeyStroke != null && secondKeyStroke.modifiers != 0 && firstKeyStroke.modifiers != 0) {
          val altShortCut = KeyboardShortcut(firstKeyStroke, KeyStroke.getKeyStroke(secondKeyStroke.keyCode, 0))
          addActionsFromActiveKeymap(altShortCut)
        }
      }
    }
  }

  private fun addActionsFromActiveKeymap(shortcut: Shortcut) {
    val app = ApplicationManager.getApplication()
    val keymapManager = KeymapManager.getInstance()
    val keymap = keymapManager?.activeKeymap
    val actionIds = keymap?.getActionIdList(shortcut)?.takeIf { it.isNotEmpty() } ?: return
    val actionManager = app.getServiceIfCreated(ActionManager::class.java) ?: return
    for (actionId in actionIds) {
      val action = actionManager.getAction(actionId)
      if (action != null && (!context.isModalContext || action.isEnabledInModalContext)) {
        addAction(action, shortcut)
      }
    }

    if (shortcut is KeyboardShortcut) {
      // A user-pressed keystroke and keymap has some actions assigned to sc (actions going to be executed).
      // Check whether this shortcut conflicts with system-wide shortcuts and notify user if necessary.
      // See IDEA-173174 Warn user about IDE keymap conflicts with native OS keymap.
      SystemShortcuts.getInstance().onUserPressedShortcut(keymap, actionIds, shortcut)
    }
  }

  private fun addAction(action: AnAction, shortcut: Shortcut) {
    if (action is EmptyAction) {
      return
    }

    val isNotTwoStroke = shortcut is KeyboardShortcut && shortcut.secondKeyStroke == null
    for (each in action.shortcutSet.shortcuts) {
      if (each == null) {
        throw NullPointerException("unexpected shortcut of action: $action")
      }
      if (!each.isKeyboard) {
        continue
      }

      if (each.startsWith(shortcut)) {
        if (each is KeyboardShortcut && each.secondKeyStroke != null && isNotTwoStroke && !context.secondStrokeActions.contains(action)) {
          context.secondStrokeActions.add(action)
        }
        if (!context.actions.contains(action)) {
          context.actions.add(action)
          context.shortcut = shortcut
        }
      }
    }
  }
}

private val F10 by lazy(LazyThreadSafetyMode.NONE) {
  KeyStroke.getKeyStroke(KeyEvent.VK_F10, 0)
}

private fun storeAsciiForChar(e: KeyEvent) {
  val aChar = e.keyChar
  if (aChar == KeyEvent.CHAR_UNDEFINED) {
    return
  }

  @Suppress("DEPRECATION")
  val modifiers = e.modifiers
  @Suppress("DEPRECATION")
  if (modifiers and InputEvent.SHIFT_MASK.inv() and InputEvent.SHIFT_DOWN_MASK.inv() != 0) {
    return
  }
  KeyboardLayoutUtil.storeAsciiForChar(e.keyCode, aChar, KeyEvent.VK_A, KeyEvent.VK_Z)
}

@Suppress("DEPRECATION")
private val cachedStroke by lazy {
  ReflectionUtil.getDeclaredMethod(AWTKeyStroke::class.java, "getCachedStroke", Char::class.javaPrimitiveType,
                                   Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                                   Boolean::class.javaPrimitiveType)!!

}

/**
 * This is hack. AWT doesn't allow creating KeyStroke with specified key code and key char simultaneously.
 * Therefore, we're using reflection.
 */
private fun getKeyStrokeWithoutMouseModifiers(originalKeyStroke: KeyStroke): KeyStroke {
  @Suppress("DEPRECATION")
  val modifier = originalKeyStroke.modifiers and InputEvent.BUTTON1_DOWN_MASK.inv() and InputEvent.BUTTON1_MASK.inv() and
    InputEvent.BUTTON2_DOWN_MASK.inv() and InputEvent.BUTTON2_MASK.inv() and
    InputEvent.BUTTON3_DOWN_MASK.inv() and InputEvent.BUTTON3_MASK.inv()
  try {
    return cachedStroke.invoke(originalKeyStroke,
                               originalKeyStroke.keyChar, originalKeyStroke.keyCode,
                               modifier, originalKeyStroke.isOnKeyRelease) as KeyStroke
  }
  catch (e: Exception) {
    throw IllegalStateException(e.message)
  }
}

private fun getSecondKeystroke(action: AnAction, firstKeyStroke: KeyStroke): KeyStroke? {
  val shortcuts = action.shortcutSet.shortcuts
  for (shortcut in shortcuts) {
    if (shortcut is KeyboardShortcut) {
      if (firstKeyStroke == shortcut.firstKeyStroke) {
        shortcut.secondKeyStroke?.let {
          return it
        }
      }
    }
  }
  return null
}

private fun hasMnemonicInWindow(focusOwner: Component?, keyCode: Int): Boolean {
  if (keyCode == KeyEvent.VK_ALT || keyCode == 0) {
    // optimization
    return false
  }

  var container: Container? = if (focusOwner == null) null else ComponentUtil.getWindow(focusOwner)
  if (container is JFrame) {
    val componentWithMnemonics = ComponentUtil.getParentOfType(ComponentWithMnemonics::class.java, focusOwner)
    if (componentWithMnemonics is Container) {
      container = componentWithMnemonics
    }
  }
  return hasMnemonic(container, keyCode) || hasMnemonicInBalloons(container, keyCode)
}

private fun hasMnemonic(container: Container?, keyCode: Int): Boolean {
  val component = UIUtil.uiTraverser(container)
    .traverse()
    .filter { it.isEnabled && it.isShowing }
    .find { it !is ActionMenu && MnemonicHelper.hasMnemonic(it, keyCode) }
  return component != null
}

private fun hasMnemonicInBalloons(container: Container?, code: Int): Boolean {
  val parent = UIUtil.findUltimateParent(container)
  if (parent is RootPaneContainer) {
    val pane = (parent as RootPaneContainer).layeredPane
    for (component in pane.components) {
      if (component is ComponentWithMnemonics && component is Container && hasMnemonic(component as Container, code)) {
        return true
      }
    }
  }
  return false
}

@ApiStatus.Internal
data class UpdateResult(@JvmField val action: AnAction, @JvmField val event: AnActionEvent, @JvmField val startedAt: Long)

private suspend fun doUpdateActionsInner(actions: List<AnAction>,
                                         updater: suspend (AnAction) -> Presentation,
                                         events: Map<Presentation, AnActionEvent>,
                                         dumb: Boolean,
                                         wouldBeEnabledIfNotDumb: MutableList<in AnAction>): UpdateResult? {
  for (action in actions) {
    val startedAt = System.currentTimeMillis()
    val presentation = updater(action)
    if (dumb && !action.isDumbAware) {
      if (presentation.getClientProperty(ActionUtil.WOULD_BE_ENABLED_IF_NOT_DUMB_MODE) != false) {
        wouldBeEnabledIfNotDumb.add(action)
      }
      logTimeMillis(startedAt, action)
      continue
    }

    val event = events[presentation]
    if (event == null || !presentation.isEnabled) {
      logTimeMillis(startedAt, action)
      continue
    }
    return UpdateResult(action, event, startedAt)
  }
  return null
}

private fun doPerformActionInner(e: InputEvent,
                                 processor: ActionProcessor,
                                 action: AnAction,
                                 actionEvent: AnActionEvent) {
  processor.onUpdatePassed(e, action, actionEvent)
  val eventCount = IdeEventQueue.getInstance().eventCount
  val actionManager = actionEvent.actionManager as ActionManagerEx
  actionManager.performWithActionCallbacks(action, actionEvent) {
    LOG.assertTrue(eventCount == IdeEventQueue.getInstance().eventCount, "Event counts do not match: $eventCount != ${IdeEventQueue.getInstance().eventCount}")
    (TransactionGuard.getInstance() as TransactionGuardImpl).performUserActivity {
      processor.performAction(e, action, actionEvent)
    }
  }
}

private fun showDumbModeBalloonLater(project: Project?,
                                     message: @Nls String,
                                     expired: Condition<Any?>,
                                     actionIds: List<String>,
                                     retryRunnable: Runnable) {
  if (project == null || expired.value(null)) {
    return
  }

  ApplicationManager.getApplication().invokeLater({
                                                    if (expired.value(null)) {
                                                      return@invokeLater
                                                    }
                                                    DumbService.getInstance(project).showDumbModeActionBalloon(message, retryRunnable,
                                                                                                               actionIds)
                                                  }, Conditions.or(expired, project.disposed))
}

private fun getActionUnavailableMessage(actions: List<AnAction>): @Nls String {
  val actionNames = actions.asSequence().mapNotNull { action -> action.templateText?.takeIf { it.isNotEmpty() } }.distinct().toList()
  return when {
    actionNames.isEmpty() -> {
      IdeBundle.message("dumb.balloon.this.action.is.not.available.during.indexing")
    }
    actionNames.size == 1 -> {
      IdeBundle.message("dumb.balloon.0.is.not.available.while.indexing", actionNames[0])
    }
    else -> {
      val join: @NlsSafe String = actionNames.joinToString(separator = ", ")
      IdeBundle.message("dumb.balloon.none.of.the.following.actions.are.available.during.indexing.0", join)
    }
  }
}

private fun fireBeforeShortcutTriggered(shortcut: Shortcut, actions: List<AnAction>, context: DataContext) {
  try {
    ApplicationManager.getApplication().messageBus.syncPublisher(AnActionListener.TOPIC)
      .beforeShortcutTriggered(shortcut, Collections.unmodifiableList(actions), context)
  }
  catch (e: Exception) {
    LOG.error(e)
  }
}

private val CONTROL_ENTER = KeyboardShortcut.fromString("control ENTER")

private val CMD_ENTER = KeyboardShortcut.fromString("meta ENTER")

private fun isControlEnterOnDialog(component: Component?, sc: Shortcut): Boolean {
  return ((CONTROL_ENTER == sc || ClientSystemInfo.isMac() && CMD_ENTER == sc)
          && !IdeEventQueue.getInstance().isPopupActive && DialogWrapper.findInstance(component) != null)
}

// see PlatformActions.xml
private const val POPUP_MENU_PREFIX = "PopupMenu-"

private fun processMenuActions(event: KeyEvent, element: MenuElement): Boolean {
  if (KeyEvent.KEY_PRESSED != event.id || !Registry.`is`("ide.popup.navigation.via.actions")) {
    return false
  }

  val manager = KeymapManager.getInstance() ?: return false
  val pane = getMenuActionsHolder(element.component) ?: return false
  val keymap = manager.activeKeymap
  // iterate through actions for the specified event
  for (id in keymap.getActionIds(KeyStroke.getKeyStrokeForEvent(event))) {
    if (id.startsWith(POPUP_MENU_PREFIX)) {
      val actionId = id.substring(POPUP_MENU_PREFIX.length)
      val action = pane.actionMap.get(actionId)
      if (action != null) {
        action.actionPerformed(ActionEvent(pane, ActionEvent.ACTION_PERFORMED, actionId))
        event.consume()
        // notify dispatcher that event is processed
        return true
      }
    }
  }
  return false
}

private fun getMenuActionsHolder(component: Component): JRootPane? {
  if (component is JPopupMenu) {
    // BasicPopupMenuUI.MenuKeyboardHelper#stateChanged
    return SwingUtilities.getRootPane(component.invoker)
  }
  else {
    return SwingUtilities.getRootPane(component)
  }
}

private fun logTimeMillis(startedAt: Long, action: AnAction) {
  EventWatcher.getInstanceOrNull()?.logTimeMillis(action.toString(), startedAt)
}

// http://www.oracle.com/technetwork/java/javase/documentation/jdk12locales-5294582.html
private val ALT_GR_LANGUAGES: @NonNls Set<String> = setOf(
  "da",  // Danish
  "de",  // German
  "es",  // Spanish
  "et",  // Estonian
  "fi",  // Finnish
  "fr",  // French
  "hr",  // Croatian
  "hu",  // Hungarian
  "it",  // Italian
  "lv",  // Latvian
  "mk",  // Macedonian
  "nl",  // Dutch
  "no",  // Norwegian
  "pl",  // Polish
  "pt",  // Portuguese
  "ro",  // Romanian
  "sk",  // Slovak
  "sl",  // Slovenian
  "sr",  // Serbian
  "sv",  // Swedish
  "tr" // Turkish
)
private val ALT_GR_COUNTRIES: @NonNls Set<String> = setOf(
  "DK",  // Denmark
  "DE",  // Germany
  "FI",  // Finland
  "NL",  // Netherlands
  "SL",  // Slovenia
  "SE" // Sweden
)