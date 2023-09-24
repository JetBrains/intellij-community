// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ActionUpdaterKt")
@file:OptIn(IntellijInternalApi::class)

package com.intellij.openapi.actionSystem.impl

import com.intellij.concurrency.ConcurrentCollectionFactory
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
import com.intellij.openapi.application.readActionUndispatched
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.checkCancelled
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.DumbService.Companion.getInstance
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.diagnostic.telemetry.helpers.computeWithSpan
import com.intellij.platform.diagnostic.telemetry.helpers.runWithSpan
import com.intellij.util.SlowOperations
import com.intellij.util.TimeoutUtil
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.FList
import com.intellij.util.ui.EDT
import com.intellij.util.use
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.awt.AWTEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
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

private val ourToolbarJobs: MutableSet<Job> = ConcurrentCollectionFactory.createConcurrentSet()
private val ourOtherJobs: MutableSet<Job> = ConcurrentCollectionFactory.createConcurrentSet()
private var ourInEDTActionOperationStack: FList<String> = FList.emptyList()

internal class ActionUpdater @JvmOverloads constructor(
  private val presentationFactory: PresentationFactory,
  private val dataContext: DataContext,
  private val place: String,
  private val contextMenuAction: Boolean,
  private val toolbarAction: Boolean,
  private val edtScope: CoroutineScope,
  private val eventTransform: ((AnActionEvent) -> AnActionEvent)? = null) {

  @Volatile private var bgtScope: CoroutineScope? = null
  private val application: Application = com.intellij.util.application
  private val project = CommonDataKeys.PROJECT.getData(dataContext)
  private val userDataHolder = UserDataHolderBase()
  private val updatedPresentations = ConcurrentHashMap<AnAction, Presentation>()
  private val groupChildren = ConcurrentHashMap<ActionGroup, List<AnAction>>()
  private val realUpdateStrategy = UpdateStrategy(
    update = { action ->
      retryOnAwaitSharedData {
        updateActionReal(action)
      }
    },
    getChildren = { group ->
      retryOnAwaitSharedData {
        callAction(group, Op.GetChildren) {
          doGetChildren(group, createActionEvent(updatedPresentations[group] ?: initialBgtPresentation(group)))
        }
      }
    })
  private val cheapStrategy = UpdateStrategy(
    update = { action -> presentationFactory.getPresentation(action) },
    getChildren = { group  -> doGetChildren(group, null) })

  private val testDelayMillis =
    if (ActionPlaces.ACTION_SEARCH == place || ActionPlaces.isShortcutPlace(place)) 0
    else Registry.intValue("actionSystem.update.actions.async.test.delay", 0)
  private val threadDumpService = ThreadDumpService.getInstance()

  private val preCacheSlowDataKeys: AtomicReference<Deferred<Unit>>? =
    if (Utils.isAsyncDataContext(dataContext) &&
        !Registry.`is`("actionSystem.update.actions.suppress.dataRules.on.edt")) AtomicReference()
    else null

  private var edtCallsCount: Int = 0 // used only in EDT
  private var edtWaitNanos: Long = 0 // used only in EDT

  private suspend fun updateActionReal(action: AnAction): Presentation? {
    // clone the presentation to avoid partially changing the cached one if the update is interrupted
    val presentation = presentationFactory.getPresentation(action).clone()
    // reset enabled/visible flags (actions are encouraged to always set them in `update`)
    presentation.setEnabledAndVisible(true)
    val success = callAction(action, Op.Update) {
      doUpdate(action, createActionEvent(presentation))
    }
    return if (success) presentation else null
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

  private suspend fun <T> callAction(action: AnAction, operation: Op, call: () -> T): T {
    val operationName = Utils.operationName(action, operation.name, place)
    return callAction(action, operationName, action.getActionUpdateThread(), call)
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
    checkCancelled()
    if (isEDT || !shallEDT) {
      return computeWithSpan(Utils.getTracer(true), operationName) { span: Span ->
        val adjustedCall = {
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
        if (isEDT) adjustedCall()
        else readActionUndispatched(adjustedCall)
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
        computeWithSpan(Utils.getTracer(true), operationName) { span: Span ->
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
        LOG.warn("$currentEDTWaitMillis ms to grab EDT for $operationName")
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

  /**
   * @return actions from the given and nested non-popup groups that are visible after updating
   */
  private suspend fun expandActionGroup(group: ActionGroup, hideDisabled: Boolean, strategy: UpdateStrategy): List<AnAction> {
    return removeUnnecessarySeparators(doExpandActionGroup(group, hideDisabled, strategy))
  }

  /**
   * @return actions from the given and nested non-popup groups that are visible after updating
   */
  suspend fun expandActionGroupWithTimeout(group: ActionGroup,
                                           hideDisabled: Boolean,
                                           timeoutMs: Int = -1): List<AnAction> = coroutineScope {
    bgtScope = null
    val adjustedMs = if (timeoutMs <= 0) Registry.intValue("actionSystem.update.timeout.ms") else timeoutMs
    val result = try {
      withTimeout(adjustedMs.toLong()) {
        expandActionGroup(group, hideDisabled, realUpdateStrategy)
      }
    }
    catch (_ : TimeoutCancellationException) {
      expandActionGroup(group, hideDisabled, cheapStrategy)
    }
    applyPresentationChanges()
    result
  }

  suspend fun expandActionGroup(group: ActionGroup, hideDisabled: Boolean): List<AnAction> = withContext(CoroutineName("doExpandActionGroup")) {
    bgtScope = this
    edtCallsCount = 0
    edtWaitNanos = 0
    val job = coroutineContext.job
    val targetPromises = if (toolbarAction) ourToolbarJobs else ourOtherJobs
    targetPromises.add(job)
    try {
      if (testDelayMillis > 0) {
        delay(testDelayMillis.toLong())
      }
      val result = expandActionGroup(group, hideDisabled, realUpdateStrategy)
      computeOnEdt {
        applyPresentationChanges()
      }
      result
    }
    finally {
      targetPromises.remove(job)
      val edtWaitMillis = TimeUnit.NANOSECONDS.toMillis(edtWaitNanos)
      if (edtCallsCount > 500 || edtWaitMillis > 3000) {
        LOG.warn(edtWaitMillis.toString() + " ms total to grab EDT " + edtCallsCount + " times to expand " +
                 Utils.operationName(group, null, place) + ". Use `ActionUpdateThread.BGT`.")
      }
    }
  }

  private suspend fun ensureSlowDataKeysPreCached(action: Any, targetOperationName: String) {
    if (preCacheSlowDataKeys == null) return
    val deferred = preCacheSlowDataKeys.get() ?: CompletableDeferred<Unit>().let { cur ->
      val existing = preCacheSlowDataKeys.compareAndExchange(null, cur)
      if (existing != null) return@let existing
      cur.completeWith(runCatching {
        readActionUndispatched {
          precacheSlowDataKeys(action, targetOperationName)
        }
      })
      cur
    }
    deferred.await()
  }

  private fun precacheSlowDataKeys(action: Any, targetOperationName: String) {
    val operationName = "precache-slow-data@$targetOperationName"
    val start = System.nanoTime()
    try {
      runWithSpan(Utils.getTracer(true), operationName) { span: Span ->
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

  private suspend fun doExpandActionGroup(group: ActionGroup, hideDisabled: Boolean, strategy: UpdateStrategy): List<AnAction> = coroutineScope {
    if (group is ActionGroupStub) {
      throw IllegalStateException("ActionGroupStub cannot be expanded")
    }
    checkCancelled()
    val presentation = update(group, strategy)
    if (presentation == null || !presentation.isVisible) {
      // don't process invisible groups
      return@coroutineScope emptyList()
    }

    val children = getGroupChildren(group, strategy)
    // parallel update execution can break some existing caching
    // the preferred way to do caching now is `updateSession.sharedData`
    val result = withContext(if (group is ActionUpdateThreadAware.Recursive) ForcedActionUpdateThreadElement(group.getActionUpdateThread())
                             else EmptyCoroutineContext) {
      children
        .map {
          async(Context.current().asContextElement()) {
            expandGroupChild(it, hideDisabled, strategy)
          }
        }
        .awaitAll()
        .flatten()
    }
    val actions = group.postProcessVisibleChildren(result, asUpdateSession(strategy))
    for (action in actions) {
      if (action is InlineActionsHolder) {
        for (inlineAction in action.getInlineActions()) {
          update(inlineAction, strategy)
        }
      }
    }
    actions
  }

  private suspend fun getGroupChildren(group: ActionGroup, strategy: UpdateStrategy): List<AnAction> {
    return groupChildren.getOrPut(group) {
      val children = try {
        strategy.getChildren(group)
      }
      catch (_: ComputeOnEDTSkipped) {
        emptyArray()
      }

      val nullIndex = (children as Array<*>).indexOf(null)
      if (nullIndex < 0) {
        children.asList()
      }
      else {
        LOG.error("action is null: i=$nullIndex group=$group group id=${ActionManager.getInstance().getId(group)}")
        @Suppress("UselessCallOnCollection")
        children.filterNotNull()
      }
    }
  }

  private suspend fun expandGroupChild(child: AnAction, hideDisabledBase: Boolean, strategy: UpdateStrategy): List<AnAction> {
    if (application.isDisposed()) {
      return emptyList()
    }
    val presentation = update(child, strategy)
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
    @Suppress("removal", "DEPRECATION")
    val hideEmpty = isPopup && !skipChecks && (presentation.isHideGroupIfEmpty || child.hideIfNoVisibleChildren())
    @Suppress("removal", "DEPRECATION")
    val disableEmpty = isPopup && !skipChecks && presentation.isDisableGroupIfEmpty && child.disableIfNoVisibleChildren()
    val checkChildren = isPopup && !skipChecks && (canBePerformed || hideDisabled || hideEmpty || disableEmpty)
    var hasEnabled = false
    var hasVisible = false
    if (checkChildren) {
      val childrenFlow = iterateGroupChildren(child, strategy)
      childrenFlow.take(100).takeWhile { action ->
        if (action is Separator) return@takeWhile true
        val p = update(action, strategy)
        if (p == null) return@takeWhile true
        hasVisible = hasVisible or p.isVisible
        hasEnabled = hasEnabled or p.isEnabled
        // stop early if all the required flags are collected
        return@takeWhile !(hasVisible && (hasEnabled || !hideDisabled))
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
      else -> doExpandActionGroup(child, hideDisabledChildren, strategy)
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

  private suspend fun <T> computeOnEdt(supplier: () -> T): T {
    return edtScope.async(Context.current().asContextElement()) {
      blockingContext {
        supplier()
      }
    }.await()
  }

  fun asUpdateSession(): UpdateSession {
    return asUpdateSession(realUpdateStrategy)
  }

  private fun asUpdateSession(strategy: UpdateStrategy): UpdateSession {
    return UpdateSessionImpl(this, strategy)
  }

  private suspend fun iterateGroupChildren(group: ActionGroup, strategy: UpdateStrategy): Flow<AnAction> {
    val isDumb = project != null && getInstance(project).isDumb
    val tree: suspend (AnAction) -> List<AnAction>? = tree@ { o ->
      if (o === group) return@tree null
      if (isDumb && !o.isDumbAware()) return@tree null
      if (o !is ActionGroup) {
        return@tree null
      }
      val presentation = update(o, strategy)
      if (presentation == null ||
          !presentation.isVisible ||
          presentation.isPopupGroup ||
          presentation.isPerformGroup) {
        null
      }
      else {
        getGroupChildren(o, strategy)
      }
    }
    val roots = getGroupChildren(group, strategy)
    return channelFlow {
      val set = HashSet<AnAction>()
      val queue = ArrayDeque(roots)
      while (!queue.isEmpty()) {
        val first = queue.removeFirst()
        if (!set.add(first)) continue
        val children = tree(first)
        if (children.isNullOrEmpty()) send(first)
        else children.reversed().forEach(queue::addFirst)
      }
    }
      .buffer(1)
      .filter { !isDumb || it.isDumbAware()  }
  }

  private suspend fun update(action: AnAction, strategy: UpdateStrategy): Presentation? {
    val cached = updatedPresentations[action]
    if (cached != null) {
      return cached
    }
    try {
      val presentation = strategy.update(action)
      if (presentation != null) {
        updatedPresentations[action] = presentation
        return presentation
      }
    }
    catch (_: ComputeOnEDTSkipped) {
      return null
    }
    return null
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  fun <T> computeSharedData(key: Key<T>, supplier: Supplier<out T>): T {
    @Suppress("UNCHECKED_CAST")
    key as Key<Deferred<T>> // reuse the key, ignore the generics
    val deferred = userDataHolder.getUserData(key) ?: CompletableDeferred<T>().let { cur ->
      val existing = userDataHolder.putUserDataIfAbsent(key, cur)
      if (existing != cur) return@let existing
      bgtScope?.async(Context.current().asContextElement()) {
        runWithSpan(Utils.getTracer(true), "Key($key)#sharedData@$place") {
          cur.completeWith(runCatching {
            readActionUndispatched {
              supplier.get()
            }
          })
        }
      } ?: cur.completeWith(runCatching { supplier.get() }) // GotoAction
      cur
    }
    if (deferred.isCompleted) {
      return deferred.getCompleted()
    }
    throw AwaitSharedData(deferred)
  }

  companion object {
    var isNoRulesInEDTSection: Boolean = false
      private set

    fun currentInEDTOperationName(): String? = ourInEDTActionOperationStack.head

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

  private class UpdateSessionImpl(val updater: ActionUpdater, val strategy: UpdateStrategy) : UpdateSession {
    override fun expandedChildren(actionGroup: ActionGroup): Iterable<AnAction> = runBlockingForActionExpand {
      updater.iterateGroupChildren(actionGroup, strategy).toCollection(ArrayList())
    }

    override fun children(actionGroup: ActionGroup): List<AnAction> = runBlockingForActionExpand {
      updater.getGroupChildren(actionGroup, strategy)
    }

    override fun presentation(action: AnAction): Presentation = runBlockingForActionExpand {
      updater.update(action, strategy) ?: updater.initialBgtPresentation(action)
    }

    override fun <T : Any> sharedData(key: Key<T>, supplier: Supplier<out T>): T {
      return updater.computeSharedData(key, supplier)
    }

    override fun <T> compute(action: Any,
                             operationName: String,
                             updateThread: ActionUpdateThread,
                             supplier: Supplier<out T>): T = runBlockingForActionExpand {
      val operationNameFull = Utils.operationName(action, operationName, updater.place)
      updater.callAction(action = action, operationName = operationNameFull, updateThreadOrig = updateThread) { supplier.get() }
    }
  }
}

private enum class Op { Update, GetChildren }

private data class UpdateStrategy(@JvmField val update: suspend (AnAction) -> Presentation?,
                                  @JvmField val getChildren: suspend (ActionGroup) -> Array<AnAction>)

private class ComputeOnEDTSkipped : RuntimeException() {
  override fun fillInStackTrace(): Throwable = this
}

internal fun cancelAllUpdates(reason: String) {
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

internal fun waitForAllUpdatesToFinish() {
  runBlockingForActionExpand {
    (ourToolbarJobs + ourOtherJobs).joinAll()
  }
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

private fun handleException(action: AnAction, op: Op, event: AnActionEvent?, ex: Throwable) {
  if (ex is ProcessCanceledException) throw ex
  if (ex is AwaitSharedData) throw ex
  if (ex is ComputeOnEDTSkipped) throw ex
  val id = ActionManager.getInstance().getId(action)
  val place = event?.place
  val text = event?.presentation?.text
  val message = Utils.operationName(action, op.name, place) +
                (if (id != null) ", actionId=$id" else "") +
                if (StringUtil.isNotEmpty(text)) ", text='$text'" else ""
  LOG.error(message, ex)
}

// returns false if exception was thrown and handled
private fun doUpdate(action: AnAction, e: AnActionEvent): Boolean = try {
  if (application.isDisposed()) false
  else !ActionUtil.performDumbAwareUpdate(action, e, false)
}
catch (ex: Throwable) {
  handleException(action, Op.Update, e, ex)
  false
}

private fun doGetChildren(group: ActionGroup, e: AnActionEvent?): Array<AnAction> {
  try {
    return if (application.isDisposed()) AnAction.EMPTY_ARRAY
    else group.getChildren(e)
  }
  catch (ex: Throwable) {
    handleException(group, Op.GetChildren, e, ex)
    return AnAction.EMPTY_ARRAY
  }
}

private suspend inline fun <R> retryOnAwaitSharedData(block: suspend () -> R): R {
  while (true) {
    try {
      return block()
    }
    catch (ex: AwaitSharedData) {
      ex.job.join()
    }
  }
}

private class AwaitSharedData(val job: Job): RuntimeException()

private class ForcedActionUpdateThreadElement(val updateThread: ActionUpdateThread)
  : AbstractCoroutineContextElement(ForcedActionUpdateThreadElement) {
  companion object : CoroutineContext.Key<ForcedActionUpdateThreadElement>
}