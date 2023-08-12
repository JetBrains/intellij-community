// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ActionUpdaterKt")

package com.intellij.openapi.actionSystem.impl

import com.intellij.codeWithMe.ClientId.Companion.current
import com.intellij.codeWithMe.ClientId.Companion.withClientId
import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.concurrency.SensitiveProgressWrapper
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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.client.ClientSessionsManager.Companion.getAppSession
import com.intellij.openapi.client.ClientSessionsManager.Companion.getProjectSession
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.progress.util.ProgressWrapper
import com.intellij.openapi.project.DumbService.Companion.getInstance
import com.intellij.openapi.util.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.diagnostic.telemetry.helpers.computeWithSpan
import com.intellij.platform.diagnostic.telemetry.helpers.runWithSpan
import com.intellij.util.SlowOperations
import com.intellij.util.TimeoutUtil
import com.intellij.util.containers.*
import com.intellij.util.ui.EDT
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.CancellablePromise
import java.awt.AWTEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.lang.Runnable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import java.util.function.Supplier
import javax.swing.JComponent
import kotlin.time.Duration.Companion.minutes

private val LOG = logger<ActionUpdater>()

@JvmField
internal val SUPPRESS_SUBMENU_IMPL: Key<Boolean> = Key.create("SUPPRESS_SUBMENU_IMPL")
private const val NESTED_WA_REASON_PREFIX = "nested write-action requested by "
private const val OLD_EDT_MSG_SUFFIX = ". Revise AnAction.getActionUpdateThread property"

private val ourPromises: MutableSet<CancellablePromise<*>> = ConcurrentCollectionFactory.createConcurrentSet()
private val ourToolbarPromises: MutableSet<CancellablePromise<*>> = ConcurrentCollectionFactory.createConcurrentSet()
private var ourInEDTActionOperationStack: FList<String> = FList.emptyList()

private class MyExecutor(name: String, private val coroutineScope: CoroutineScope) : Executor {
  private val coroutineName = CoroutineName(name)

  override fun execute(command: Runnable) {
    val name = if (command is NamedRunnable) CoroutineName("${coroutineName.name} ($command)") else coroutineName
    coroutineScope.launch(name) {
      blockingContext {
        command.run()
      }
    }
  }
}

internal val beforePerformedExecutor: Executor
  get() = service<ActionUpdateExecutorManager>().beforePerformedExecutor

private val fastTrackToolbarsCount = AtomicInteger()

@Service
private class ActionUpdateExecutorManager(private val coroutineScope: CoroutineScope) {
  private val fastTrackExecutor: Executor = MyExecutor("Action Updater (Fast)", coroutineScope)

  @JvmField
  val beforePerformedExecutor: Executor = MyExecutor("Action Updater (Exclusive)", coroutineScope)

  private val commonExecutor: Executor = object : Executor {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val commonExecutorCoroutineContext = Dispatchers.Default.limitedParallelism(2) + CoroutineName("Action Updater (Common)")

    override fun execute(it: Runnable) {
      coroutineScope.launch(commonExecutorCoroutineContext) {
        blockingContext {
          it.run()
        }
      }
    }
  }

  fun getExecutor(useFastTrack: Boolean): Executor = if (useFastTrack) fastTrackExecutor else commonExecutor

  fun waitForAllUpdatesToFinish() {
    val jobs = coroutineScope.coroutineContext.job.children.toList()
    if (jobs.isEmpty()) {
      return
    }

    coroutineScope.launch {
      withTimeout(1.minutes) {
        jobs.joinAll()
      }
    }.asCompletableFuture().join()
  }
}

