// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsDataKeys.COMMIT_WORKFLOW_HANDLER
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ui.AbstractCommitWorkflow.Companion.getCommitHandlers
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.ui.Refreshable
import com.intellij.util.ui.UIUtil.replaceMnemonicAmpersand

// Need to support '_' for mnemonics as it is supported in DialogWrapper internally
private fun String.fixUnderscoreMnemonic() = replace('_', '&')

internal fun getDefaultCommitActionName(vcses: Collection<AbstractVcs<*>> = emptyList()): String =
  replaceMnemonicAmpersand(
    (vcses.mapNotNull { it.checkinEnvironment?.checkinOperationName }.distinct().singleOrNull()
     ?: VcsBundle.getString("commit.dialog.default.commit.operation.name")
    ).fixUnderscoreMnemonic()
  )

abstract class AbstractCommitWorkflowHandler<W : AbstractCommitWorkflow, U : CommitWorkflowUi> :
  CommitWorkflowHandler,
  CommitWorkflowListener,
  CommitExecutorListener,
  InclusionListener,
  Disposable {

  abstract val workflow: W
  abstract val ui: U

  protected val project get() = workflow.project
  private val vcsConfiguration get() = VcsConfiguration.getInstance(project)

  protected abstract val commitPanel: CheckinProjectPanel

  protected fun getIncludedChanges() = ui.getIncludedChanges()
  protected fun getIncludedUnversionedFiles() = ui.getIncludedUnversionedFiles()
  internal fun isCommitEmpty(): Boolean = getIncludedChanges().isEmpty() && getIncludedUnversionedFiles().isEmpty()

  protected fun getCommitMessage() = ui.commitMessageUi.text
  protected fun setCommitMessage(text: String?) = ui.commitMessageUi.setText(text)

  protected val commitHandlers get() = workflow.commitHandlers

  protected fun createDataProvider() = DataProvider { dataId ->
    when {
      COMMIT_WORKFLOW_HANDLER.`is`(dataId) -> this
      Refreshable.PANEL_KEY.`is`(dataId) -> commitPanel
      else -> null
    }
  }

  protected fun initCommitHandlers() = workflow.initCommitHandlers(getCommitHandlers(commitPanel, workflow.commitContext))

  override fun inclusionChanged() = commitHandlers.forEach { it.includedChangesChanged() }

  override fun executorCalled(executor: CommitExecutor?) = executor?.let { execute(it) } ?: executeDefault(null)

  override fun getExecutor(executorId: String): CommitExecutor? = null
  override fun isExecutorEnabled(executor: CommitExecutor): Boolean = false
  override fun execute(executor: CommitExecutor) {
    val session = executor.createCommitSession()

    if (session === CommitSession.VCS_COMMIT) {
      executeDefault(executor)
    }
    else {
      executeCustom(executor, session)
    }
  }

  override fun beforeCommitChecksStarted() = ui.startBeforeCommitChecks()
  override fun beforeCommitChecksEnded(isDefaultCommit: Boolean, result: CheckinHandler.ReturnResult) = ui.endBeforeCommitChecks(result)

  protected open fun executeDefault(executor: CommitExecutor?) = Unit

  protected open fun executeCustom(executor: CommitExecutor, session: CommitSession) = Unit

  protected fun addUnversionedFiles(changeList: LocalChangeList): Boolean =
    workflow.addUnversionedFiles(changeList, getIncludedUnversionedFiles()) { changes -> ui.includeIntoCommit(changes) }

  protected fun checkEmptyCommitMessage(): Boolean =
    getCommitMessage().isNotEmpty() || !vcsConfiguration.FORCE_NON_EMPTY_COMMENT || ui.confirmCommitWithEmptyMessage()

  override fun dispose() = Unit
}