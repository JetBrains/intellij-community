// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.CommonBundle.getCancelButtonText
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages.*
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ChangesUtil.getAffectedVcses
import com.intellij.openapi.vcs.checkin.*
import com.intellij.openapi.vcs.impl.CheckinHandlersManager
import com.intellij.openapi.vcs.impl.PartialChangesUtil
import com.intellij.openapi.vcs.impl.PartialChangesUtil.getPartialTracker
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.ContainerUtil.newUnmodifiableList
import com.intellij.util.containers.ContainerUtil.unmodifiableOrEmptySet
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import java.util.*
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

private val LOG = logger<AbstractCommitWorkflow>()

fun CommitOptions.saveState() = allOptions.forEach { it.saveState() }
fun CommitOptions.restoreState() = allOptions.forEach { it.restoreState() }

private class CommitProperty<T>(private val key: Key<T>, private val defaultValue: T) : ReadWriteProperty<CommitContext, T> {
  override fun getValue(thisRef: CommitContext, property: KProperty<*>): T = thisRef.getUserData(key) ?: defaultValue
  override fun setValue(thisRef: CommitContext, property: KProperty<*>, value: T) = thisRef.putUserData(key, value)
}

fun commitProperty(key: Key<Boolean>): ReadWriteProperty<CommitContext, Boolean> = commitProperty(key, false)
fun <T> commitProperty(key: Key<T>, defaultValue: T): ReadWriteProperty<CommitContext, T> = CommitProperty(key, defaultValue)

private val IS_POST_COMMIT_CHECK_KEY = Key.create<Boolean>("Vcs.Commit.IsPostCommitCheck")
var CommitContext.isPostCommitCheck: Boolean by commitProperty(IS_POST_COMMIT_CHECK_KEY)

private val IS_AMEND_COMMIT_MODE_KEY = Key.create<Boolean>("Vcs.Commit.IsAmendCommitMode")
var CommitContext.isAmendCommitMode: Boolean by commitProperty(IS_AMEND_COMMIT_MODE_KEY)

private val IS_CLEANUP_COMMIT_MESSAGE_KEY = Key.create<Boolean>("Vcs.Commit.IsCleanupCommitMessage")
var CommitContext.isCleanupCommitMessage: Boolean by commitProperty(IS_CLEANUP_COMMIT_MESSAGE_KEY)

interface CommitWorkflowListener : EventListener {
  fun vcsesChanged() = Unit

  fun executionStarted() = Unit
  fun executionEnded() = Unit

  fun beforeCommitChecksStarted(sessionInfo: CommitSessionInfo) = Unit
  fun beforeCommitChecksEnded(sessionInfo: CommitSessionInfo, result: CommitChecksResult) = Unit
}

abstract class AbstractCommitWorkflow(val project: Project) {
  private val eventDispatcher = EventDispatcher.create(CommitWorkflowListener::class.java)
  private val commitEventDispatcher = EventDispatcher.create(CommitterResultHandler::class.java)
  private val commitCustomEventDispatcher = EventDispatcher.create(CommitterResultHandler::class.java)

  var isExecuting = false
    private set

  var commitContext: CommitContext = CommitContext()
    private set

  abstract val isDefaultCommitEnabled: Boolean

  private val _vcses = mutableSetOf<AbstractVcs>()
  val vcses: Set<AbstractVcs> get() = unmodifiableOrEmptySet(_vcses.toSet())

  private val _commitExecutors = mutableListOf<CommitExecutor>()
  val commitExecutors: List<CommitExecutor> get() = newUnmodifiableList(_commitExecutors)

  private val _commitHandlers = mutableListOf<CheckinHandler>()
  val commitHandlers: List<CheckinHandler> get() = newUnmodifiableList(_commitHandlers)

  private val _commitOptions = MutableCommitOptions()
  val commitOptions: CommitOptions get() = _commitOptions.toUnmodifiableOptions()

  protected fun updateVcses(vcses: Set<AbstractVcs>) {
    if (_vcses != vcses) {
      _vcses.clear()
      _vcses += vcses

      eventDispatcher.multicaster.vcsesChanged()
    }
  }

  internal fun initCommitExecutors(executors: List<CommitExecutor>) {
    _commitExecutors.clear()
    _commitExecutors += executors
  }

