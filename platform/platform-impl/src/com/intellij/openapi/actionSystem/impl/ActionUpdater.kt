// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ActionUpdaterKt")
@file:OptIn(IntellijInternalApi::class)

package com.intellij.openapi.actionSystem.impl

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.concurrency.currentThreadContext
import com.intellij.diagnostic.PluginException
import com.intellij.diagnostic.ThreadDumpService
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.ProhibitAWTEvents
import com.intellij.internal.DebugAttachDetector
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.ex.InlineActionsHolder
import com.intellij.openapi.actionSystem.impl.ActionMenu.Companion.ALWAYS_VISIBLE
import com.intellij.openapi.actionSystem.impl.ActionMenu.Companion.SUPPRESS_SUBMENU
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.CeProcessCanceledException
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScopeBlocking
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.SlowOperations
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.FList
import com.intellij.util.ui.EDT
import com.intellij.util.use
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import java.awt.AWTEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.lang.IllegalStateException
import java.lang.Integer.max
import java.lang.RuntimeException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import javax.swing.JComponent
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private val LOG = logger<ActionUpdater>()

@JvmField
internal val SUPPRESS_SUBMENU_IMPL: Key<Boolean> = Key.create("SUPPRESS_SUBMENU_IMPL")

private const val OLD_EDT_MSG_SUFFIX = ". Revise AnAction.getActionUpdateThread property"

