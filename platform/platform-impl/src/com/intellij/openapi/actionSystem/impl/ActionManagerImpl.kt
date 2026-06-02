// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet",
               "ReplacePutWithAssignment",
               "ReplaceJavaStaticMethodWithKotlinAnalog",
               "OVERRIDE_DEPRECATION",
               "RemoveRedundantQualifierName")

package com.intellij.openapi.actionSystem.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.concurrency.ExternalIntelliJContextElement
import com.intellij.concurrency.currentThreadContext
import com.intellij.concurrency.installThreadContext
import com.intellij.ide.ActivityTracker
import com.intellij.ide.DataManager
import com.intellij.ide.ProhibitAWTEvents
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.idea.IdeaLogger
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionIdProvider
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsCollectorImpl.Companion.onAfterActionInvoked
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsCollectorImpl.Companion.onBeforeActionInvoked
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AbbreviationManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionPopupMenu
import com.intellij.openapi.actionSystem.ActionStub
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.EmptyAction
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.OverridingAction
import com.intellij.openapi.actionSystem.PerformWithDocumentsCommitted
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.TimerListener
import com.intellij.openapi.actionSystem.ex.ActionContextElement
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.ex.ActionPopupMenuListener
import com.intellij.openapi.actionSystem.ex.ActionRuntimeRegistrar
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.ActionUtil.getActionUnavailableMessage
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer.LightCustomizeStrategy
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorThreading
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.impl.KeymapImpl
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFrame
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.ClientProperty
import com.intellij.util.ArrayUtilRt
import com.intellij.util.SlowOperations
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.ThreadScopeCheckpoint
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.RawSwingDispatcher
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.awt.Component
import java.awt.event.InputEvent
import java.util.function.Function
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal val DEFAULT_ACTION_GROUP_CLASS_NAME = DefaultActionGroup::class.java.name

@ApiStatus.Internal
open class ActionManagerImpl protected constructor(private val coroutineScope: CoroutineScope) : ActionManagerEx() {
  private val actionPopupMenuListeners = ContainerUtil.createLockFreeCopyOnWriteList<ActionPopupMenuListener>()
  private val popups = ArrayList<Any>()
  private var timer: MyTimer? = null
  private val actionPostInitRuntimeRegistrar: ActionRuntimeRegistrar

  override var lastPreformedActionId: String? = null

  override var prevPreformedActionId: String? = null

  private var lastTimeEditorWasTypedIn: Long = 0
  private val actionPostInitRegistrar: PostInitActionRegistrar

  private val keymapToOperations: Map<String, List<KeymapShortcutOperation>>

  private val pluginRegistrar = ActionPluginRegistrar()