  internal fun initCommitHandlers(handlers: List<CheckinHandler>) {
    _commitHandlers.clear()
    _commitHandlers += handlers
  }

  internal fun disposeCommitOptions() {
    _commitOptions.allOptions.filterIsInstance<Disposable>().forEach { Disposer.dispose(it) }
    _commitOptions.clear()
  }

  internal fun initCommitOptions(options: CommitOptions) {
    disposeCommitOptions()
    _commitOptions.add(options)
  }

  internal fun clearCommitContext() {
    commitContext = CommitContext()
  }

  @RequiresEdt
  internal fun startExecution(block: () -> Boolean) {
    check(!isExecuting) { "Commit session is already started" }

    isExecuting = true
    continueExecution {
      eventDispatcher.multicaster.executionStarted()
      block()
    }
  }

  internal fun continueExecution(block: () -> Boolean) {
    check(isExecuting) { "Commit session has already finished" }

    try {
      val continueExecution = block()
      if (!continueExecution) endExecution()
    }
    catch (e: ProcessCanceledException) {
      endExecution()
    }
    catch (e: CancellationException) {
      endExecution()
    }
    catch (e: Throwable) {
      endExecution()
      LOG.error(e)
    }
  }

  @RequiresEdt
  internal fun endExecution() {
    check(isExecuting) { "Commit session has already finished" }

    isExecuting = false
    eventDispatcher.multicaster.executionEnded()
  }

  fun addListener(listener: CommitWorkflowListener, parent: Disposable) =
    eventDispatcher.addListener(listener, parent)

  fun addVcsCommitListener(listener: CommitterResultHandler, parent: Disposable) =
    commitEventDispatcher.addListener(listener, parent)

  fun addCommitCustomListener(listener: CommitterResultHandler, parent: Disposable) =
    commitCustomEventDispatcher.addListener(listener, parent)

  fun executeSession(sessionInfo: CommitSessionInfo, commitInfo: DynamicCommitInfo): Boolean {
    return runBlockingModal(project, message("commit.checks.on.commit.progress.text")) {
      withContext(Dispatchers.EDT) {
        fireBeforeCommitChecksStarted(sessionInfo)
        val result = runModalBeforeCommitChecks(commitInfo)
        fireBeforeCommitChecksEnded(sessionInfo, result)

        if (result.shouldCommit) {
          performCommit(sessionInfo)
          return@withContext true
        }
        else {
          return@withContext false
        }
      }
    }
  }

  protected abstract fun performCommit(sessionInfo: CommitSessionInfo)

  protected open fun addCommonResultHandlers(sessionInfo: CommitSessionInfo, committer: Committer) {
    committer.addResultHandler(CheckinHandlersNotifier(committer, commitHandlers))
    if (sessionInfo.isVcsCommit) {
      committer.addResultHandler(commitEventDispatcher.multicaster)
    }
    else {
      committer.addResultHandler(commitCustomEventDispatcher.multicaster)
    }
    committer.addResultHandler(EndExecutionCommitResultHandler(this))
  }

  protected fun fireBeforeCommitChecksStarted(sessionInfo: CommitSessionInfo) =
    eventDispatcher.multicaster.beforeCommitChecksStarted(sessionInfo)

  protected fun fireBeforeCommitChecksEnded(sessionInfo: CommitSessionInfo, result: CommitChecksResult) =
    eventDispatcher.multicaster.beforeCommitChecksEnded(sessionInfo, result)

  suspend fun <T> runModificationCommitChecks(modifications: suspend () -> T): T {
    return PartialChangesUtil.underChangeList(project, getBeforeCommitChecksChangelist(), modifications)
  }

  private suspend fun runModalBeforeCommitChecks(commitInfo: DynamicCommitInfo): CommitChecksResult {
    return runModificationCommitChecks {
      runCommitHandlers(commitInfo)
    }
  }

