// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ActionUpdaterKt")
@file:OptIn(IntellijInternalApi::class)

package com.intellij.openapi.actionSystem.impl

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.concurrency.IntelliJContextElement
import com.intellij.concurrency.currentThreadContext
import com.intellij.diagnostic.PluginException
import com.intellij.diagnostic.ThreadDumpService
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.ProhibitAWTEvents
import com.intellij.internal.DebugAttachDetector
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.ActionUtil.ALWAYS_VISIBLE_GROUP
import com.intellij.openapi.actionSystem.ex.ActionUtil.HIDE_DISABLED_CHILDREN
import com.intellij.openapi.actionSystem.ex.ActionUtil.SUPPRESS_SUBMENU
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.ex.InlineActionsHolder
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.CeProcessCanceledException
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.*
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.FList
import com.intellij.util.ui.EDT
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import java.awt.AWTEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.lang.Integer.max
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import javax.swing.JComponent
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private val LOG = logger<ActionUpdater>()

@JvmField
internal val SUPPRESS_SUBMENU_IMPL: Key<Boolean> = Key.create("internal.SUPPRESS_SUBMENU_IMPL")

private const val REVISE_AUT_MSG_SUFFIX = ". Revise AnAction.getActionUpdateThread property"

private const val OP_expandActionGroup = "expandGroup"
private const val OP_groupChildren = "children"
private const val OP_groupExpandedChildren = "expandedChildren"
private const val OP_actionPresentation = "presentation"
private const val OP_groupPostProcess = "postProcessChildren"
private const val OP_sessionData = "sessionData"

private val ourToolbarJobs: MutableSet<Job> = ConcurrentCollectionFactory.createConcurrentSet()
private val ourOtherJobs: MutableSet<Job> = ConcurrentCollectionFactory.createConcurrentSet()
private var ourInEDTActionOperationStack: FList<String> = FList.emptyList()