  init {
    val app = ApplicationManager.getApplication()
    if (!app.isUnitTestMode && !app.isHeadlessEnvironment && !app.isCommandLine && app.isDispatchThread) {
      actionManagerImplLog.error("Instantiating ActionManager in EDT is prohibited")
    }

    val idToAction = HashMap<String, AnAction>(5_000, 0.5f)
    val boundShortcuts = HashMap<String, String>(512, 0.5f)
    val state = ActionManagerState()
    val actionPreInitRegistrar = ActionPreInitRegistrar(idToAction = idToAction, boundShortcuts = boundShortcuts, state = state)
    val keymapToOperations = HashMap<String, MutableList<KeymapShortcutOperation>>()
    doRegisterActions(descriptors = PluginManagerCore.getPluginSet().sequenceResolvedSortedDescriptorsForRegistration(),
                      keymapToOperations = keymapToOperations,
                      actionRegistrar = actionPreInitRegistrar)

    coroutineScope.launch {
      CustomActionsSchema.getInstanceAsync().incrementModificationStamp()
    }

    this.keymapToOperations = keymapToOperations

    val heavyTasks = preInitRegistration(
      idToAction = idToAction,
      actionPreInitRegistrar = actionPreInitRegistrar,
      coroutineScope = coroutineScope,
    )

    // by intention, _after_ doRegisterActions
    actionPostInitRegistrar = PostInitActionRegistrar(idToAction = idToAction, boundShortcuts = boundShortcuts, state = state)
    actionPostInitRuntimeRegistrar = PostInitActionRuntimeRegistrar(actionPostInitRegistrar)

    for (customizeStrategy in heavyTasks) {
      when (customizeStrategy) {
        is ActionConfigurationCustomizer.SyncHeavyCustomizeStrategy -> {
          @Suppress("LeakingThis")
          customizeStrategy.customize(this)
        }
        is ActionConfigurationCustomizer.AsyncLightCustomizeStrategy -> {
          // execute after we set actionPostInitRegistrar
          coroutineScope.launch {
            customizeStrategy.customize(asActionRuntimeRegistrar())
          }
        }
        is LightCustomizeStrategy -> throw IllegalStateException("$customizeStrategy not expected")
      }
    }

    DYNAMIC_EP_NAME.forEachExtensionSafe { customizer ->
      callDynamicRegistration(customizer, actionPostInitRuntimeRegistrar)
    }

    DYNAMIC_EP_NAME.addExtensionPointListener(coroutineScope, object : ExtensionPointListener<DynamicActionConfigurationCustomizer> {
      override fun extensionAdded(extension: DynamicActionConfigurationCustomizer, pluginDescriptor: PluginDescriptor) {
        callDynamicRegistration(extension, actionPostInitRuntimeRegistrar)
      }

      override fun extensionRemoved(extension: DynamicActionConfigurationCustomizer, pluginDescriptor: PluginDescriptor) {
        extension.unregisterActions(this@ActionManagerImpl)
      }
    })

    app.extensionArea.getExtensionPoint<Any>("com.intellij.editorActionHandler").addChangeListener(coroutineScope) {
      for (action in actionPostInitRegistrar.actions) {
        updateHandlers(action)
      }
    }
  }

  private fun callDynamicRegistration(customizer: DynamicActionConfigurationCustomizer, mutator: ActionRuntimeRegistrar) {
    if (customizer is LightCustomizeStrategy) {
      coroutineScope.launch(Dispatchers.Unconfined) {
        customizer.customize(mutator)
      }
    }
    else {
      customizer.registerActions(this)
    }
  }

  override fun getBoundActions(): Set<String> = actionPostInitRegistrar.getBoundActions()

  override fun getActionBinding(actionId: String): String? = actionPostInitRegistrar.getActionBinding(actionId)

  override fun bindShortcuts(sourceActionId: String, targetActionId: String) {
    actionPostInitRegistrar.bindShortcuts(sourceActionId = sourceActionId, targetActionId = targetActionId)
  }

  override fun unbindShortcuts(targetActionId: String) {
    actionPostInitRegistrar.unbindShortcuts(targetActionId)
  }

  final override fun asActionRuntimeRegistrar(): ActionRuntimeRegistrar = actionPostInitRuntimeRegistrar

  // for dynamic plugins
  internal fun registerActions(descriptors: Sequence<IdeaPluginDescriptorImpl>) {
    val keymapToOperations = HashMap<String, MutableList<KeymapShortcutOperation>>()
    doRegisterActions(descriptors = descriptors, keymapToOperations = keymapToOperations, actionRegistrar = actionPostInitRegistrar)
    if (keymapToOperations.isNotEmpty()) {
      val keymapManager = service<KeymapManager>()
      for ((keymapName, operations) in keymapToOperations) {
        val keymap = keymapManager.getKeymap(keymapName) as KeymapImpl? ?: continue
        keymap.initShortcuts(operations = operations, actionBinding = actionPostInitRegistrar::getActionBinding)
      }
    }
  }

  private fun doRegisterActions(
    descriptors: Sequence<IdeaPluginDescriptorImpl>,
    keymapToOperations: MutableMap<String, MutableList<KeymapShortcutOperation>>,
    actionRegistrar: ActionRegistrar,
  ) {
    pluginRegistrar.registerActions(descriptors = descriptors, keymapToOperations = keymapToOperations, actionRegistrar = actionRegistrar)
  }

