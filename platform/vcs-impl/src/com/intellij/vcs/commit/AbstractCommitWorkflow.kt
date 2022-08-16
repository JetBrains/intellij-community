// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.CommonBundle.getCancelButtonText
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages.*
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ChangesUtil.getAffectedVcses
import com.intellij.openapi.vcs.changes.actions.ScheduleForAdditionAction.addUnversionedFilesToVcs
import com.intellij.openapi.vcs.checkin.BaseCheckinHandlerFactory
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinMetaHandler
import com.intellij.openapi.vcs.impl.CheckinHandlersManager
import com.intellij.openapi.vcs.impl.PartialChangesUtil.getPartialTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.EventDispatcher
import com.intellij.util.containers.ContainerUtil.newUnmodifiableList
import com.intellij.util.containers.ContainerUtil.unmodifiableOrEmptySet
import com.intellij.util.containers.forEachLoggingErrors
import java.util.*
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

private val IS_AMEND_COMMIT_MODE_KEY = Key.create<Boolean>("Vcs.Commit.IsAmendCommitMode")
var CommitContext.isAmendCommitMode: Boolean by commitProperty(IS_AMEND_COMMIT_MODE_KEY)

private val IS_CLEANUP_COMMIT_MESSAGE_KEY = Key.create<Boolean>("Vcs.Commit.IsCleanupCommitMessage")
var CommitContext.isCleanupCommitMessage: Boolean by commitProperty(IS_CLEANUP_COMMIT_MESSAGE_KEY)

interface CommitWorkflowListener : EventListener {
  fun vcsesChanged()

  fun executionStarted()
  fun executionEnded()

  fun beforeCommitChecksStarted()
  fun beforeCommitChecksEnded(isDefaultCommit: Boolean, executor: CommitExecutor?, result: CommitChecksResult)
}

abstract class AbstractCommitWorkflow(val project: Project) {
  private val eventDispatcher = EventDispatcher.create(CommitWorkflowListener::class.java)
  private val commitEventDispatcher = EventDispatcher.create(CommitResultHandler::class.java)
  private val commitCustomEventDispatcher = EventDispatcher.create(CommitResultHandler::class.java)

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

  internal fun startExecution(block: () -> Boolean) {
    check(!isExecuting)

    isExecuting = true
    continueExecution {
      eventDispatcher.multicaster.executionStarted()
      block()
    }
  }

  internal fun continueExecution(block: () -> Boolean) {
    check(isExecuting)

    runCatching(block)
      .onFailure { endExecution() }
      .onSuccess { continueExecution -> if (!continueExecution) endExecution() }
      .getOrThrow()
  }

  internal fun endExecution(block: () -> Unit) =
    continueExecution {
      block()
      false
    }

  internal fun endExecution() {
    check(isExecuting)

    isExecuting = false
    eventDispatcher.multicaster.executionEnded()
  }

  protected fun getEndExecutionHandler(): CommitResultHandler = EndExecutionCommitResultHandler(this)

  fun addListener(listener: CommitWorkflowListener, parent: Disposable) = eventDispatcher.addListener(listener, parent)
  fun addCommitListener(listener: CommitResultHandler, parent: Disposable) = commitEventDispatcher.addListener(listener, parent)
  fun addCommitCustomListener(listener: CommitResultHandler, parent: Disposable) = commitCustomEventDispatcher.addListener(listener, parent)

  protected fun getCommitEventDispatcher(): CommitResultHandler = EdtCommitResultHandler(commitEventDispatcher.multicaster)
  protected fun getCommitCustomEventDispatcher(): CommitResultHandler = commitCustomEventDispatcher.multicaster

  fun executeSession(sessionInfo: CommitSessionInfo): Boolean {
    val beforeCommitChecksResult = runBeforeCommitChecksWithEvents(sessionInfo)
    processExecuteChecksResult(sessionInfo, beforeCommitChecksResult)
    return beforeCommitChecksResult.shouldCommit
  }

  protected open fun processExecuteChecksResult(sessionInfo: CommitSessionInfo, result: CommitChecksResult) {
    if (result.shouldCommit) {
      performCommit(sessionInfo)
    }
  }

  protected abstract fun performCommit(sessionInfo: CommitSessionInfo)

  protected fun runBeforeCommitChecksWithEvents(sessionInfo: CommitSessionInfo): CommitChecksResult {
    fireBeforeCommitChecksStarted()
    val result = runBeforeCommitChecks(sessionInfo)
    fireBeforeCommitChecksEnded(sessionInfo, result)

    return result
  }