internal class ActionUpdater @JvmOverloads constructor(
  private val presentationFactory: PresentationFactory,
  private val dataContext: DataContext,
  val place: String,
  private val contextMenuAction: Boolean,
  private val toolbarAction: Boolean,
  private val edtDispatcher: CoroutineDispatcher,
  private val actionFilter: ((AnAction) -> Boolean)? = null,
  private val eventTransform: ((AnActionEvent) -> AnActionEvent)? = null) {

  @Volatile private var bgtScope: CoroutineScope? = null

  private val application: Application = com.intellij.util.application
  private val actionManager = ActionManager.getInstance()
  private val sessionData = ConcurrentHashMap<SessionKey, Deferred<*>>()
  private val updatedPresentations = ConcurrentHashMap<AnAction, Presentation>()
  private val groupChildren = ConcurrentHashMap<ActionGroup, List<AnAction>>()

  private val testDelayMillis =
    if (ActionPlaces.ACTION_SEARCH == place || ActionPlaces.isShortcutPlace(place)) 0
    else Registry.intValue("actionSystem.update.actions.async.test.delay", 0)
  private val threadDumpService = ThreadDumpService.getInstance()

  private val maxAwaitSharedDataRetries = max(1, Registry.intValue("actionSystem.update.actions.max.await.retries", 500))

  private var edtCallsCount: Int = 0 // used only in EDT
  private var edtWaitNanos: Long = 0 // used only in EDT

  init {
    checkRecursiveUpdateSession()
  }

  @RequiresEdt
  fun applyPresentationChanges() {
    for ((action, copy) in updatedPresentations) {
      val orig = presentationFactory.getPresentation(action)
      var customComponent: JComponent? = null
      if (action is CustomComponentAction) {
        // 1. toolbar may have already created a custom component, do not erase it
        // 2. presentation factory may be just reset, do not reuse component from a copy
        customComponent = orig.getClientProperty(CustomComponentAction.COMPONENT_KEY)
      }
      presentationFactory.postProcessPresentation(action, copy)
      orig.copyFrom(copy, customComponent, true)
      if (customComponent != null && orig.isVisible) {
        (action as CustomComponentAction).updateCustomComponent(customComponent, orig)
      }
    }
  }

  private suspend fun <T> callAction(opElement: OpElement,
                                     updateThreadOrig: ActionUpdateThread,
                                     call: () -> T): T {
    val operationName = opElement.operationName
    val forcedUpdateThread = opElement.all().mapNotNull { it.forcedUpdateThread }.firstOrNull()
    val updateThread: ActionUpdateThread = forcedUpdateThread ?: updateThreadOrig
    val canAsync = Utils.isAsyncDataContext(dataContext)
    val shallAsync = updateThread == ActionUpdateThread.BGT
    val isEDT = EDT.isCurrentThreadEdt()
    val shallEDT = !(canAsync && shallAsync)
    if (isEDT && !shallEDT && !SlowOperations.isInSection(SlowOperations.ACTION_PERFORM) && !application.isUnitTestMode()) {
      LOG.error("Calling on EDT $operationName that requires $updateThread${if (forcedUpdateThread != null) " (forced)" else ""}")
    }
    if (isEDT || !shallEDT) {
      val spanBuilder = Utils.getTracer(true).spanBuilder(operationName)
      return spanBuilder.useWithScope(EmptyCoroutineContext) {
        readActionUndispatchedForActionExpand {
          val start = System.nanoTime()
          try {
            ProhibitAWTEvents.start(operationName).use {
              call()
            }
          }
          finally {
            val elapsed = TimeoutUtil.getDurationMillis(start)
            if (elapsed > 1000) {
              LOG.warn(elapsedReport(elapsed, isEDT, operationName))
            }
          }
        }
      }
    }
    return computeOnEdt(opElement, updateThread == ActionUpdateThread.EDT) {
      call()
    }
  }

  private suspend fun <T> computeOnEdt(opElement: OpElement, noRulesInEDT: Boolean, call: () -> T): T {
    val operationName = opElement.operationName
    var currentEDTPerformMillis = 0L
    var currentEDTWaitMillis = 0L
    var edtTraces: List<Throwable>? = null
    val start0 = System.nanoTime()
    return try {
      computeOnEdt {
        val start = System.nanoTime()
        edtCallsCount++
        edtWaitNanos += start - start0
        currentEDTWaitMillis = TimeUnit.NANOSECONDS.toMillis(start - start0)
        Utils.getTracer(true).spanBuilder(operationName).use { span: Span ->
          val prevStack = ourInEDTActionOperationStack
          val prevNoRules = isNoRulesInEDTSection
          var traceCookie: ThreadDumpService.Cookie? = null
          try {
            Triple({ ProhibitAWTEvents.start(operationName) },
                   { IdeEventQueue.getInstance().threadingSupport.prohibitWriteActionsInside() },
                   { threadDumpService.start(100, 50, 5, Thread.currentThread()) }).use { _, _, cookie ->
              traceCookie = cookie
              ourInEDTActionOperationStack = prevStack.prepend(operationName)
              isNoRulesInEDTSection = noRulesInEDT
              call()
            }
          }
          finally {
            isNoRulesInEDTSection = prevNoRules
            ourInEDTActionOperationStack = prevStack
            traceCookie?.apply {
              currentEDTPerformMillis = TimeoutUtil.getDurationMillis(startNanos)
              edtTraces = traces
            }
          }
        }
      }
    }
    finally {
      if (currentEDTWaitMillis > 300) {
        LOG.info("$currentEDTWaitMillis ms to grab EDT for $operationName")
      }
      if (currentEDTPerformMillis > 300) {
        reportSlowEdtOperation(opElement.sessionKey ?: opElement.action ?: opElement,
                               operationName, currentEDTPerformMillis, edtTraces)
      }
    }
  }

  suspend fun <R : Any?> runUpdateSession(coroutineContext: CoroutineContext, block: suspend CoroutineScope.() -> R): R =
    withContext(coroutineContext) {
      val childScope = childScope("ActionUpdater.runUpdateSession")
      bgtScope = childScope
      try {
        block()
      }
      finally {
        childScope.cancel()
        bgtScope = null
      }
    }

  /**
   * Returns actions from the given and nested non-popup groups that are visible after updating
   */
  @RequiresBackgroundThread
  suspend fun expandActionGroup(group: ActionGroup): List<AnAction> {
    edtCallsCount = 0
    edtWaitNanos = 0
    val job = currentCoroutineContext().job
    val targetJobs = if (toolbarAction) ourToolbarJobs else ourOtherJobs
    targetJobs.add(job)
    try {
      if (testDelayMillis > 0) {
        delay(testDelayMillis.toLong())
      }
      val result = ActionUpdaterInterceptor.expandActionGroup(
        presentationFactory, dataContext, place, group, toolbarAction, asUpdateSession()) {
        removeUnnecessarySeparators(doExpandActionGroup(group, false))
      }
      computeOnEdt {
        applyPresentationChanges()
      }
      return result
    }
    finally {
      targetJobs.remove(job)
      val edtWaitMillis = TimeUnit.NANOSECONDS.toMillis(edtWaitNanos)
      if (edtCallsCount > 500 || edtWaitMillis > 3000) {
        LOG.warn(edtWaitMillis.toString() + " ms total to grab EDT " + edtCallsCount + " times to expand " +
                 Utils.operationName(group, OP_expandActionGroup, place) + ". Use `ActionUpdateThread.BGT`.")
      }
    }
  }

  private suspend fun doExpandActionGroup(group: ActionGroup, hideDisabled: Boolean): List<AnAction> {
    if (group is ActionGroupStub) {
      throw IllegalStateException("ActionGroupStub cannot be expanded")
    }
    val opElement = OpElement.next(group, OP_expandActionGroup, place, null)
    return withContext(opElement) {
      val presentation = updateAction(group)
      if (presentation == null || !presentation.isVisible) {
        // don't process invisible groups
        return@withContext emptyList()
      }
      val hideDisabled = hideDisabled || group is CompactActionGroup || presentation.getClientProperty(HIDE_DISABLED_CHILDREN) == true
      val children = getGroupChildren(group)
      // parallel update execution can break some existing caching
      // the preferred way to do caching now is `updateSession.sharedData`
      val expandResult = children
        .map {
          async {
            expandGroupChild(it, hideDisabled)
          }
        }
        .awaitAll()
        .flatten()
      val result = postProcessGroupChildren(group, expandResult)
      result
        .mapNotNull {
          updatedPresentations[it]?.getClientProperty(ActionUtil.INLINE_ACTIONS)
          ?: (it as? InlineActionsHolder)?.inlineActions
        }
        .flatten()
        .map {
          async {
            updateAction(it)
          }
        }
        .toList()
        .awaitAll()
      result
    }
  }

  private suspend fun postProcessGroupChildren(group: ActionGroup, result: List<AnAction>): List<AnAction> {
    if (isDefaultImplementationRecursively(OP_groupPostProcess, group)) {
      return result
    }
    val opElement = OpElement.next(group, OP_groupPostProcess, place, null)
    val event = createActionEvent(opElement, updatedPresentations[group] ?: initialBgtPresentation(group))
    return try {
      retryOnAwaitSharedData(opElement, maxAwaitSharedDataRetries) {
        blockingContext { // no data-context hence no RA, just blockingContext
          val spanBuilder = Utils.getTracer(true).spanBuilder(opElement.operationName)
          spanBuilder.use {
            group.postProcessVisibleChildren(event, result)
          }
        }
      }
    }
    catch (ex: Throwable) {
      handleException(opElement, updatedPresentations[group] ?: group.templatePresentation, actionManager, ex)
      result
    }
  }

  /** same event/retry/intercept/cache logic as in [updateAction] */
  private suspend fun getGroupChildren(group: ActionGroup): List<AnAction> {
    val cached = groupChildren[group]
    if (cached != null) {
      return cached
    }
    // use initial presentation if there's no updated presentation (?)
    val opElement = OpElement.next(group, OP_groupChildren, place, null)
    val event = createActionEvent(opElement, updatedPresentations[group] ?: initialBgtPresentation(group))
    val children = try {
      retryOnAwaitSharedData(opElement, maxAwaitSharedDataRetries) {
        ActionUpdaterInterceptor.getGroupChildren(group, event) {
          callAction(opElement, group.actionUpdateThread) {
            group.getChildren(event)
          }.let {
            ensureNotNullChildren(opElement, it)
          }
        }
      }.asList()
    }
    catch (ex: Throwable) {
      handleException(opElement, event.presentation, actionManager, ex)
      return emptyList()
    }
    return groupChildren.getOrPut(group) { children }
  }

  private suspend fun expandGroupChild(child: AnAction, hideDisabledBase: Boolean): List<AnAction> {
    val presentation = updateAction(child)
    if (presentation == null) {
      return emptyList()
    }
    else if (!presentation.isVisible || hideDisabledBase && !presentation.isEnabled) {
      return emptyList()
    }
    else if (child !is ActionGroup) {
      return listOf(child)
    }
    val isPopup = presentation.isPopupGroup
    val canBePerformed = presentation.isPerformGroup
    var performOnly = isPopup && canBePerformed && presentation.getClientProperty(SUPPRESS_SUBMENU) == true
    val alwaysVisible = child is AlwaysVisibleActionGroup || presentation.getClientProperty(ALWAYS_VISIBLE_GROUP) == true
    val skipChecks = performOnly || alwaysVisible
    val hideDisabled = isPopup && !skipChecks && hideDisabledBase
    val hideEmpty = isPopup && !skipChecks && presentation.isHideGroupIfEmpty
    val disableEmpty = isPopup && !skipChecks && presentation.isDisableGroupIfEmpty
    val checkChildren = isPopup && !skipChecks && (canBePerformed || hideDisabled || hideEmpty || disableEmpty)
    var hasEnabled = false
    var hasVisible = false
    if (checkChildren) {
      var last: AnAction? = null // for debug
      val childrenFlow = iterateGroupChildren(child)
      childrenFlow.take(100).filter { it !is Separator }.takeWhile { action ->
        val p = updateAction(action)
        hasVisible = hasVisible or (p?.isVisible == true)
        hasEnabled = hasEnabled or (p?.isEnabled == true)
        // stop early if all the required flags are collected
        val result = !(hasVisible && (hasEnabled || !hideDisabled))
        last = action
        result
      }
        .collect()
      performOnly = canBePerformed && !hasVisible
    }
    if (isPopup) {
      presentation.putClientProperty(SUPPRESS_SUBMENU_IMPL, if (performOnly) true else null)
      if (!performOnly && !hasVisible && disableEmpty) {
        presentation.setEnabled(false)
      }
    }
    val isCompactGroup = child is CompactActionGroup || presentation.getClientProperty(HIDE_DISABLED_CHILDREN) == true
    val hideDisabledChildren = (hideDisabledBase || isCompactGroup) && !alwaysVisible
    return when {
      !hasEnabled && hideDisabled || !hasVisible && hideEmpty -> when {
        canBePerformed -> listOf(child)
        else -> emptyList()
      }
      isPopup -> when {
        hideDisabledChildren && !isCompactGroup -> listOf(ActionGroupUtil.forceHideDisabledChildren(child))
        else -> listOf(child)
      }
      else -> doExpandActionGroup(child, hideDisabledChildren)
    }
  }

  private fun initialBgtPresentation(action: AnAction): Presentation {
    return presentationFactory.getPresentation(action).clone()
  }

  @Suppress("unused")
  private fun createActionEvent(element: OpElement, presentation: Presentation): AnActionEvent {
    val event = AnActionEvent(
      null, dataContext, place, presentation,
      actionManager, 0, contextMenuAction, toolbarAction).let {
      eventTransform?.invoke(it) ?: it
    }
    event.updateSession = asUpdateSession()
    return event
  }

  @OptIn(DelicateCoroutinesApi::class)
  private suspend fun <T> computeOnEdt(supplier: () -> T): T {
    // We need the block below to escape the current scope on WA to let the parent RA free
    // while the EDT block is still waiting to be cancelled in the EDT queue.
    // The target scope must not be cancelled by `AwaitSharedData` exception (SupervisorJob)!
    val scope = bgtScope ?: service<CoreUiCoroutineScopeHolder>().coroutineScope
    val deferred = scope.async(
      currentCoroutineContext().minusKey(Job) +
      CoroutineName("computeOnEdt ($place)") + edtDispatcher) {
      writeIntentReadAction {
        supplier()
      }
    }
    try {
      return deferred.await()
    }
    catch (ce: CancellationException) {
      deferred.cancel(ce)
      throw ce
    }
  }

  fun asUpdateSession(): SuspendingUpdateSession {
    return UpdateSessionImpl(this)
  }

  private fun iterateGroupChildren(group: ActionGroup): Flow<AnAction> = flow {
    val tree: suspend (AnAction) -> List<AnAction>? = { o ->
      // in all clients the next call is `update`, let's update both actions and groups here
      val presentation = if (o === group) null else updateAction(o)
      if (o !is ActionGroup || presentation == null || !presentation.isVisible ||
          presentation.isPopupGroup || presentation.isPerformGroup) {
        null
      }
      else {
        getGroupChildren(o)
      }
    }
    val roots = getGroupChildren(group)
    if (roots.isEmpty()) return@flow
    val set = HashSet<AnAction>()
    val queue = ArrayDeque(roots)
    while (!queue.isEmpty()) {
      val first = queue.removeFirst()
      if (!set.add(first)) continue
      val children = tree(first)
      if (children.isNullOrEmpty()) emit(first)
      else children.reversed().forEach(queue::addFirst)
    }
  }

  suspend fun presentation(action: AnAction): Presentation {
    return updateAction(action) ?: initialBgtPresentation(action)
  }

  /** same event/retry/intercept/cache logic as in [getGroupChildren] */
  private suspend fun updateAction(action: AnAction): Presentation? {
    val cached = updatedPresentations[action]
    if (cached != null) {
      return cached
    }
    // clone the presentation to avoid partially changing the cached one if the update is interrupted
    val presentation = presentationFactory.getPresentation(action).clone()
    if (actionFilter?.invoke(action) == true) {
      presentation.isEnabledAndVisible = false
      updatedPresentations[action] = presentation
      if (action is ActionGroup) {
        groupChildren[action] = emptyList()
      }
      return presentation
    }
    val opElement = OpElement.next(action, OP_actionPresentation, place, null)
    val event = createActionEvent(opElement, presentation)
    val success = try {
      retryOnAwaitSharedData(opElement, maxAwaitSharedDataRetries) {
        ActionUpdaterInterceptor.updateAction(action, event) {
          // reset enabled/visible flags (actions are encouraged to always set them in `update`)
          presentation.setEnabledAndVisible(true)
          if (isDefaultImplementationRecursively(OP_actionPresentation, action)) {
            action.applyTextOverride(event)
            return@updateAction true
          }
          callAction(opElement, action.actionUpdateThread) {
            !ActionUtil.performDumbAwareUpdate(action, event, false)
          }
        }
      }
    }
    catch (ex: Throwable) {
      handleException(opElement, event.presentation, actionManager, ex)
      return null
    }
    if (success) {
      return updatedPresentations.getOrPut(action) { presentation }
    }
    return null
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T : Any?> getSessionDataDeferred(opElement: OpElement?,
                                                key: SessionKey,
                                                supplier: suspend () -> T): Deferred<T> {
    val opNext = OpElement.next(opElement, opElement?.action, OP_sessionData, place, key)
    val existing = sessionData[key]
    if (existing != null) {
      return existing as Deferred<T>
    }
    val bgtScope = bgtScope
    val context = currentThreadContext().minusKey(Job) + opNext
    return if (bgtScope != null) {
      sessionData.computeIfAbsent(key) {
        bgtScope.async(context) {
          val spanBuilder = Utils.getTracer(true).spanBuilder("${key.opName}@$place")
          spanBuilder.useWithScope(EmptyCoroutineContext) {
            supplier()
          }
        }
      }
    }
    else {
      // not a good branch to be in, seek ways to get bgtScope
      val deferred = CompletableDeferred<T>()
      sessionData.computeIfAbsent(key) { deferred }.also {
        if (it == deferred) {
          deferred.completeWith(runCatching {
            runBlockingForActionExpand(context) {
              supplier()
            }
          })
        }
      }
    } as Deferred<T>
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun <T: Any?> computeSessionDataOrThrow(key: SessionKey, supplier: suspend () -> T): T {
    val opElement = currentThreadContext()[OpElement]
    val deferred = getSessionDataDeferred(opElement, key, supplier)
    if (deferred.isCompleted) {
      try {
        return deferred.getCompleted()
      }
      catch (pce : ProcessCanceledException) {
        throw pce
      }
      catch (ex: CancellationException) {
        throw CeProcessCanceledException(ex)
      }
    }
    val operationName = "session.${key.opName} at ${opElement?.operationName ?: "unknown"}"
    throw AwaitSharedData(deferred, operationName)
  }

  companion object {
    var isNoRulesInEDTSection: Boolean = false
      private set

    fun currentInEDTOperationName(): String? = ourInEDTActionOperationStack.head

    fun getUpdater(updateSession: UpdateSession) = (updateSession as? UpdateSessionImpl)?.updater

    init {
      IdeEventQueue.getInstance().addPreprocessor(IdeEventQueue.EventDispatcher { event: AWTEvent ->
        if (event is KeyEvent && event.keyCode != 0 ||
            event is MouseEvent && event.getID() == MouseEvent.MOUSE_PRESSED) {
          cancelAllUpdates(ourToolbarJobs, event.toString())
        }
        false
      }, ApplicationManager.getApplication())
    }
  }

  private class UpdateSessionImpl(val updater: ActionUpdater) : SuspendingUpdateSession {
    override fun expandedChildren(actionGroup: ActionGroup): Iterable<AnAction> {
      val sessionKey = SessionKey(OP_groupExpandedChildren, actionGroup)
      return updater.computeSessionDataOrThrow(sessionKey) {
        updater.iterateGroupChildren(actionGroup).toCollection(ArrayList())
      }
    }

    override fun children(actionGroup: ActionGroup): List<AnAction> {
      val sessionKey = SessionKey(OP_groupChildren, actionGroup)
      return updater.groupChildren[actionGroup] ?: updater.computeSessionDataOrThrow(sessionKey) {
        updater.getGroupChildren(actionGroup)
      }
    }

    override fun presentation(action: AnAction): Presentation {
      val sessionKey = SessionKey(OP_actionPresentation, action)
      return updater.updatedPresentations[action] ?: updater.computeSessionDataOrThrow(sessionKey) {
        updater.updateAction(action) ?: updater.initialBgtPresentation(action)
      }
    }

    override fun <T : Any> sharedData(key: Key<T>, supplier: Supplier<out T>): T {
      val sessionKey = SessionKey(key.toString(), key)
      return updater.computeSessionDataOrThrow(sessionKey) {
        readActionUndispatchedForActionExpand {
          supplier.get()
        }
      }
    }

    override fun <T> compute(action: Any,
                             opName: String,
                             updateThread: ActionUpdateThread,
                             supplier: Supplier<out T>): T {
      if (updateThread == ActionUpdateThread.EDT && EDT.isCurrentThreadEdt()) {
        return supplier.get()
      }
      val opCur = currentThreadContext()[OpElement]
      val sessionKey = SessionKey(opName, action)
      val opElement = OpElement.next(opCur, opCur?.action, OP_sessionData, updater.place, sessionKey)
      return runBlockingForActionExpand(opElement) {
        updater.callAction(opElement, updateThread) { supplier.get() }
      }
    }

    override suspend fun presentationSuspend(action: AnAction): Presentation {
      return updater.updateAction(action) ?: updater.initialBgtPresentation(action)
    }

    override suspend fun childrenSuspend(actionGroup: ActionGroup): List<AnAction> {
      return updater.getGroupChildren(actionGroup)
    }

    override suspend fun expandSuspend(group: ActionGroup): List<AnAction> {
      return updater.expandActionGroup(group)
    }

    override fun <T: Any?> sharedDataSuspend(key: Key<T>, supplier: suspend () -> T): T {
      val sessionKey = SessionKey(key.toString(), key)
      return updater.computeSessionDataOrThrow(sessionKey) {
        supplier()
      }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun visitCaches(visitor: (AnAction, String, Any) -> Unit) {
      updater.updatedPresentations.forEach { action, presentation -> visitor(action, OP_actionPresentation, presentation) }
      updater.groupChildren.forEach { action, children -> visitor(action, OP_groupChildren, children) }
      updater.sessionData.forEach { pair, deferred ->
        if (pair.opName == OP_groupExpandedChildren) visitor(pair.source as ActionGroup, pair.opName, deferred.getCompleted()!!) }
    }

    override fun dropCaches(predicate: (Any) -> Boolean) {
      // if reused, clear temporary deferred caches from previous `expandActionGroup` calls
      // some are in completed-with-exception state (SkipOperation), so re-calling will fail
      // 1. valid presentation and children are already cached in other maps
      // 2. expanded children must not be cached in remote scenarios anyway
      updater.sessionData.keys.removeIf { (op, key) ->
        op == OP_actionPresentation || op == OP_groupChildren || op == OP_groupExpandedChildren ||
        key is Key<*> && predicate(key)
      }
      // clear caches for selected actions
      updater.updatedPresentations.keys.removeIf(predicate)
      updater.groupChildren.keys.removeIf(predicate)
    }

    override suspend fun <T> readAction(block: () -> T): T {
      return readActionUndispatchedForActionExpand(block)
    }
  }
}

private fun reportSlowEdtOperation(action: Any,
                                   operationName: String,
                                   currentEDTPerformMillis: Long,
                                   edtTraces: List<Throwable>?) {
  var edtTraces1 = edtTraces
  val throwable: Throwable = PluginException.createByClass(
    elapsedReport(currentEDTPerformMillis, true, operationName) + REVISE_AUT_MSG_SUFFIX, null, action.javaClass)
  val edtTraces = edtTraces1
  // do not report pauses without EDT traces (e.g., due to debugging)
  if (edtTraces != null && !edtTraces.isEmpty() && edtTraces[0].stackTrace.isNotEmpty()) {
    for (trace in edtTraces) {
      throwable.addSuppressed(trace)
    }
    LOG.error(throwable)
  }
  else if (!DebugAttachDetector.isDebugEnabled()) {
    LOG.warn(throwable)
  }
}

@ApiStatus.Internal
fun cancelAllUpdates(reason: String) {
  val adjusted = "$reason (cancelling all updates)"
  cancelAllUpdates(ourToolbarJobs, adjusted)
  cancelAllUpdates(ourOtherJobs, adjusted)
}

private fun cancelAllUpdates(jobs: MutableCollection<Job>, reason: String) {
  if (jobs.isEmpty()) return
  for (job in jobs) {
    job.cancel(reason)
  }
  jobs.clear()
}

internal suspend fun waitForAllUpdatesToFinish() {
  (ourToolbarJobs + ourOtherJobs).joinAll()
}

private fun removeUnnecessarySeparators(visible: List<AnAction>): List<AnAction> {
  var first = true
  return visible.filterIndexed { i, child ->
    val skip = child is Separator && (
      i == visible.size - 1 ||
      visible[i + 1] is Separator ||
      first && StringUtil.isEmpty(child.text))
    if (!skip) first = false
    !skip
  }
}

private fun elapsedReport(elapsed: Long, isEDT: Boolean, operationName: String): String {
  return elapsed.toString() + (if (isEDT) " ms to call on EDT " else " ms to call on BGT ") + operationName
}

private fun handleException(opElement: OpElement, presentation: Presentation, actionManager: ActionManager, ex: Throwable) {
  if (ex is CancellationException) throw ex
  if (ex is AwaitSharedData) throw ex
  if (ex is SkipOperation) {
    if (opElement.isNested()) throw ex
    else return
  }
  val id = opElement.action?.let { actionManager.getId(it) }
  val text = presentation.text
  val message = opElement.operationName +
                (if (id != null) ", actionId=$id" else "") +
                if (StringUtil.isNotEmpty(text)) ", text='$text'" else ""
  LOG.error(message, ex)
}

private fun ensureNotNullChildren(opElement: OpElement, children: Array<AnAction?>): Array<AnAction> {
  val nullIndex = (children as Array<*>).indexOf(null)
  return if (nullIndex < 0) {
    @Suppress("UNCHECKED_CAST")
    children as Array<AnAction>
  }
  else {
    LOG.error("$nullIndex item is null in ${opElement.operationName}")
    children.filterNotNull().toTypedArray()
  }
}

private fun isDefaultImplementationRecursively(op: String, action: AnAction): Boolean {
  var cur: AnAction? = action
  while (cur != null) {
    val next = (cur as? AnActionWrapper)?.delegate ?: (cur as? ActionGroupWrapper)?.delegate
    val isDefault = when (op) {
      OP_actionPresentation ->
        if (next != null) ActionClassMetaData.isWrapperUpdate(cur)
        else ActionClassMetaData.isDefaultUpdate(cur)
      OP_groupChildren ->
        if (next != null) ActionClassMetaData.isWrapperGetChildren(cur as ActionGroup)
        else ActionClassMetaData.isDefaultGetChildren(cur as ActionGroup)
      OP_groupPostProcess ->
        if (next != null) ActionClassMetaData.isWrapperPostProcessVisibleChildren(cur as ActionGroup)
        else ActionClassMetaData.isDefaultPostProcessVisibleChildren(cur as ActionGroup)
      else -> throw AssertionError(op)
    }
    if (!isDefault) {
      return false
    }
    cur = next
  }
  return true
}

private fun checkCyclicDependency(opElement: OpElement) {
  val same = opElement.all().drop(1).find { op ->
    when (val k = opElement.sessionKey) {
      null -> op.sessionKey == null &&
              opElement.opName == op.opName && opElement.action === op.action
      else -> k == op.sessionKey ||
              k.opName == op.opName && k.source === op.action
    }
  }
  if (same != null) {
    val map = IdentityHashMap<Any, String>()
    fun Any.hc() = map.computeIfAbsent(this) { "item${map.size}" }
    val seq = opElement.all()
      .takeWhileInclusive { it != same }
      .mapIndexed { i, it ->
        "$i. ${it.opName}" +
        ", ${it.sessionKey?.run { "$opName ${source.hc()}" } ?: it.action?.hc()}" +
        ", ${it.operationName}"
      }
    throw IllegalStateException("Cyclic dependency:\n${seq.joinToString("\n")}")
  }
}

private fun checkRecursiveUpdateSession() {
  if (EDT.isCurrentThreadEdt() && SlowOperations.isInSection(SlowOperations.ACTION_UPDATE)) {
    LOG.error("Recursive update sessions are forbidden. Reuse existing AnActionEvent#getUpdateSession instead.")
  }
}

private suspend inline fun <R> retryOnAwaitSharedData(opElement: OpElement,
                                                      maxRetries: Int,
                                                      crossinline block: suspend () -> R): R = withContext(opElement) {
  repeat(maxRetries) {
    try {
      return@withContext block()
    }
    catch (ex: AwaitSharedData) {
      ex.job.join()
    }
  }
  throw RuntimeException("max $maxRetries retries reached")
}

private class AwaitSharedData(val job: Job, message: String) : RuntimeException(message) {
  override fun fillInStackTrace(): Throwable = this
}

class SkipOperation(operation: String) : RuntimeException(operation) {
  override fun fillInStackTrace(): Throwable = this
}

private data class SessionKey(val opName: String, val source: Any)

private class OpElement(
  val action: AnAction?,
  val opName: String,
  place: String,
  val sessionKey: SessionKey?,
  val forcedUpdateThread: ActionUpdateThread?,
  val parent: OpElement?,
) : AbstractCoroutineContextElement(OpElement), IntelliJContextElement {
  override fun produceChildElement(parentContext: CoroutineContext, isStructured: Boolean): IntelliJContextElement = this
  val operationName: String = Utils.operationName(action ?: sessionKey ?: ObjectUtils.NULL, opName, place)
  init {
    checkCyclicDependency(this)
  }

  fun all(): Sequence<OpElement> = generateSequence(this) { it.parent }

  fun isNested(): Boolean {
    val count = all()
      .takeWhile { it.opName != OP_expandActionGroup }
      .filter { it.opName != OP_sessionData }
      .count()
    return count > 1
  }

  override fun toString(): String = "Op($operationName)"

  companion object : CoroutineContext.Key<OpElement> {
    suspend fun next(action: AnAction?, opName: String, place: String, sessionKey: SessionKey?): OpElement {
      return next(currentCoroutineContext()[this], action, opName, place, sessionKey)
    }

    fun next(parent: OpElement?, action: AnAction?, opName: String, place: String, sessionKey: SessionKey?): OpElement {
      val forcedUpdateThread = (action as? ActionUpdateThreadAware.Recursive)?.actionUpdateThread
      return OpElement(action, opName, place, sessionKey, forcedUpdateThread, parent)
    }
  }
}