  internal fun getKeymapPendingOperations(keymapName: String): List<KeymapShortcutOperation> {
    @Suppress("RemoveRedundantQualifierName")
    return keymapToOperations.get(keymapName) ?: java.util.List.of()
  }

  final override fun addTimerListener(listener: TimerListener) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return
    }

    if (timer == null) {
      timer = MyTimer(coroutineScope.childScope(toString() + " timer"))
    }

    val wrappedListener = if (AppExecutorUtil.propagateContext() && listener !is CapturingListener) {
      CapturingListener(listener)
    }
    else {
      listener
    }
    timer!!.listeners.add(wrappedListener)
  }

  @Experimental
  @Internal
  fun reinitializeTimer() {
    val timer = timer ?: return
    val oldListeners = timer.listeners
    timer.stop()
    this.timer = null
    for (listener in oldListeners) {
      addTimerListener(listener)
    }
  }

  final override fun removeTimerListener(listener: TimerListener) {
    if (listener is CapturingListener) {
      listener.childContext.continuation?.context?.job?.cancel()
    }

    if (ApplicationManager.getApplication().isUnitTestMode) {
      return
    }

    if (actionManagerImplLog.assertTrue(timer != null)) {
      timer!!.listeners.removeIf {
        it == listener || (it is CapturingListener && it.timerListener == listener)
      }
    }
  }

  open fun createActionPopupMenu(place: String, group: ActionGroup, presentationFactory: PresentationFactory?): ActionPopupMenu {
    return ActionPopupMenuImpl(place, group, this, presentationFactory)
  }

  override fun createActionPopupMenu(place: String, group: ActionGroup): ActionPopupMenu {
    return ActionPopupMenuImpl(place, group, this, null)
  }

  override fun createActionToolbar(place: String, group: ActionGroup, horizontal: Boolean): ActionToolbar {
    return createActionToolbar(place = place, group = group, horizontal = horizontal, decorateButtons = false, customizable = true)
  }

  override fun createActionToolbar(place: String, group: ActionGroup, horizontal: Boolean, decorateButtons: Boolean): ActionToolbar {
    return createActionToolbarImpl(place = place,
                                   group = group,
                                   horizontal = horizontal,
                                   decorateButtons = decorateButtons,
                                   customizable = false)
  }

  override fun createActionToolbar(
    place: String,
    group: ActionGroup,
    horizontal: Boolean,
    decorateButtons: Boolean,
    customizable: Boolean,
  ): ActionToolbar {
    return createActionToolbarImpl(place = place,
                                   group = group,
                                   horizontal = horizontal,
                                   decorateButtons = decorateButtons,
                                   customizable = customizable)
  }

  final override fun createActionToolbar(
    place: String,
    group: ActionGroup,
    horizontal: Boolean,
    separatorCreator: Function<in String?, out Component>,
  ): ActionToolbar {
    val toolbar = createActionToolbarImpl(place = place,
                                          group = group,
                                          horizontal = horizontal,
                                          decorateButtons = false,
                                          customizable = true)
    toolbar.setSeparatorCreator(separatorCreator)
    return toolbar
  }

  final override fun getAction(id: String): AnAction? {
    val action = getAction(
      id = id,
      canReturnStub = false,
      actionRegistrar = actionPostInitRegistrar,
      actionSupplier = { getAction(it) }
    )
    if (action == null && SystemProperties.getBooleanProperty("action.manager.log.available.actions.if.not.found", false)) {
      val availableActionIds = actionPostInitRegistrar.getActionIdList("")
      actionManagerImplLog.info("Action $id is not found. Available actions: $availableActionIds")
    }
    return action
  }

  override fun getId(action: AnAction): String? = actionPostInitRegistrar.getId(action)

  final override fun getActionIdList(idPrefix: String): List<String> {
    return actionPostInitRegistrar.getActionIdList(idPrefix)
  }

  @Suppress("OVERRIDE_DEPRECATION")
  final override fun getActionIds(idPrefix: String): Array<String> = ArrayUtilRt.toStringArray(getActionIdList(idPrefix))

  final override fun isGroup(actionId: String): Boolean {
    return getAction(id = actionId, canReturnStub = true, actionRegistrar = actionPostInitRegistrar) is ActionGroup
  }

  final override fun getActionOrStub(id: String): AnAction? {
    return getAction(id = id, canReturnStub = true, actionRegistrar = actionPostInitRegistrar)
  }

  @Experimental
  @Internal
  fun actionsOrStubs(): Sequence<AnAction> = actionPostInitRegistrar.actionsOrStubs()

  @Experimental
  @Internal
  fun unstubbedActions(filter: (String) -> Boolean): Sequence<AnAction> = actionPostInitRegistrar.unstubbedActions(filter)

  @Experimental
  @Internal
  fun groupIds(actionId: String): List<String> = actionPostInitRegistrar.groupIds(actionId)

  fun unloadActions(module: IdeaPluginDescriptorImpl) {
    pluginRegistrar.unloadActions(
      module = module,
      actionRegistrar = actionPostInitRegistrar,
      unregisterAction = { actionId -> unregisterAction(actionId) },
      replaceAction = { actionId, action -> replaceAction(actionId, action) },
    )
  }

  override fun registerAction(actionId: String, action: AnAction, pluginId: PluginId?) {
    actionPostInitRegistrar.state.withLock {
      registerAction(actionId = actionId,
                     action = action,
                     pluginId = pluginId,
                     projectType = null,
                     actionRegistrar = actionPostInitRegistrar)
    }
  }

  override fun registerAction(actionId: String, action: AnAction) {
    actionPostInitRegistrar.state.withLock {
      registerAction(actionId = actionId, action = action, pluginId = null, projectType = null, actionRegistrar = actionPostInitRegistrar)
    }
  }

  override fun unregisterAction(actionId: String) {
    actionPostInitRegistrar.state.withLock {
      unregisterAction(actionId = actionId, actionRegistrar = actionPostInitRegistrar)
    }
  }

  /**
   * Unregisters already registered action and prevented the action from being registered in the future.
   * Should be used only in IDE configuration
   */
  @Internal
  fun prohibitAction(actionId: String) {
    prohibitAction(actionId = actionId, actionPostInitRegistrar)
  }

  private fun prohibitAction(actionId: String, actionRegistrar: ActionRegistrar) {
    val state = actionRegistrar.state
    state.prohibitAction(actionId)
    val action = getAction(
      id = actionId,
      canReturnStub = false,
      actionRegistrar = actionRegistrar
    )
    if (action != null) {
      if (actionRegistrar == actionPostInitRegistrar) {
        AbbreviationManager.getInstance().removeAllAbbreviations(actionId)
      }
      state.withLock {
        unregisterAction(actionId = actionId, actionRegistrar = actionRegistrar)
      }
    }
  }

  @TestOnly
  fun resetProhibitedActions() {
    actionPostInitRegistrar.state.resetProhibitedActions()
  }

  override val registrationOrderComparator: Comparator<String>
    get() {
      val registrationOrder = actionPostInitRegistrar.state.registrationOrderSnapshot()
      return Comparator { id1, id2 ->
        val result = (registrationOrder.get(id1) ?: -1).compareTo(registrationOrder.get(id2) ?: -1)
        if (result == 0) id1.compareTo(id2) else result
      }
    }

  override fun getPluginActions(pluginId: PluginId): Array<String> {
    return actionPostInitRegistrar.state.getPluginActions(pluginId).toTypedArray()
  }

  fun addActionPopup(menu: Any) {
    popups.add(menu)
    if (menu is ActionPopupMenu) {
      for (listener in actionPopupMenuListeners) {
        listener.actionPopupMenuCreated(menu)
      }
    }
  }

  fun removeActionPopup(menu: Any) {
    val removed = popups.remove(menu)
    if (removed && menu is ActionPopupMenu) {
      for (listener in actionPopupMenuListeners) {
        listener.actionPopupMenuReleased(menu)
      }
    }
  }

  override val isActionPopupStackEmpty: Boolean
    get() = popups.isEmpty()

  final override fun addActionPopupMenuListener(listener: ActionPopupMenuListener, parentDisposable: Disposable) {
    actionPopupMenuListeners.add(listener)
    Disposer.register(parentDisposable) { actionPopupMenuListeners.remove(listener) }
  }

  final override fun replaceAction(actionId: String, newAction: AnAction) {
    val plugin = walker.callerClass?.let { PluginManager.getPluginByClass(it) }
    actionPostInitRegistrar.state.withLock {
      replaceAction(actionId = actionId, newAction = newAction, pluginId = plugin?.pluginId, actionRegistrar = actionPostInitRegistrar)
    }
  }

  /**
   * Returns the action overridden by the specified overriding action (with overrides="true" in plugin.xml).
   */
  fun getBaseAction(overridingAction: OverridingAction): AnAction? = actionPostInitRegistrar.getBaseAction(overridingAction)

  fun getParentGroupIds(actionId: String): Collection<String> = actionPostInitRegistrar.state.getParentGroupIds(actionId)

  override fun fireBeforeActionPerformed(action: AnAction, event: AnActionEvent) {
    prevPreformedActionId = lastPreformedActionId
    lastPreformedActionId = getId(action)
    if (lastPreformedActionId == null && action is ActionIdProvider) {
      lastPreformedActionId = (action as ActionIdProvider).id
    }
    IdeaLogger.ourLastActionId = lastPreformedActionId
    ProhibitAWTEvents.start("fireBeforeActionPerformed").use {
      publisher().beforeActionPerformed(action, event)
      onBeforeActionInvoked(action, event)
    }
  }

  override fun fireAfterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult) {
    prevPreformedActionId = lastPreformedActionId
    lastPreformedActionId = getId(action)
    IdeaLogger.ourLastActionId = lastPreformedActionId
    ProhibitAWTEvents.start("fireAfterActionPerformed").use {
      onAfterActionInvoked(action, event, result)
      publisher().afterActionPerformed(action, event, result)
    }
  }

  final override fun getKeyboardShortcut(actionId: String): KeyboardShortcut? {
    val action = getAction(actionId) ?: return null
    for (shortcut in action.shortcutSet.shortcuts) {
      // Shortcut can be a MouseShortcut here.
      // For example, `IdeaVIM` often assigns them
      if (shortcut is KeyboardShortcut && shortcut.secondKeyStroke == null) {
        return shortcut
      }
    }
    return null
  }

  override fun fireBeforeEditorTyping(c: Char, dataContext: DataContext) {
    lastTimeEditorWasTypedIn = System.currentTimeMillis()
    EditorThreading.runWritable {
      publisher().beforeEditorTyping(c, dataContext)
    }
  }

  override fun fireAfterEditorTyping(c: Char, dataContext: DataContext) {
    EditorThreading.runWritable {
      publisher().afterEditorTyping(c, dataContext)
    }
  }

  val actionIds: Set<String>
    get() = actionPostInitRegistrar.ids

  @Internal
  fun actions(canReturnStub: Boolean): Sequence<AnAction> {
    if (canReturnStub) {
      // return snapshot
      return actionPostInitRegistrar.actions.asSequence()
    }
    else {
      return actionPostInitRegistrar.ids.asSequence()
        .mapNotNull {
          getAction(id = it, canReturnStub = false, actionRegistrar = actionPostInitRegistrar)
        }
    }
  }

  override fun performWithActionCallbacks(
    action: AnAction,
    event: AnActionEvent,
    runnable: Runnable,
  ): AnActionResult {
    val project = event.project
    PerformWithDocumentsCommitted.commitDocumentsIfNeeded(action, event)
    fireBeforeActionPerformed(action, event)
    val component = event.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
    val actionId = getId(action)
                   ?: if (action is EmptyAction) runnable.javaClass.name else action.javaClass.name
    if (event.presentation.getClientProperty(ActionUtil.SKIP_ACTION_EXECUTION) == true) {
      actionManagerImplLog.debug("Action execution was skipped: action=$actionId")
      val actionResult = AnActionResult.ignored("action is skipped")
      fireAfterActionPerformed(action, event, actionResult)
      return actionResult
    }
    if (component != null && !UIUtil.isShowing(component) &&
        event.place != ActionPlaces.TOUCHBAR_GENERAL &&
        ClientProperty.get(component, ActionUtil.ALLOW_ACTION_PERFORM_WHEN_HIDDEN) != true) {
      actionManagerImplLog.warn("Action is not performed because target component is not showing: " +
                                "action=$actionId, component=${component.javaClass.name}")
      val actionResult = AnActionResult.ignored("target component is not showing")
      fireAfterActionPerformed(action, event, actionResult)
      return actionResult
    }
    val container =
      if (!event.presentation.isApplicationScope && project is ComponentManagerEx) project
      else ApplicationManager.getApplication() as ComponentManagerEx
    val cs = container.pluginCoroutineScope(action.javaClass.classLoader)
    val coroutineName = CoroutineName("${action.javaClass.name}#actionPerformed@${event.place}")
    // save stack frames using an explicit continuation trick and inline blockingContext
    lateinit var continuation: CancellableContinuation<Unit>
    cs.launch(Dispatchers.Unconfined + coroutineName, CoroutineStart.UNDISPATCHED) {
      suspendCancellableCoroutine { continuation = it }
    }
    val result = try {
      val coroutineContext = continuation.context +
                             ModalityState.current().asContextElement() +
                             ClientId.coroutineContext() +
                             currentThreadContext().fold<CoroutineContext>(EmptyCoroutineContext) { acc, elem ->
                               acc + (elem as? ExternalIntelliJContextElement ?: EmptyCoroutineContext)
                             } +
                             ActionContextElement.create(actionId, event.place, event.inputEvent, component)
      // todo: remove `ThreadScopeCheckpoint` from here once we migrate all usages to `AnActionEvent#coroutineScope`
      val coroutineContext2 = coroutineContext + ThreadScopeCheckpoint(coroutineContext) // permit `currentThreadCoroutineScope` inside
      val providedScope =
        ActionCoroutineScope(coroutineContext.minusKey(Job.Key) + Dispatchers.Default + CoroutineName("actionPerformed of $actionId"), cs)
      event.installCoroutineScope(providedScope)
      installThreadContext(coroutineContext2.minusKey(ContinuationInterceptor), replace = true) {
        runInWriteIntentConditionally(action) {
          SlowOperations.startSection(SlowOperations.ACTION_PERFORM).use { _ ->
            runnable.run()
          }
        }
      }
      AnActionResult.PERFORMED
    }
    catch (ex: Throwable) {
      AnActionResult.failed(ex)
    }
    finally {
      continuation.resume(Unit)
    }
    try {
      fireAfterActionPerformed(action, event, result)
    }
    catch (ex: Throwable) {
      if (result is AnActionResult.Performed) throw ex
      else (result as? AnActionResult.Failed)?.cause?.addSuppressed(ex)
    }
    when (result) {
      is AnActionResult.Performed -> Unit
      is AnActionResult.Failed -> {
        if (result.cause is IndexNotReadyException) {
          actionManagerImplLog.info(result.cause)
          if (project != null) {
            DumbService.getInstance(project)
              .showDumbModeNotificationForFailedAction(getActionUnavailableMessage(event.presentation.text), getId(action))
          }
        }
        else {
          throw result.cause
        }
      }
      is AnActionResult.Ignored -> Unit
    }
    return result
  }

  // inlining here to reduce the number of service stacktraces
  @Suppress("NOTHING_TO_INLINE")
  inline fun runInWriteIntentConditionally(action: AnAction, runnable: Runnable) {
    if (Utils.isLockRequired(action)) {
      WriteIntentReadAction.run(runnable)
    }
    else {
      runnable.run()
    }
  }

  @TestOnly
  fun preloadActions() {
    for (id in actionPostInitRegistrar.ids) {
      getAction(id = id, canReturnStub = false, actionRegistrar = actionPostInitRegistrar)
      // don't preload ActionGroup.getChildren() because that would un-stub child actions
      // and make it impossible to replace the corresponding actions later
      // (via unregisterAction+registerAction, as some app components do)
    }
  }

  /**
   * The coroutines in actions should be launched on a scope with a specific coroutine context,
   * that's why we augment the container's scope with [localContext].
   * In addition, scope's context should NOT be terminated when the action is finished, because the action could fire some `invokeLater`s
   * which capture this context and possibly launch some more coroutines.
   */
  private class ActionCoroutineScope(val localContext: CoroutineContext, val delegate: CoroutineScope) : CoroutineScope by delegate {
    override val coroutineContext: CoroutineContext
      get() = delegate.coroutineContext + localContext

    override fun toString(): String {
      return "ActionCoroutineScope(delegate=$delegate, localContext=$localContext)"
    }
  }

  override fun tryToExecute(
    action: AnAction,
    inputEvent: InputEvent?,
    contextComponent: Component?,
    place: String?,
    now: Boolean,
  ): ActionCallback {
    ThreadingAssertions.assertEventDispatchThread()
    val result = ActionCallback()
    val place = place ?: "tryToExecute"
    if (now) {
      try {
        @Suppress("DEPRECATION")
        val dataContext = DataManager.getInstance().let {
          if (contextComponent == null) it.dataContext else it.getDataContext(contextComponent)
        }
        tryToExecuteNow(action, place, contextComponent, inputEvent, result, dataContext)
      }
      finally {
        if (!result.isProcessed) {
          result.reject("unknown error")
        }
      }
    }
    else {
      service<CoreUiCoroutineScopeHolder>().coroutineScope.launch(Dispatchers.EDT) {
        try {
          tryToExecuteSuspend(action, place, contextComponent, inputEvent, this@ActionManagerImpl, result)
        }
        finally {
          if (!result.isProcessed) {
            result.reject("unknown error")
          }
        }
      }
    }
    return result
  }

  private val _timerEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_LATEST)

  @Internal
  @Experimental
  override val timerEvents: Flow<Unit> = _timerEvents.asSharedFlow()

  private inner class MyTimer(private val coroutineScope: CoroutineScope) {
    @JvmField
    val listeners: MutableList<TimerListener> = ContainerUtil.createLockFreeCopyOnWriteList()

    private var lastTimePerformed = 0

    init {
      val connection = ApplicationManager.getApplication().messageBus.simpleConnect()

      val delayFlow = MutableSharedFlow<Duration>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

      connection.subscribe(ApplicationActivationListener.TOPIC, object : ApplicationActivationListener {
        override fun applicationActivated(ideFrame: IdeFrame) {
          delayFlow.tryEmit(TIMER_DELAY.milliseconds)
        }

        override fun applicationDeactivated(ideFrame: IdeFrame) {
          delayFlow.tryEmit(DEACTIVATED_TIMER_DELAY.milliseconds)
        }
      })

      delayFlow.tryEmit(TIMER_DELAY.milliseconds)

      coroutineScope.launch {
        delayFlow.collectLatest { delay ->
          while (true) {
            delay(delay)
            // RawSwingDispatcher - as old javax.swing.Timer does
            withContext(RawSwingDispatcher) {
              tick()
            }
          }
        }
      }
    }

    fun stop() {
      coroutineScope.cancel()
    }

    private fun tick() {
      if (lastTimeEditorWasTypedIn + UPDATE_DELAY_AFTER_TYPING > System.currentTimeMillis()) {
        return
      }

      val lastEventCount = lastTimePerformed
      lastTimePerformed = ActivityTracker.getInstance().count
      if (lastTimePerformed == lastEventCount) {
        return
      }

      _timerEvents.tryEmit(Unit)

      for (listener in listeners) {
        runListenerAction(listener)
      }
    }
  }
}
