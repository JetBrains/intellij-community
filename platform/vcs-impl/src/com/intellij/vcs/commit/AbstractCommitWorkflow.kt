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
import com.intellij.openapi.vcs.changes.ui.SessionDialog
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

internal fun CommitOptions.saveState() = allOptions.forEach { it.saveState() }
internal fun CommitOptions.restoreState() = allOptions.forEach { it.restoreState() }
internal fun CommitOptions.refresh() = allOptions.forEach { it.refresh() }

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
  fun beforeCommitChecksEnded(isDefaultCommit: Boolean, result: CheckinHandler.ReturnResult)
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

  fun addUnversionedFiles(changeList: LocalChangeList, unversionedFiles: List<VirtualFile>, callback: (List<Change>) -> Unit): Boolean {
    if (unversionedFiles.isEmpty()) return true

    FileDocumentManager.getInstance().saveAllDocuments()
    return addUnversionedFilesToVcs(project, changeList, unversionedFiles, callback, null)
  }

  fun executeDefault(executor: CommitExecutor?): Boolean {
    val beforeCommitChecksResult = runBeforeCommitChecksWithEvents(true, executor)
    processExecuteDefaultChecksResult(beforeCommitChecksResult)
    return beforeCommitChecksResult == CheckinHandler.ReturnResult.COMMIT
  }

  protected open fun processExecuteDefaultChecksResult(result: CheckinHandler.ReturnResult) = Unit

  protected fun runBeforeCommitChecksWithEvents(isDefaultCommit: Boolean, executor: CommitExecutor?): CheckinHandler.ReturnResult {
    fireBeforeCommitChecksStarted()
    val result = runBeforeCommitChecks(executor)
    fireBeforeCommitChecksEnded(isDefaultCommit, result)

    return result
  }

  protected fun fireBeforeCommitChecksStarted() = eventDispatcher.multicaster.beforeCommitChecksStarted()

  protected fun fireBeforeCommitChecksEnded(isDefaultCommit: Boolean, result: CheckinHandler.ReturnResult) =
    eventDispatcher.multicaster.beforeCommitChecksEnded(isDefaultCommit, result)

  private fun runBeforeCommitChecks(executor: CommitExecutor?): CheckinHandler.ReturnResult {
    var result: CheckinHandler.ReturnResult? = null

    var checks = Runnable {
      ProgressManager.checkCanceled()
      FileDocumentManager.getInstance().saveAllDocuments()
      result = runBeforeCommitHandlersChecks(executor, commitHandlers)
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

    return result ?: CheckinHandler.ReturnResult.CANCEL.also { LOG.debug("No commit handlers result. Cancelling commit.") }
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

  protected fun runBeforeCommitHandlersChecks(executor: CommitExecutor?, handlers: List<CheckinHandler>): CheckinHandler.ReturnResult {
    handlers.forEachLoggingErrors(LOG) { handler ->
      try {
        val result = runBeforeCommitHandler(handler, executor)
        if (result != CheckinHandler.ReturnResult.COMMIT) return result
      }
      catch (e: ProcessCanceledException) {
        LOG.debug("CheckinHandler cancelled $handler")
        return CheckinHandler.ReturnResult.CANCEL
      }
    }

    return CheckinHandler.ReturnResult.COMMIT
  }

  protected open fun runBeforeCommitHandler(handler: CheckinHandler, executor: CommitExecutor?): CheckinHandler.ReturnResult {
    if (!handler.acceptExecutor(executor)) return CheckinHandler.ReturnResult.COMMIT
    LOG.debug("CheckinHandler.beforeCheckin: $handler")

    return handler.beforeCheckin(executor, commitContext.additionalDataConsumer)
  }

  open fun canExecute(executor: CommitExecutor, changes: Collection<Change>): Boolean {
    if (!executor.supportsPartialCommit()) {
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

  abstract fun executeCustom(executor: CommitExecutor, session: CommitSession): Boolean

  protected fun executeCustom(executor: CommitExecutor, session: CommitSession, changes: List<Change>, commitMessage: String): Boolean =
    configureCommitSession(executor, session, changes, commitMessage) &&
    run {
      val beforeCommitChecksResult = runBeforeCommitChecksWithEvents(false, executor)
      processExecuteCustomChecksResult(executor, session, beforeCommitChecksResult)
      beforeCommitChecksResult == CheckinHandler.ReturnResult.COMMIT
    }

  private fun configureCommitSession(executor: CommitExecutor,
                                     session: CommitSession,
                                     changes: List<Change>,
                                     commitMessage: String): Boolean {
    val sessionConfigurationUi = session.getAdditionalConfigurationUI(changes, commitMessage) ?: return true
    val sessionDialog = SessionDialog(executor.getPresentableText(), project, session, changes, commitMessage, sessionConfigurationUi)

    if (sessionDialog.showAndGet()) return true
    else {
      session.executionCanceled()
      return false
    }
  }

  protected open fun processExecuteCustomChecksResult(executor: CommitExecutor,
                                                      session: CommitSession,
                                                      result: CheckinHandler.ReturnResult) = Unit

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

    internal fun getCommitExecutors(project: Project, vcses: Collection<AbstractVcs>): List<CommitExecutor> =
      vcses.flatMap { it.commitExecutors } + ChangeListManager.getInstance(project).registeredExecutors +
      LocalCommitExecutor.LOCAL_COMMIT_EXECUTOR.getExtensions(project)
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