  private suspend fun runCommitHandlers(commitInfo: DynamicCommitInfo): CommitChecksResult {
    try {
      val handlers = commitHandlers
      val commitChecks = handlers
        .map { it.asCommitCheck(commitInfo) }
        .filter { it.isEnabled() }
        .groupBy { it.getExecutionOrder() }

      if (!checkDumbMode(commitInfo, commitChecks.values.flatten())) {
        return CommitChecksResult.Cancelled
      }

      runModalCommitChecks(commitInfo, commitChecks[CommitCheck.ExecutionOrder.EARLY])?.let { return it }

      @Suppress("DEPRECATION") val metaHandlers = handlers.filterIsInstance<CheckinMetaHandler>()
      runMetaHandlers(metaHandlers)

      runModalCommitChecks(commitInfo, commitChecks[CommitCheck.ExecutionOrder.MODIFICATION])?.let { return it }
      FileDocumentManager.getInstance().saveAllDocuments()

      runModalCommitChecks(commitInfo, commitChecks[CommitCheck.ExecutionOrder.LATE])?.let { return it }
      runModalCommitChecks(commitInfo, commitChecks[CommitCheck.ExecutionOrder.POST_COMMIT])?.let { return it }

      return CommitChecksResult.Passed
    }
    catch (e: CancellationException) {
      return CommitChecksResult.Cancelled
    }
    catch (e: Throwable) {
      LOG.warn(Throwable(e))
      return CommitChecksResult.ExecutionError
    }
  }

  private fun checkDumbMode(commitInfo: DynamicCommitInfo,
                            commitChecks: List<CommitCheck>): Boolean {
    if (!DumbService.isDumb(project)) return true
    if (commitChecks.none { commitCheck -> commitCheck.isEnabled() && !DumbService.isDumbAware(commitCheck) }) return true

    return !MessageDialogBuilder.yesNo(message("commit.checks.error.indexing"),
                                       message("commit.checks.error.indexing.message", ApplicationNamesInfo.getInstance().productName))
      .yesText(message("checkin.wait"))
      .noText(commitInfo.commitActionText)
      .ask(project)
  }

  private suspend fun runModalCommitChecks(commitInfo: DynamicCommitInfo,
                                           commitChecks: List<CommitCheck>?): CommitChecksResult? {
    for (commitCheck in commitChecks.orEmpty()) {
      try {
        val problem = runCommitCheck(project, commitCheck, commitInfo)
        if (problem == null) continue

        val result = blockingContext {
          problem.showModalSolution(project, commitInfo)
        }
        return when (result) {
          CheckinHandler.ReturnResult.COMMIT -> continue
          CheckinHandler.ReturnResult.CANCEL -> CommitChecksResult.Failed()
          CheckinHandler.ReturnResult.CLOSE_WINDOW -> CommitChecksResult.Failed(toCloseWindow = true)
        }
      }
      catch (e: CancellationException) {
        LOG.debug("CheckinHandler cancelled $commitCheck")
        throw e
      }
    }
    return null // check passed
  }

  protected open fun getBeforeCommitChecksChangelist(): LocalChangeList? = null

  open fun canExecute(sessionInfo: CommitSessionInfo, changes: Collection<Change>): Boolean {
    val executor = sessionInfo.executor
    if (executor != null && !executor.supportsPartialCommit()) {
      val hasPartialChanges = changes.any { getPartialTracker(project, it)?.hasPartialChangesToCommit() ?: false }
      if (hasPartialChanges) {
        return YES == showYesNoDialog(
          project, message("commit.dialog.partial.commit.warning.body", cleanActionText(executor.actionText)),
          message("commit.dialog.partial.commit.warning.title"), executor.actionText, getCancelButtonText(),
          getWarningIcon())
      }
    }
    return true
  }

