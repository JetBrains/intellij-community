// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.CommonBundle.getCancelButtonText
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages.*
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.ProjectLevelVcsManager
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

interface CommitWorkflowListener : EventListener {
  fun vcsesChanged()

  fun beforeCommitChecksStarted()
  fun beforeCommitChecksEnded(isDefaultCommit: Boolean, result: CheckinHandler.ReturnResult)

  fun customCommitSucceeded()
}

abstract class AbstractCommitWorkflow(val project: Project) {
  protected val eventDispatcher = EventDispatcher.create(CommitWorkflowListener::class.java)

  var commitContext: CommitContext = CommitContext()
    private set

  abstract val isDefaultCommitEnabled: Boolean

  private val _vcses = mutableSetOf<AbstractVcs<*>>()
  val vcses: Set<AbstractVcs<*>> get() = unmodifiableOrEmptySet(_vcses.toSet())

  private val _commitExecutors = mutableListOf<CommitExecutor>()
  val commitExecutors: List<CommitExecutor> get() = newUnmodifiableList(_commitExecutors)

  private val _commitHandlers = mutableListOf<CheckinHandler>()
  val commitHandlers: List<CheckinHandler> get() = newUnmodifiableList(_commitHandlers)

  private val _commitOptions = MutableCommitOptions()
  val commitOptions: CommitOptions get() = _commitOptions.toUnmodifiableOptions()

  protected fun updateVcses(vcses: Set<AbstractVcs<*>>) {
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

  fun addListener(listener: CommitWorkflowListener, parent: Disposable) = eventDispatcher.addListener(listener, parent)

  fun addUnversionedFiles(changeList: LocalChangeList, unversionedFiles: List<VirtualFile>, callback: (List<Change>) -> Unit): Boolean {
    if (unversionedFiles.isEmpty()) return true

    FileDocumentManager.getInstance().saveAllDocuments()
    return addUnversionedFilesToVcs(project, changeList, unversionedFiles, callback, null)
  }

  fun executeDefault(executor: CommitExecutor?) {
    val beforeCommitChecksResult = runBeforeCommitChecksWithEvents(true, executor)
    processExecuteDefaultChecksResult(beforeCommitChecksResult)
  }

  protected open fun processExecuteDefaultChecksResult(result: CheckinHandler.ReturnResult) = Unit

  protected fun runBeforeCommitChecksWithEvents(isDefaultCommit: Boolean, executor: CommitExecutor?): CheckinHandler.ReturnResult {
    eventDispatcher.multicaster.beforeCommitChecksStarted()
    val result = runBeforeCommitChecks(executor)
    eventDispatcher.multicaster.beforeCommitChecksEnded(isDefaultCommit, result)

    return result
  }

  private fun runBeforeCommitChecks(executor: CommitExecutor?): CheckinHandler.ReturnResult {
    var result: CheckinHandler.ReturnResult? = null
    val checks = Runnable {
      FileDocumentManager.getInstance().saveAllDocuments()
      result = runBeforeCommitHandlersChecks(executor)
    }

    doRunBeforeCommitChecks(wrapWithCommitMetaHandlers(checks))

    return result ?: CheckinHandler.ReturnResult.CANCEL
  }

  protected open fun doRunBeforeCommitChecks(checks: Runnable) = checks.run()

  private fun wrapWithCommitMetaHandlers(block: Runnable): Runnable {
    var result = block
    commitHandlers.filterIsInstance<CheckinMetaHandler>().forEach { metaHandler ->
      val previousResult = result
      result = Runnable {
        LOG.debug("CheckinMetaHandler.runCheckinHandlers: $metaHandler")
        metaHandler.runCheckinHandlers(previousResult)
      }
    }
    return result
  }

  private fun runBeforeCommitHandlersChecks(executor: CommitExecutor?): CheckinHandler.ReturnResult {
    commitHandlers.asSequence().filter { it.acceptExecutor(executor) }.forEach { handler ->
      LOG.debug("CheckinHandler.beforeCheckin: $handler")

      val result = handler.beforeCheckin(executor, commitContext.additionalDataConsumer)
      if (result != CheckinHandler.ReturnResult.COMMIT) return result
    }

    return CheckinHandler.ReturnResult.COMMIT
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

  abstract fun executeCustom(executor: CommitExecutor, session: CommitSession)

  protected fun executeCustom(executor: CommitExecutor, session: CommitSession, changes: List<Change>, commitMessage: String) {
    if (!configureCommitSession(executor, session, changes, commitMessage)) return

    val beforeCommitChecksResult = runBeforeCommitChecksWithEvents(false, executor)
    processExecuteCustomChecksResult(executor, session, beforeCommitChecksResult)
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

  protected fun doCommitCustom(executor: CommitExecutor, session: CommitSession, changes: List<Change>, commitMessage: String): Boolean {
    try {
      val completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(
        { session.execute(changes, commitMessage) }, executor.actionText, true, project)

      if (completed) {
        LOG.debug("Commit successful")
        commitHandlers.forEach { it.checkinSuccessful() }
        eventDispatcher.multicaster.customCommitSucceeded()
        return true
      }

      LOG.debug("Commit canceled")
      session.executionCanceled()
    }
    catch (e: Throwable) {
      showErrorDialog(message("error.executing.commit", executor.actionText, e.localizedMessage), executor.actionText)

      val errors = listOf(VcsException(e))
      commitHandlers.forEach { it.checkinFailed(errors) }
    }
    return false
  }

  companion object {
    @JvmStatic
    fun getCommitHandlerFactories(project: Project): List<BaseCheckinHandlerFactory> =
      CheckinHandlersManager.getInstance().getRegisteredCheckinHandlerFactories(ProjectLevelVcsManager.getInstance(project).allActiveVcss)

    // TODO Seems, it is better to get handlers/factories for workflow.vcses, but not allActiveVcss
    @JvmStatic
    fun getCommitHandlers(commitPanel: CheckinProjectPanel, commitContext: CommitContext) =
      getCommitHandlerFactories(commitPanel.project)
        .map { it.createHandler(commitPanel, commitContext) }
        .filter { it != CheckinHandler.DUMMY }

    @JvmStatic
    fun getCommitExecutors(project: Project, changes: Collection<Change>): List<CommitExecutor> =
      getCommitExecutors(project, getAffectedVcses(changes, project))

    internal fun getCommitExecutors(project: Project, vcses: Collection<AbstractVcs<*>>): List<CommitExecutor> =
      vcses.flatMap { it.commitExecutors } + ChangeListManager.getInstance(project).registeredExecutors
  }
}