internal class ActionUpdater @JvmOverloads constructor(
  private val presentationFactory: PresentationFactory,
  private val dataContext: DataContext,
  private val place: String,
  private val contextMenuAction: Boolean,
  private val toolbarAction: Boolean,
  private val edtExecutor: Executor? = null,
  private val eventTransform: ((AnActionEvent) -> AnActionEvent)? = null) {

  private val project = CommonDataKeys.PROJECT.getData(dataContext)
  private val myUserDataHolder = UserDataHolderBase()
  private val updatedPresentations = ConcurrentHashMap<AnAction, Presentation>()
  private val groupChildren = ConcurrentHashMap<ActionGroup, List<AnAction>>()
  private val realUpdateStrategy = UpdateStrategy(
    update = { action -> updateActionReal(action) },
    getChildren = { group -> callAction(group, Op.GetChildren) {
      doGetChildren(group, createActionEvent(updatedPresentations[group] ?: initialBgtPresentation(group)))
    } })
  private val myCheapStrategy = UpdateStrategy(
    update = { action -> presentationFactory.getPresentation(action) },
    getChildren = { group  -> doGetChildren(group, null) })

  private var allowPartialExpand = true
  private var preCacheSlowDataKeys: Boolean = Utils.isAsyncDataContext(dataContext) &&
                                              !Registry.`is`("actionSystem.update.actions.suppress.dataRules.on.edt")
  private var forcedUpdateThread: ActionUpdateThread? = null
  private val testDelayMillis =
    if (ActionPlaces.ACTION_SEARCH == place || ActionPlaces.isShortcutPlace(place)) 0
    else Registry.intValue("actionSystem.update.actions.async.test.delay", 0)
  private val myThreadDumpService = ThreadDumpService.getInstance()
  private var edtCallsCount = 0
  private var edtWaitNanos: Long = 0

  @Volatile
  private var currentEDTWaitMillis: Long = 0

  @Volatile
  private var currentEDTPerformMillis: Long = 0
  private fun updateActionReal(action: AnAction): Presentation? {
    // clone the presentation to avoid partially changing the cached one if the update is interrupted
    val presentation = presentationFactory.getPresentation(action).clone()
    // reset enabled/visible flags (actions are encouraged to always set them in `update`)
    presentation.setEnabledAndVisible(true)
    val success = callAction(action, Op.Update) {
      doUpdate(action, createActionEvent(presentation))
    }
    return if (success) presentation else null
  }

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

  private fun <T> callAction(action: AnAction, operation: Op, call: () -> T): T {
    val operationName = Utils.operationName(action = action, op = operation.name, place = place)
    return callAction(action = action, operationName = operationName, updateThreadOrig = action.getActionUpdateThread(), call = call)
  }

  private fun <T> callAction(action: Any,
                             operationName: String,
                             updateThreadOrig: ActionUpdateThread,
                             call: () -> T): T {
    val updateThread = forcedUpdateThread ?: updateThreadOrig
    val canAsync = Utils.isAsyncDataContext(dataContext)
    val shallAsync = updateThread == ActionUpdateThread.BGT
    val isEDT = EDT.isCurrentThreadEdt()
    val shallEDT = !(canAsync && shallAsync)
    if (isEDT && !shallEDT && !SlowOperations.isInSection(SlowOperations.ACTION_PERFORM) &&
        !ApplicationManager.getApplication().isUnitTestMode()) {
      LOG.error("Calling on EDT $operationName that requires $updateThread${if (forcedUpdateThread != null) " (forced)" else ""}")
    }
    if (allowPartialExpand) {
      ProgressManager.checkCanceled()
    }
    if (isEDT || !shallEDT) {
      return computeWithSpan(Utils.getTracer(true), if (isEDT) "edt-op" else "bgt-op") { span: Span ->
        span.setAttribute(Utils.OT_OP_KEY, operationName)
        val start = System.nanoTime()
        try {
          ProhibitAWTEvents.start(operationName).use {
            call()
          }
        }
        finally {
          val elapsed = TimeoutUtil.getDurationMillis(start)
          span.end()
          if (elapsed > 1000) {
            LOG.warn(elapsedReport(elapsed, isEDT, operationName))
          }
        }
      }
    }
    if (PopupMenuPreloader.isToSkipComputeOnEDT(place)) {
      throw ComputeOnEDTSkipped()
    }
    @Suppress("removal", "DEPRECATION")
    if (preCacheSlowDataKeys && updateThread == ActionUpdateThread.OLD_EDT) {
      ApplicationManagerEx.getApplicationEx().tryRunReadAction {
        ensureSlowDataKeysPreCached(action, operationName)
      }
    }
    return computeOnEdt(action = action, operationName = operationName, call = call, noRulesInEDT = updateThread == ActionUpdateThread.EDT)
  }

  /** @noinspection AssignmentToStaticFieldFromInstanceMethod
   */
  private fun <T> computeOnEdt(action: Any, operationName: String, call: () -> T, noRulesInEDT: Boolean): T {
    currentEDTPerformMillis = 0L
    currentEDTWaitMillis = currentEDTPerformMillis
    val progress = ProgressIndicatorProvider.getGlobalProgressIndicator()!!
    val edtTraceListRef = AtomicReference<List<Throwable>>()
    val start0 = System.nanoTime()
    val supplier: Supplier<out T> = Supplier {
      val start = System.nanoTime()
      edtCallsCount++
      edtWaitNanos += start - start0
      currentEDTWaitMillis = TimeUnit.NANOSECONDS.toMillis(start - start0)
      computeWithSpan(Utils.getTracer(true), "edt-op") { span: Span ->
        span.setAttribute(Utils.OT_OP_KEY, operationName)
        val computable: Computable<out T> = Computable {
          val prevStack = ourInEDTActionOperationStack
          val prevNoRules = isNoRulesInEDTSection
          var traceCookie: ThreadDumpService.Cookie? = null
          try {
            ProhibitAWTEvents.start(operationName).use {
              myThreadDumpService.start(100, 50, 5, Thread.currentThread()).use { cookie ->
                traceCookie = cookie
                ourInEDTActionOperationStack = prevStack.prepend(operationName)
                isNoRulesInEDTSection = noRulesInEDT
                return@Computable call()
              }
            }
          }
          finally {
            isNoRulesInEDTSection = prevNoRules
            ourInEDTActionOperationStack = prevStack
            if (traceCookie != null) {
              currentEDTPerformMillis = TimeoutUtil.getDurationMillis(traceCookie!!.startNanos)
              edtTraceListRef.set(traceCookie!!.traces)
            }
          }
        }
        ProgressManager.getInstance().runProcess(computable, ProgressWrapper.wrap(progress))
      }
    }
    try {
      return computeOnEdt(Context.current().wrapSupplier(supplier))
    }
    finally {
      if (currentEDTWaitMillis > 300) {
        LOG.warn("$currentEDTWaitMillis ms to grab EDT for $operationName")
      }
      if (currentEDTPerformMillis > 300) {
        val throwable: Throwable = PluginException.createByClass(
          elapsedReport(currentEDTPerformMillis, true, operationName) + OLD_EDT_MSG_SUFFIX, null, action.javaClass)
        val edtTraces = edtTraceListRef.get()
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
      currentEDTPerformMillis = 0L
      currentEDTWaitMillis = currentEDTPerformMillis
    }
  }

  /**
   * @return actions from the given and nested non-popup groups that are visible after updating
   */
  fun expandActionGroup(group: ActionGroup, hideDisabled: Boolean): List<AnAction> {
    try {
      return expandActionGroup(group, hideDisabled, realUpdateStrategy)
    }
    finally {
      applyPresentationChanges()
    }
  }

  /**
   * @return actions from the given and nested non-popup groups that are visible after updating
   * don't check progress.isCanceled (to obtain a full list of actions)
   */
  fun expandActionGroupFull(group: ActionGroup, hideDisabled: Boolean): List<AnAction> {
    try {
      allowPartialExpand = false
      return expandActionGroup(group, hideDisabled, realUpdateStrategy)
    }
    finally {
      allowPartialExpand = true
      applyPresentationChanges()
    }
  }

  /**
   * @return actions from the given and nested non-popup groups that are visible after updating
   */
  private fun expandActionGroup(group: ActionGroup, hideDisabled: Boolean, strategy: UpdateStrategy): List<AnAction> {
    return removeUnnecessarySeparators(doExpandActionGroup(group, hideDisabled, strategy))
  }

  /**
   * @return actions from the given and nested non-popup groups that are visible after updating
   */
  @JvmOverloads
  fun expandActionGroupWithTimeout(group: ActionGroup, hideDisabled: Boolean, timeoutMs: Int = -1): List<AnAction> {
    val adjustedMs = if (timeoutMs <= 0) Registry.intValue("actionSystem.update.timeout.ms") else timeoutMs
    val result = ProgressIndicatorUtils.withTimeout(adjustedMs.toLong()) {
      expandActionGroup(group, hideDisabled)
    }
    try {
      return result ?: expandActionGroup(group, hideDisabled, myCheapStrategy)
    }
    finally {
      applyPresentationChanges()
    }
  }

  fun expandActionGroupAsync(group: ActionGroup, hideDisabled: Boolean): CancellablePromise<List<AnAction>> {
    return ActionGroupExpander.getInstance().expandActionGroupAsync(
      presentationFactory, dataContext, place, group, toolbarAction, hideDisabled) { g, h ->
      doExpandActionGroupAsync(g, h)
    }
  }

  private fun doExpandActionGroupAsync(group: ActionGroup, hideDisabled: Boolean): CancellablePromise<List<AnAction>?> {
    val clientId = current
    val disposableParent = when {
      project == null -> getAppSession(clientId)!!
      project.isDefault -> project
      else -> getProjectSession(project, clientId)!!
    }
    val parentIndicator = ProgressIndicatorProvider.getGlobalProgressIndicator()
    val indicator: ProgressIndicator = if (parentIndicator == null) ProgressIndicatorBase() else SensitiveProgressWrapper(parentIndicator)
    val promise = newPromise<List<AnAction>?>(place)
    promise.onError(Consumer<Throwable> {
      indicator.cancel()
      ApplicationManager.getApplication().invokeLater(
        { applyPresentationChanges() }, ModalityState.any(), disposableParent.getDisposed())
    })
    edtCallsCount = 0
    edtWaitNanos = 0
    promise.onProcessed {
      val edtWaitMillis = TimeUnit.NANOSECONDS.toMillis(edtWaitNanos)
      if (edtExecutor == null && (edtCallsCount > 500 || edtWaitMillis > 3000)) {
        LOG.warn(edtWaitMillis.toString() + " ms total to grab EDT " + edtCallsCount + " times to expand " +
                 Utils.operationName(group, null, place) + ". Use `ActionUpdateThread.BGT`.")
      }
    }
    // only one toolbar fast-track at a time
    val useFastTrack = edtExecutor != null && !(toolbarAction && fastTrackToolbarsCount.get() > 0)
    if (useFastTrack && toolbarAction) {
      fastTrackToolbarsCount.incrementAndGet()
    }
    val targetPromises = if (toolbarAction) ourToolbarPromises else ourPromises
    targetPromises.add(promise)
    val computable = Computable {
      indicator.checkCanceled()
      if (testDelayMillis > 0) {
        waitTheTestDelay()
      }
      val result = expandActionGroup(group, hideDisabled, realUpdateStrategy)
      Computable<Void?> {
        try {
          applyPresentationChanges()
          promise.setResult(result)
        }
        catch (e: Throwable) {
          cancelPromise(promise, e)
        }
        null
      }
    }
    val runnable = Runnable {
      val applyRunnableRef = Ref.create<Computable<Void?>>()
      try {
        withClientId(clientId).use {
          BackgroundTaskUtil.runUnderDisposeAwareIndicator(
            disposableParent, Runnable {
            if (tryRunReadActionAndCancelBeforeWrite(promise) { applyRunnableRef.set(computable.compute()) } &&
                !applyRunnableRef.isNull && !promise.isDone()) {
              computeOnEdt(applyRunnableRef.get())
            }
            else if (!promise.isDone()) {
              cancelPromise(promise, "read-action unavailable")
            }
          }, indicator)
        }
      }
      catch (e: Throwable) {
        if (!promise.isDone()) {
          cancelPromise(promise, e)
        }
      }
      finally {
        if (useFastTrack && toolbarAction) {
          fastTrackToolbarsCount.decrementAndGet()
        }
        targetPromises.remove(promise)
        if (!promise.isDone()) {
          cancelPromise(promise, "unknown reason")
          LOG.error(Throwable("'" + place + "' update exited incorrectly (" + !applyRunnableRef.isNull + ")"))
        }
      }
    }
    val current = Context.current()
    service<ActionUpdateExecutorManager>().getExecutor(useFastTrack).execute(object : NamedRunnable(place) {
      override fun run() {
        current.makeCurrent().use { runnable.run() }
      }
    })
    return promise
  }

  fun tryRunReadActionAndCancelBeforeWrite(promise: CancellablePromise<*>, runnable: () -> Unit): Boolean {
    if (promise.isDone) return false
    val applicationEx = ApplicationManagerEx.getApplicationEx()
    return ProgressIndicatorUtils.runActionAndCancelBeforeWrite(
      applicationEx,
      Runnable {
        cancelPromise(promise,
                      if (currentInEDTOperationName() == null) "write-action requested"
                      else NESTED_WA_REASON_PREFIX + currentInEDTOperationName())
      },
      Runnable { applicationEx.tryRunReadAction(runnable) })
  }

  private fun waitTheTestDelay() {
    if (testDelayMillis <= 0) return
    ProgressIndicatorUtils.awaitWithCheckCanceled(testDelayMillis.toLong())
  }

  private fun ensureSlowDataKeysPreCached(action: Any, targetOperationName: String) {
    if (!preCacheSlowDataKeys) return
    val operationName = "precache-slow-data@$targetOperationName"
    val start = System.nanoTime()
    try {
      runWithSpan(Utils.getTracer(true), "precache-slow-data", Consumer { span: Span ->
        span.setAttribute(Utils.OT_OP_KEY, operationName)
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
        preCacheSlowDataKeys = false
      })
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

  private fun doExpandActionGroup(group: ActionGroup, hideDisabled: Boolean, strategy: UpdateStrategy): List<AnAction> {
    if (group is ActionGroupStub) {
      @Suppress("SpellCheckingInspection")
      throw IllegalStateException("Trying to expand non-unstubbed group")
    }
    if (allowPartialExpand) {
      ProgressManager.checkCanceled()
    }
    val prevForceAsync = forcedUpdateThread
    forcedUpdateThread = if (group is ActionUpdateThreadAware.Recursive) group.getActionUpdateThread() else prevForceAsync
    val presentation = update(group, strategy)
    if (presentation == null || !presentation.isVisible) {
      // don't process invisible groups
      return emptyList()
    }

    val children = getGroupChildren(group, strategy)
    val result = if (children.isEmpty()) {
      emptyList()
    }
    else {
      val result = ArrayList<AnAction>()
      for (v in children) {
        result.addAll(expandGroupChild(child = v, hideDisabledBase = hideDisabled, strategy = strategy))
      }
      result
    }

    forcedUpdateThread = prevForceAsync
    val actions = group.postProcessVisibleChildren(/* visibleChildren = */ result, /* updateSession = */ asUpdateSession(strategy))
    for (action in actions) {
      if (action is InlineActionsHolder) {
        for (inlineAction in action.getInlineActions()) {
          update(inlineAction, strategy)
        }
      }
    }
    return actions
  }

  private fun getGroupChildren(group: ActionGroup, strategy: UpdateStrategy): List<AnAction> {
    return groupChildren.computeIfAbsent(group) {
      val children = try {
        strategy.getChildren(group)
      }
      catch (ignore: ComputeOnEDTSkipped) {
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

  private fun expandGroupChild(child: AnAction, hideDisabledBase: Boolean, strategy: UpdateStrategy): List<AnAction> {
    val application = ApplicationManager.getApplication()
    if (application == null || application.isDisposed()) {
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
      val childrenIterable = iterateGroupChildren(child, strategy)
      for (action in childrenIterable.take(100)) {
        if (action is Separator) continue
        val p = update(action, strategy)
        if (p == null) continue
        hasVisible = hasVisible or p.isVisible
        hasEnabled = hasEnabled or p.isEnabled
        // stop early if all the required flags are collected
        if (hasVisible && (hasEnabled || !hideDisabled)) break
      }
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

  private fun <T> computeOnEdt(supplier: Supplier<out T>): T {
    return ActionUpdateEdtExecutor.computeOnEdt(supplier, edtExecutor)
  }

  fun asUpdateSession(): UpdateSession {
    return asUpdateSession(realUpdateStrategy)
  }

  private fun asUpdateSession(strategy: UpdateStrategy): UpdateSession {
    return UpdateSessionImpl(this, strategy)
  }

  private fun iterateGroupChildren(group: ActionGroup, strategy: UpdateStrategy): JBIterable<AnAction> {
    val isDumb = project != null && getInstance(project).isDumb
    return JBTreeTraverser.from { o: AnAction ->
      if (o === group) return@from null
      if (isDumb && !o.isDumbAware()) return@from null
      if (o !is ActionGroup) {
        return@from null
      }
      val presentation = update(o, strategy)
      if (presentation == null || !presentation.isVisible) {
        return@from null
      }
      if (presentation.isPopupGroup || presentation.isPerformGroup) {
        return@from null
      }
      getGroupChildren(o, strategy)
    }
      .withRoots(getGroupChildren(group, strategy))
      .unique()
      .traverse(TreeTraversal.LEAVES_DFS)
      .filter(Condition { !isDumb || it.isDumbAware() })
  }

  private fun update(action: AnAction, strategy: UpdateStrategy): Presentation? {
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
    catch (ignore: ComputeOnEDTSkipped) {
    }
    return null
  }

  companion object {
    var isNoRulesInEDTSection: Boolean = false
      private set

    fun currentInEDTOperationName(): String? = ourInEDTActionOperationStack.head

    init {
      IdeEventQueue.getInstance().addPreprocessor(IdeEventQueue.EventDispatcher { event: AWTEvent ->
        if (event is KeyEvent && event.keyCode != 0 ||
            event is MouseEvent && event.getID() == MouseEvent.MOUSE_PRESSED) {
          cancelPromises(ourToolbarPromises, event)
        }
        false
      }, ApplicationManager.getApplication())
    }
  }

  private class UpdateSessionImpl(val updater: ActionUpdater, val strategy: UpdateStrategy) : UpdateSession {
    override fun expandedChildren(actionGroup: ActionGroup): Iterable<AnAction> {
      return updater.iterateGroupChildren(actionGroup, strategy)
    }

    override fun children(actionGroup: ActionGroup): List<AnAction> {
      return updater.getGroupChildren(actionGroup, strategy)
    }

    override fun presentation(action: AnAction): Presentation {
      return updater.update(action, strategy) ?: updater.initialBgtPresentation(action)
    }

    override fun <T : Any> sharedData(key: Key<T>, provider: Supplier<out T>): T {
      return updater.myUserDataHolder.getUserData(key)
             ?: updater.myUserDataHolder.putUserDataIfAbsent(key, provider.get())
    }

    override fun <T> compute(action: Any,
                             operationName: String,
                             updateThread: ActionUpdateThread,
                             supplier: Supplier<out T>): T {
      val operationNameFull = Utils.operationName(action, operationName, updater.place)
      return updater.callAction(action = action, operationName = operationNameFull, updateThreadOrig = updateThread) { supplier.get() }
    }
  }
}

private enum class Op { Update, GetChildren }

private data class UpdateStrategy(@JvmField val update: (AnAction) -> Presentation?,
                                  @JvmField val getChildren: (ActionGroup) -> Array<AnAction>)

private class ComputeOnEDTSkipped : ProcessCanceledException() {
  override fun fillInStackTrace(): Throwable = this
}

internal fun cancelAllUpdates(reason: String) {
  val adjusted = "$reason (cancelling all updates)"
  cancelPromises(ourToolbarPromises, adjusted)
  cancelPromises(ourPromises, adjusted)
}

private fun cancelPromises(promises: MutableCollection<CancellablePromise<*>>, reason: Any) {
  if (promises.isEmpty()) return
  for (promise in promises) {
    cancelPromise(promise, reason)
  }
  promises.clear()
}

internal fun waitForAllUpdatesToFinish() {
  service<ActionUpdateExecutorManager>().waitForAllUpdatesToFinish()
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
  if (ApplicationManager.getApplication().isDisposed()) false
  else !ActionUtil.performDumbAwareUpdate(action, e, false)
}
catch (ex: Throwable) {
  handleException(action, Op.Update, e, ex)
  false
}

private fun doGetChildren(group: ActionGroup, e: AnActionEvent?): Array<AnAction> {
  try {
    return if (ApplicationManager.getApplication().isDisposed()) AnAction.EMPTY_ARRAY else group.getChildren(e)
  }
  catch (ex: Throwable) {
    handleException(group, Op.GetChildren, e, ex)
    return AnAction.EMPTY_ARRAY
  }
}

private val ourDebugPromisesMap = CollectionFactory.createConcurrentWeakIdentityMap<AsyncPromise<*>, String>()

internal fun <T> newPromise(place: String): AsyncPromise<T> {
  val promise = AsyncPromise<T>()
  if (LOG.isDebugEnabled()) {
    ourDebugPromisesMap[promise] = place
    promise.onProcessed(Consumer { ourDebugPromisesMap.remove(promise) })
  }
  return promise
}

internal fun cancelPromise(promise: CancellablePromise<*>, reason: Any) {
  if (LOG.isDebugEnabled()) {
    val place = ourDebugPromisesMap.remove(promise)
    if (place == null && promise.isDone) return
    val message = "'$place' update cancelled: $reason"
    if (reason is String && (message.contains("fast-track") || message.contains("all updates"))) {
      LOG.debug(message)
    }
    else {
      LOG.debug(message, if (reason is Throwable) reason else ProcessCanceledException())
    }
  }
  val nestedWA = reason is String && reason.startsWith(NESTED_WA_REASON_PREFIX)
  if (nestedWA) {
    LOG.error(AssertionError(
      (reason as String).substring(NESTED_WA_REASON_PREFIX.length) + " requests write-action. " +
      "An action must not request write-action during actions update. " +
      "See CustomComponentAction.createCustomComponent javadoc, if caused by a custom component."))
  }
  if (!nestedWA && promise is AsyncPromise<*>) {
    promise.setError(
      (if (reason is Throwable) reason else ProcessCanceledWithReasonException(reason)))
  }
  else {
    promise.cancel()
  }
}