  companion object {
    @JvmStatic
    fun getCommitHandlerFactories(vcses: Collection<AbstractVcs>): List<BaseCheckinHandlerFactory> =
      CheckinHandlersManager.getInstance().getRegisteredCheckinHandlerFactories(vcses.toTypedArray())

    @JvmStatic
    fun getCommitHandlers(
      vcses: Collection<AbstractVcs>,
      commitPanel: CheckinProjectPanel,
      commitContext: CommitContext
    ): List<CheckinHandler> =
      getCommitHandlerFactories(vcses)
        .map { it.createHandler(commitPanel, commitContext) }
        .filter { it != CheckinHandler.DUMMY }

    @JvmStatic
    fun getCommitExecutors(project: Project, changes: Collection<Change>): List<CommitExecutor> =
      getCommitExecutors(project, getAffectedVcses(changes, project))

    internal fun getCommitExecutors(project: Project, vcses: Collection<AbstractVcs>): List<CommitExecutor> {
      return vcses.flatMap { it.commitExecutors } +
             ChangeListManager.getInstance(project).registeredExecutors +
             LocalCommitExecutor.LOCAL_COMMIT_EXECUTOR.getExtensions(project)
    }

    suspend fun runMetaHandlers(@Suppress("DEPRECATION") metaHandlers: List<CheckinMetaHandler>) {
      EDT.assertIsEdt()
      // reversed to have the same order as when wrapping meta handlers into each other
      for (metaHandler in metaHandlers.reversed()) {
        suspendCancellableCoroutine { continuation ->
          try {
            withCurrentJob(continuation.context.job) {
              LOG.debug("CheckinMetaHandler.runCheckinHandlers: $metaHandler")
              metaHandler.runCheckinHandlers {
                continuation.resume(Unit)
              }
            }
          }
          catch (e: CancellationException) {
            LOG.debug("CheckinMetaHandler cancelled $metaHandler")
            continuation.resumeWithException(e)
          }
          catch (e: Throwable) {
            LOG.debug("CheckinMetaHandler failed $metaHandler")
            continuation.resumeWithException(e)
          }
        }
      }
    }

    suspend fun runCommitCheck(project: Project, commitCheck: CommitCheck, commitInfo: CommitInfo): CommitProblem? {
      try {
        if (DumbService.isDumb(project) && !DumbService.isDumbAware(commitCheck)) {
          LOG.debug("Skipped commit check in dumb mode $commitCheck")
          return null
        }

        LOG.debug("Running commit check $commitCheck")
        val ctx = coroutineContext
        ctx.ensureActive()
        ctx.progressSink?.update(text = "", details = "")

        return commitCheck.runCheck(commitInfo)
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        return CommitProblem.createError(e)
      }
    }
  }
}

private class EndExecutionCommitResultHandler(private val workflow: AbstractCommitWorkflow) : CommitterResultHandler {
  override fun onAfterRefresh() {
    workflow.endExecution()
  }
}

sealed class CommitSessionInfo {
  val isVcsCommit: Boolean get() = session === CommitSession.VCS_COMMIT

  abstract val executor: CommitExecutor?
  abstract val session: CommitSession

  object Default : CommitSessionInfo() {
    override val executor: CommitExecutor? get() = null
    override val session: CommitSession get() = CommitSession.VCS_COMMIT
  }

  class Custom(override val executor: CommitExecutor,
               override val session: CommitSession) : CommitSessionInfo()
}

internal fun CheckinHandler.asCommitCheck(commitInfo: CommitInfo): CommitCheck {
  if (this is CommitCheck) return this
  return ProxyCommitCheck(this, commitInfo.executor)
}

private class ProxyCommitCheck(private val checkinHandler: CheckinHandler,
                               private val executor: CommitExecutor?) : CommitCheck {
  override fun getExecutionOrder(): CommitCheck.ExecutionOrder {
    if (checkinHandler is CheckinModificationHandler) return CommitCheck.ExecutionOrder.MODIFICATION
    return CommitCheck.ExecutionOrder.LATE
  }

  override fun isDumbAware(): Boolean {
    return DumbService.isDumbAware(checkinHandler)
  }

  override fun isEnabled(): Boolean = checkinHandler.acceptExecutor(executor)

  override suspend fun runCheck(commitInfo: CommitInfo): CommitProblem? {
    val result = blockingContext {
      @Suppress("DEPRECATION") checkinHandler.beforeCheckin(commitInfo.executor, commitInfo.commitContext.additionalDataConsumer)
    }
    if (result == null || result == CheckinHandler.ReturnResult.COMMIT) return null
    return UnknownCommitProblem(result)
  }

  override fun toString(): String {
    return "ProxyCommitCheck: $checkinHandler"
  }
}

internal class UnknownCommitProblem(val result: CheckinHandler.ReturnResult) : CommitProblem {
  override val text: String get() = message("before.checkin.error.unknown")

  override fun showModalSolution(project: Project, commitInfo: CommitInfo): CheckinHandler.ReturnResult = result
}