private const val OP_expandActionGroup = "expandedChildren"
private const val OP_groupChildren = "children"
private const val OP_actionPresentation = "presentation"
private const val OP_groupPostProcess = "postProcessChildren"

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
  private val sessionData = ConcurrentHashMap<Pair<String, Any?>, Deferred<*>>()
  private val updatedPresentations = ConcurrentHashMap<AnAction, Presentation>()
  private val groupChildren = ConcurrentHashMap<ActionGroup, List<AnAction>>()

  private val testDelayMillis =
    if (ActionPlaces.ACTION_SEARCH == place || ActionPlaces.isShortcutPlace(place)) 0
    else Registry.intValue("actionSystem.update.actions.async.test.delay", 0)
  private val threadDumpService = ThreadDumpService.getInstance()

  private val preCacheSlowDataKeys = !Registry.`is`("actionSystem.update.actions.suppress.dataRules.on.edt")
  private val maxAwaitSharedDataRetries = max(1, Registry.intValue("actionSystem.update.actions.max.await.retries", 500))

  private var edtCallsCount: Int = 0 // used only in EDT
  private var edtWaitNanos: Long = 0 // used only in EDT

  init {
    if (EDT.isCurrentThreadEdt() && SlowOperations.isInSection(SlowOperations.ACTION_UPDATE)) {
      reportRecursiveUpdateSession()
    }
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
      orig.copyFrom(copy, customComponent, true)
      if (customComponent != null && orig.isVisible) {
        (action as CustomComponentAction).updateCustomComponent(customComponent, orig)
      }
    }
  }

  private suspend fun <T> callAction(action: Any,
                                     operationName: String,
                                     updateThreadOrig: ActionUpdateThread,
                                     call: () -> T): T {
    val forcedUpdateThread = currentCoroutineContext()[ForcedActionUpdateThreadElement]?.updateThread
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
    if (PopupMenuPreloader.isToSkipComputeOnEDT(place)) {
      throw ComputeOnEDTSkipped()
    }
    @Suppress("removal", "DEPRECATION")
    if (updateThread == ActionUpdateThread.OLD_EDT) {
      ensureSlowDataKeysPreCached(action, operationName)
    }
    return computeOnEdt(action, operationName, updateThread == ActionUpdateThread.EDT) {
      call()
    }
  }

  private suspend fun <T> computeOnEdt(action: Any, operationName: String, noRulesInEDT: Boolean, call: () -> T): T {
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
        Utils.getTracer(true).spanBuilder(operationName).useWithScopeBlocking { span: Span ->
          val prevStack = ourInEDTActionOperationStack
          val prevNoRules = isNoRulesInEDTSection
          var traceCookie: ThreadDumpService.Cookie? = null
          try {
            Triple({ ProhibitAWTEvents.start(operationName) },
                   { ProgressIndicatorUtils.prohibitWriteActionsInside(application) },
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
        val throwable: Throwable = PluginException.createByClass(
          elapsedReport(currentEDTPerformMillis, true, operationName) + OLD_EDT_MSG_SUFFIX, null, action.javaClass)
        val edtTraces = edtTraces
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
    }
  }

  suspend fun <R : Any?> runUpdateSession(coroutineContext: CoroutineContext, block: suspend CoroutineScope.() -> R): R =
    withContext(coroutineContext) {
      val childScope = childScope()
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
  suspend fun expandActionGroup(group: ActionGroup, hideDisabled: Boolean): List<AnAction> {
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
        removeUnnecessarySeparators(doExpandActionGroup(group, hideDisabled))
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
                 Utils.operationName(group, null, place) + ". Use `ActionUpdateThread.BGT`.")
      }
    }
  }

  private suspend fun ensureSlowDataKeysPreCached(action: Any, targetOperationName: String) {
    if (!preCacheSlowDataKeys) return
    getSessionDataDeferred(Pair("precache-slow-data@$targetOperationName", null)) {
      readActionUndispatchedForActionExpand {
        precacheSlowDataKeys(action, targetOperationName)
      }
    }.await()
  }

  private fun precacheSlowDataKeys(action: Any, targetOperationName: String) {
    val operationName = "precache-slow-data@$targetOperationName"
    val start = System.nanoTime()
    try {
      for (key in DataKey.allKeys()) {
        try {
          dataContext.getData(key)
        }
        catch (ex: ProcessCanceledException) {
          throw ex
        }
        catch (ex: Throwable) {
          LOG.error(ex)
        }
      }
    }
    finally {
      logTimeProblemForPreCached(action, operationName, TimeoutUtil.getDurationMillis(start))
    }
  }

  private fun logTimeProblemForPreCached(action: Any, operationName: String, elapsed: Long) {
    if (elapsed > 300 && ActionPlaces.isShortcutPlace(place)) {
      LOG.error(PluginException.createByClass(elapsedReport(elapsed, false, operationName) + OLD_EDT_MSG_SUFFIX, null, action.javaClass))
    }
    else if (elapsed > 3000) {
      LOG.warn(elapsedReport(elapsed, false, operationName))
    }
    else if (elapsed > 500 && LOG.isDebugEnabled()) {
      LOG.debug(elapsedReport(elapsed, false, operationName))
    }
  }

  private suspend fun doExpandActionGroup(group: ActionGroup, hideDisabled: Boolean): List<AnAction> = coroutineScope {
    if (group is ActionGroupStub) {
      throw IllegalStateException("ActionGroupStub cannot be expanded")
    }
    val presentation = updateAction(group)
    if (presentation == null || !presentation.isVisible) {
      // don't process invisible groups
      return@coroutineScope emptyList()
    }

    val children = getGroupChildren(group)
    // parallel update execution can break some existing caching
    // the preferred way to do caching now is `updateSession.sharedData`
    val updateContext = ForcedActionUpdateThreadElement.forGroup(group)
    val expandResult = withContext(updateContext) {
      children
        .map {
          async {
            expandGroupChild(it, hideDisabled)
          }
        }
        .awaitAll()
        .flatten()
    }
    val result = postProcessGroupChildren(group, expandResult)
    result
      .mapNotNull {
        updatedPresentations[it]?.getClientProperty(ActionUtil.INLINE_ACTIONS)
        ?: (it as? InlineActionsHolder)?.inlineActions
      }
      .flatten()
      .map {
        async(updateContext) {
          updateAction(it)
        }
      }
      .toList()
      .awaitAll()
    result
  }

  private suspend fun postProcessGroupChildren(group: ActionGroup, result: List<AnAction>): List<AnAction> {
    if (isDefaultImplementationRecursively(OP_groupPostProcess, group)) {
      return result
    }
    val operationName = Utils.operationName(group, OP_groupPostProcess, place)
    try {
      val updateSession = asUpdateSession()
      return retryOnAwaitSharedData(operationName, maxAwaitSharedDataRetries) {
        blockingContext { // no data-context hence no RA, just blockingContext
          val spanBuilder = Utils.getTracer(true).spanBuilder(operationName)
          spanBuilder.useWithScopeBlocking {
            group.postProcessVisibleChildren(result, updateSession)
          }
        }
      }
    }
    catch (ex: Throwable) {
      handleException(group, operationName, null, ex)
      return result
    }
  }

  /** same event/retry/intercept/cache logic as in [updateAction] */
  private suspend fun getGroupChildren(group: ActionGroup): List<AnAction> {
    val cached = groupChildren[group]
    if (cached != null) {
      return cached
    }
    val operationName = Utils.operationName(group, OP_groupChildren, place)
    // use initial presentation if there's no updated presentation (?)
    val event = createActionEvent(updatedPresentations[group] ?: initialBgtPresentation(group))
    val children = try {
      retryOnAwaitSharedData(operationName, maxAwaitSharedDataRetries) {
        ActionUpdaterInterceptor.getGroupChildren(group, event) {
          callAction(group, operationName, group.actionUpdateThread) {
            group.getChildren(event)
          }.let {
            ensureNotNullChildren(it, group, place)
          }
        }
      }.asList()
    }
    catch (ex: Throwable) {
      handleException(group, operationName, event, ex)
      return emptyList()
    }
    groupChildren[group] = children
    return children
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
    val alwaysVisible = child is AlwaysVisibleActionGroup || presentation.getClientProperty(ALWAYS_VISIBLE) == true
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
    val hideDisabledChildren = (hideDisabledBase || child is CompactActionGroup) && !alwaysVisible
    return when {
      !hasEnabled && hideDisabled || !hasVisible && hideEmpty -> when {
        canBePerformed -> listOf(child)
        else -> emptyList()
      }
      isPopup -> when {
        hideDisabledChildren && child !is CompactActionGroup -> listOf(ActionGroupUtil.forceHideDisabledChildren(child))
        else -> listOf(child)
      }
      else -> doExpandActionGroup(child, hideDisabledChildren)
    }
  }

  private fun initialBgtPresentation(action: AnAction): Presentation {
    return presentationFactory.getPresentation(action).clone()
  }

  private fun createActionEvent(presentation: Presentation): AnActionEvent {
    val event = AnActionEvent(
      null, dataContext, place, presentation,
      ActionManager.getInstance(), 0, contextMenuAction, toolbarAction).let {
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
      blockingContext {
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

  private suspend fun iterateGroupChildren(group: ActionGroup): Flow<AnAction> = flow {
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
    val operationName = Utils.operationName(action, OP_actionPresentation, place)
    // reset enabled/visible flags (actions are encouraged to always set them in `update`)
    presentation.setEnabledAndVisible(true)
    val event = createActionEvent(presentation)
    val success = try {
      retryOnAwaitSharedData(operationName, maxAwaitSharedDataRetries) {
        ActionUpdaterInterceptor.updateAction(action, event) {
          if (isDefaultImplementationRecursively(OP_actionPresentation, action)) {
            return@updateAction true
          }
          callAction(action, operationName, action.actionUpdateThread) {
            !ActionUtil.performDumbAwareUpdate(action, event, false)
          }
        }
      }
    }
    catch (ex: Throwable) {
      handleException(action, operationName, event, ex)
      return null
    }
    if (success) {
      updatedPresentations[action] = presentation
      return presentation
    }
    return null
  }

  @Suppress("UNCHECKED_CAST")
  fun <T : Any?> getSessionDataDeferred(key: Pair<String, Any?>, supplier: suspend () -> T): Deferred<T> {
    sessionData[key]?.let { return it as Deferred<T> }
    val bgtScope = bgtScope
    return if (bgtScope != null) {
      sessionData.computeIfAbsent(key) {
        bgtScope.async(currentThreadContext().minusKey(Job) +
                       CoroutineName("getSessionDataDeferred#${key.first} ($place)" )) {
          val spanBuilder = Utils.getTracer(true).spanBuilder("${key.first}@$place")
          spanBuilder.useWithScope(EmptyCoroutineContext) {
            supplier()
          }
        }
      } as Deferred<T>
    }
    else {
      // not a good branch to be in, seek ways to get bgtScope
      CompletableDeferred<T>().apply {
        completeWith(runCatching {
          runBlockingForActionExpand {
            supplier()
          }
        })
      }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  fun <T: Any?> computeSessionDataOrThrow(key: Pair<String, Any?>, supplier: suspend () -> T): T {
    val deferred = getSessionDataDeferred(key, supplier)
    if (deferred.isCompleted) {
      try {
        return deferred.getCompleted()
      }
      catch (ex: CancellationException) {
        throw CeProcessCanceledException(ex)
      }
    }
    val operationName = "session.${key.first} at ${currentThreadContext()[OperationName]?.name ?: "unknown"}"
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
    override fun expandedChildren(actionGroup: ActionGroup): Iterable<AnAction> =
      updater.computeSessionDataOrThrow(Pair(OP_expandActionGroup, actionGroup)) {
        updater.iterateGroupChildren(actionGroup).toCollection(ArrayList())
      }

    override fun children(actionGroup: ActionGroup): List<AnAction> =
      updater.groupChildren[actionGroup] ?: updater.computeSessionDataOrThrow(Pair(OP_groupChildren, actionGroup)) {
        updater.getGroupChildren(actionGroup)
      }

    override fun presentation(action: AnAction): Presentation =
      updater.updatedPresentations[action] ?: updater.computeSessionDataOrThrow(Pair(OP_actionPresentation, action)) {
        updater.updateAction(action) ?: updater.initialBgtPresentation(action)
      }

    override fun <T : Any> sharedData(key: Key<T>, supplier: Supplier<out T>): T =
      updater.computeSessionDataOrThrow(Pair(key.toString(), key)) {
        readActionUndispatchedForActionExpand {
          supplier.get()
        }
      }

    override fun <T> compute(action: Any,
                             op: String,
                             updateThread: ActionUpdateThread,
                             supplier: Supplier<out T>): T = runBlockingForActionExpand {
      val operationName = Utils.operationName(action, op, updater.place)
      withContext(OperationName(operationName) + RecursionElement.next()) {
        updater.callAction(action, operationName, updateThread) { supplier.get() }
      }
    }

    override suspend fun presentationSuspend(action: AnAction): Presentation {
      return updater.updateAction(action) ?: updater.initialBgtPresentation(action)
    }

    override suspend fun childrenSuspend(actionGroup: ActionGroup): List<AnAction> {
      return updater.getGroupChildren(actionGroup)
    }

    override suspend fun expandSuspend(group: ActionGroup): List<AnAction> {
      return updater.expandActionGroup(group, group is CompactActionGroup)
    }

    override fun <T: Any?> sharedDataSuspend(key: Key<T>, supplier: suspend () -> T): T =
      updater.computeSessionDataOrThrow(Pair(key.toString(), key)) {
        supplier()
      }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun visitCaches(visitor: (AnAction, String, Any) -> Unit) {
      updater.updatedPresentations.forEach { action, presentation -> visitor(action, OP_actionPresentation, presentation) }
      updater.groupChildren.forEach { action, children -> visitor(action, OP_groupChildren, children) }
      updater.sessionData.forEach { pair, deferred ->
        if (pair.first == OP_expandActionGroup) visitor(pair.second as ActionGroup, pair.first, deferred.getCompleted()!!) }
    }

    override fun dropCaches(predicate: (AnAction) -> Boolean) {
      // if reused, clear temporary deferred caches from previous `expandActionGroup` calls
      // some are in completed-with-exception state (SkipOperation), so re-calling will fail
      // 1. valid presentation and children are already cached in other maps
      // 2. expanded children must not be cached in remote scenarios anyway
      updater.sessionData.keys.removeIf { (op, _) ->
        op == OP_actionPresentation || op == OP_groupChildren || op == OP_expandActionGroup
      }
      // clear caches for selected actions
      updater.updatedPresentations.keys.removeIf(predicate)
      updater.groupChildren.keys.removeIf(predicate)
    }
  }
}

private fun reportRecursiveUpdateSession() {
  LOG.error("Recursive update sessions are forbidden. Reuse existing AnActionEvent#getUpdateSession instead.")
}

private class ComputeOnEDTSkipped : RuntimeException() {
  override fun fillInStackTrace(): Throwable = this
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

private suspend fun handleException(action: AnAction, operationName: String, event: AnActionEvent?, ex: Throwable) {
  if (ex is CancellationException) throw ex
  if (ex is AwaitSharedData) throw ex
  if (ex is SkipOperation) {
    if (RecursionElement.isNested()) throw ex
    else return
  }
  if (ex is ComputeOnEDTSkipped) return
  val id = serviceAsync<ActionManager>().getId(action)
  val text = event?.presentation?.text
  val message = operationName +
                (if (id != null) ", actionId=$id" else "") +
                if (StringUtil.isNotEmpty(text)) ", text='$text'" else ""
  LOG.error(message, ex)
}

private fun ensureNotNullChildren(children: Array<AnAction?>, group: ActionGroup, place: String): Array<AnAction> {
  val nullIndex = (children as Array<*>).indexOf(null)
  return if (nullIndex < 0) {
    @Suppress("UNCHECKED_CAST")
    children as Array<AnAction>
  }
  else {
    LOG.error("$nullIndex item is null in ${Utils.operationName(group, OP_groupChildren, place)}")
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

private suspend inline fun <R> retryOnAwaitSharedData(operationName: String,
                                                      maxRetries: Int,
                                                      crossinline block: suspend () -> R): R = withContext(
  OperationName(operationName) + RecursionElement.next()) {
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

private class ForcedActionUpdateThreadElement(val updateThread: ActionUpdateThread)
  : AbstractCoroutineContextElement(ForcedActionUpdateThreadElement) {
  companion object : CoroutineContext.Key<ForcedActionUpdateThreadElement> {
    fun forGroup(group: ActionGroup) =
      if (group is ActionUpdateThreadAware.Recursive) ForcedActionUpdateThreadElement(group.getActionUpdateThread())
      else EmptyCoroutineContext
  }
}

class SkipOperation(operation: String) : RuntimeException(operation) {
  override fun fillInStackTrace(): Throwable = this
}

private class RecursionElement(val level: Int)
  : AbstractCoroutineContextElement(RecursionElement) {
  override fun toString(): String = "Recursion(${level})"

  companion object : CoroutineContext.Key<RecursionElement> {
    suspend fun next() = RecursionElement(level() + 1)
    suspend fun level() = currentCoroutineContext()[this]?.level ?: 0
    suspend fun isNested() = level() > 0
  }
}

private class OperationName(val name: String)
  : AbstractCoroutineContextElement(OperationName) {
  override fun toString(): String = name
  companion object : CoroutineContext.Key<OperationName>
}