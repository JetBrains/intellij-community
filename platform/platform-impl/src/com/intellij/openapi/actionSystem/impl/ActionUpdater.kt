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
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.CeProcessCanceledException
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.checkCancelled
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.DumbService.Companion.getInstance
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.diagnostic.telemetry.helpers.computeWithSpan
import com.intellij.util.SlowOperations
import com.intellij.util.TimeoutUtil
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.FList
import com.intellij.util.io.computeDetached
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
  val place: String,
  private val contextMenuAction: Boolean,
  private val toolbarAction: Boolean,
  private val edtDispatcher: CoroutineDispatcher,
  private val eventTransform: ((AnActionEvent) -> AnActionEvent)? = null) {

  @Volatile private var bgtScope: CoroutineScope? = null

  private val application: Application = com.intellij.util.application
  private val project = CommonDataKeys.PROJECT.getData(dataContext)
  private val sessionData = ConcurrentHashMap<Pair<String, Any?>, Deferred<*>>()
  private val updatedPresentations = ConcurrentHashMap<AnAction, Presentation>()
  private val groupChildren = ConcurrentHashMap<ActionGroup, List<AnAction>>()

  private val testDelayMillis =
    if (ActionPlaces.ACTION_SEARCH == place || ActionPlaces.isShortcutPlace(place)) 0
    else Registry.intValue("actionSystem.update.actions.async.test.delay", 0)
  private val threadDumpService = ThreadDumpService.getInstance()

  private val preCacheSlowDataKeys = !Registry.`is`("actionSystem.update.actions.suppress.dataRules.on.edt")

  private var edtCallsCount: Int = 0 // used only in EDT
  private var edtWaitNanos: Long = 0 // used only in EDT

  init {
    if (EDT.isCurrentThreadEdt() && SlowOperations.isInSection(SlowOperations.ACTION_UPDATE)) {
      reportRecursiveUpdateSession()
    }
  }

  private suspend fun updateActionReal(action: AnAction): Presentation? {
    // clone the presentation to avoid partially changing the cached one if the update is interrupted
    val presentation = presentationFactory.getPresentation(action).clone()
    // reset enabled/visible flags (actions are encouraged to always set them in `update`)
    presentation.setEnabledAndVisible(true)
    val event = createActionEvent(presentation)
    val success = ActionUpdaterInterceptor.updateAction(action, event) {
      callAction(action, Op.Update) {
        doUpdate(action, event)
      }
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
        readActionUndispatchedForActionExpand(adjustedCall)
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

  suspend fun <R : Any?> runUpdateSession(coroutineContext: CoroutineContext, block: suspend CoroutineScope.() -> R): R =
    withContext(coroutineContext) {
      bgtScope = this
      try {
        block()
      }
      finally {
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
      val result = removeUnnecessarySeparators(doExpandActionGroup(group, hideDisabled))
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
    checkCancelled()
    val presentation = update(group)
    if (presentation == null || !presentation.isVisible) {
      // don't process invisible groups
      return@coroutineScope emptyList()
    }

    val children = getGroupChildren(group)
    // parallel update execution can break some existing caching
    // the preferred way to do caching now is `updateSession.sharedData`
    val result = withContext(if (group is ActionUpdateThreadAware.Recursive) ForcedActionUpdateThreadElement(group.getActionUpdateThread())
                             else EmptyCoroutineContext) {
      children
        .map {
          async {
            expandGroupChild(it, hideDisabled)
          }
        }
        .awaitAll()
        .flatten()
    }
    val actions = group.postProcessVisibleChildren(result, asUpdateSession())
    for (action in actions) {
      if (action is InlineActionsHolder) {
        for (inlineAction in action.getInlineActions()) {
          update(inlineAction)
        }
      }
    }
    actions
  }

  private suspend fun getGroupChildren(group: ActionGroup): List<AnAction> {
    return groupChildren.getOrPut(group) {
      val event = createActionEvent(updatedPresentations[group] ?: initialBgtPresentation(group))
      val children = try {
        retryOnAwaitSharedData {
          ActionUpdaterInterceptor.getGroupChildren(group, event) {
            callAction(group, Op.GetChildren) {
              doGetChildren(group, event)
            }
          }
        }
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

  private suspend fun expandGroupChild(child: AnAction, hideDisabledBase: Boolean): List<AnAction> {
    if (application.isDisposed()) {
      return emptyList()
    }
    val presentation = update(child)
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
      val childrenFlow = iterateGroupChildren(child)
      childrenFlow.take(100).filter { it !is Separator }.takeWhile { action ->
        val p = update(action)
        hasVisible = hasVisible or (p?.isVisible == true)
        hasEnabled = hasEnabled or (p?.isEnabled == true)
        // stop early if all the required flags are collected
        val result = !(hasVisible && (hasEnabled || !hideDisabled))
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
    // We need the block below to escape the current scope to let runBlocking in UpdateSession
    // free while the EDT block is still waiting to be cancelled in EDT queue
    return computeDetached {
      withContext(edtDispatcher) {
        blockingContext {
          supplier()
        }
      }
    }
  }

  fun asUpdateSession(): UpdateSession {
    return UpdateSessionImpl(this)
  }

  private suspend fun iterateGroupChildren(group: ActionGroup): Flow<AnAction> {
    val isDumb = project != null && getInstance(project).isDumb
    val tree: suspend (AnAction) -> List<AnAction>? = tree@ { o ->
      if (o === group) return@tree null
      if (isDumb && !o.isDumbAware()) return@tree null
      // in all clients the next call is `update`
      // let's update both actions and groups
      val presentation = update(o)
      if (o !is ActionGroup) {
        return@tree null
      }
      if (presentation == null ||
          !presentation.isVisible ||
          presentation.isPopupGroup ||
          presentation.isPerformGroup) {
        null
      }
      else {
        getGroupChildren(o)
      }
    }
    val roots = getGroupChildren(group)
    return flow {
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
      .filter { !isDumb || it.isDumbAware()  }
  }

  suspend fun presentation(action: AnAction): Presentation {
    return update(action) ?: initialBgtPresentation(action)
  }

  private suspend fun update(action: AnAction): Presentation? {
    val cached = updatedPresentations[action]
    if (cached != null) {
      return cached
    }
    try {
      val presentation = retryOnAwaitSharedData {
        updateActionReal(action)
      }
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

  @Suppress("UNCHECKED_CAST")
  fun <T : Any?> getSessionDataDeferred(key: Pair<String, Any?>, supplier: suspend () -> T): Deferred<T> {
    sessionData[key]?.let { return it as Deferred<T> }
    val bgtScope = bgtScope
    return if (bgtScope != null) {
      sessionData.computeIfAbsent(key) {
        bgtScope.async(Context.current().asContextElement()) {
          computeWithSpan(Utils.getTracer(true), "${key.first}@$place") {
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
    throw AwaitSharedData(deferred, key.first)
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

  private class UpdateSessionImpl(val updater: ActionUpdater) : UpdateSession {
    override fun expandedChildren(actionGroup: ActionGroup): Iterable<AnAction> =
      updater.computeSessionDataOrThrow(Pair("expandedChildren", actionGroup)) {
        updater.iterateGroupChildren(actionGroup).toCollection(ArrayList())
      }

    override fun children(actionGroup: ActionGroup): List<AnAction> =
      updater.groupChildren[actionGroup] ?: updater.computeSessionDataOrThrow(Pair("children", actionGroup)) {
        updater.getGroupChildren(actionGroup)
      }

    override fun presentation(action: AnAction): Presentation =
      updater.updatedPresentations[action] ?: updater.computeSessionDataOrThrow(Pair("presentation", action)) {
        updater.update(action) ?: updater.initialBgtPresentation(action)
      }

    override fun <T : Any> sharedData(key: Key<T>, supplier: Supplier<out T>): T =
      updater.computeSessionDataOrThrow(Pair(key.toString(), key)) {
        readActionUndispatchedForActionExpand {
          supplier.get()
        }
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

private fun reportRecursiveUpdateSession() {
  LOG.error("Recursive update sessions are forbidden. Reuse existing AnActionEvent#getUpdateSession instead.")
}

private enum class Op { Update, GetChildren }

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

private class AwaitSharedData(val job: Job, val key: String): RuntimeException(key) {
  override fun fillInStackTrace(): Throwable = this
}

private class ForcedActionUpdateThreadElement(val updateThread: ActionUpdateThread)
  : AbstractCoroutineContextElement(ForcedActionUpdateThreadElement) {
  companion object : CoroutineContext.Key<ForcedActionUpdateThreadElement>
}