  protected fun fireBeforeCommitChecksStarted() = eventDispatcher.multicaster.beforeCommitChecksStarted()

  protected fun fireBeforeCommitChecksEnded(sessionInfo: CommitSessionInfo, result: CommitChecksResult) =
    eventDispatcher.multicaster.beforeCommitChecksEnded(sessionInfo.isVcsCommit, sessionInfo.executor, result)

  private fun runBeforeCommitChecks(sessionInfo: CommitSessionInfo): CommitChecksResult {
    var result: CommitChecksResult? = null

    var checks = Runnable {
      ProgressManager.checkCanceled()
      FileDocumentManager.getInstance().saveAllDocuments()
      result = runBeforeCommitHandlersChecks(sessionInfo, commitHandlers)
    }

    commitHandlers.filterIsInstance<CheckinMetaHandler>().forEach { metaHandler ->
      checks = wrapWithCommitMetaHandler(metaHandler, checks)
    }

    val task = Runnable {
      try {
        checks.run()
        if (result == null) LOG.warn("No commit handlers result. Seems CheckinMetaHandler returned before invoking its callback.")
      }
      catch (ignore: ProcessCanceledException) {
      }
      catch (e: Throwable) {
        LOG.error(e)
      }
    }
    doRunBeforeCommitChecks(task)

    return result ?: CommitChecksResult.ExecutionError.also { LOG.debug("No commit handlers result. Cancelling commit.") }
  }

  protected open fun doRunBeforeCommitChecks(checks: Runnable) = checks.run()

  protected fun wrapWithCommitMetaHandler(metaHandler: CheckinMetaHandler, task: Runnable): Runnable =
    Runnable {
      try {
        LOG.debug("CheckinMetaHandler.runCheckinHandlers: $metaHandler")
        metaHandler.runCheckinHandlers(task)
      }
      catch (e: ProcessCanceledException) {
        LOG.debug("CheckinMetaHandler cancelled $metaHandler")
        throw e
      }
      catch (e: Throwable) {
        LOG.error(e)
        task.run()
      }
    }

  fun runBeforeCommitHandlersChecks(sessionInfo: CommitSessionInfo, handlers: List<CheckinHandler>): CommitChecksResult {
    handlers.forEachLoggingErrors(LOG) { handler ->
      try {
        val result = runBeforeCommitHandler(handler, sessionInfo)
        when (result) {
          CheckinHandler.ReturnResult.COMMIT -> Unit // continue
          CheckinHandler.ReturnResult.CANCEL -> return CommitChecksResult.Failed()
          CheckinHandler.ReturnResult.CLOSE_WINDOW -> return CommitChecksResult.Failed(toCloseWindow = true)
        }
      }
      catch (e: ProcessCanceledException) {
        LOG.debug("CheckinHandler cancelled $handler")
        return CommitChecksResult.Cancelled
      }
    }

    return CommitChecksResult.Passed(toCommit = true)
  }

  protected open fun runBeforeCommitHandler(handler: CheckinHandler, sessionInfo: CommitSessionInfo): CheckinHandler.ReturnResult {
    val executor = sessionInfo.executor
    if (!handler.acceptExecutor(executor)) return CheckinHandler.ReturnResult.COMMIT
    LOG.debug("CheckinHandler.beforeCheckin: $handler")

    return handler.beforeCheckin(executor, commitContext.additionalDataConsumer)
  }

  open fun canExecute(sessionInfo: CommitSessionInfo, changes: Collection<Change>): Boolean {
    val executor = sessionInfo.executor
    if (executor != null && !executor.supportsPartialCommit()) {
      val hasPartialChanges = changes.any { getPartialTracker(project, it)?.hasPartialChangesToCommit() ?: false }
      if (hasPartialChanges) {
        return YES == showYesNoDialog(
          project, message("commit.dialog.partial.commit.warning.body", executor.getPresentableText()),
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
  }
}

class EdtCommitResultHandler(private val handler: CommitResultHandler) : CommitResultHandler {
  override fun onSuccess(commitMessage: String) = runInEdt { handler.onSuccess(commitMessage) }
  override fun onCancel() = runInEdt { handler.onCancel() }
  override fun onFailure(errors: List<VcsException>) = runInEdt { handler.onFailure(errors) }
}

private class EndExecutionCommitResultHandler(private val workflow: AbstractCommitWorkflow) : CommitResultHandler {
  override fun onSuccess(commitMessage: String) = workflow.endExecution()
  override fun onCancel() = workflow.endExecution()
  override fun onFailure(errors: List<VcsException>) = workflow.endExecution()
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
