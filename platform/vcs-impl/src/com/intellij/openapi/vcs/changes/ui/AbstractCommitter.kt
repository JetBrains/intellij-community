// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.ide.util.DelegatingProgressIndicator
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.ProgressManager.progress
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.util.ExceptionUtil
import com.intellij.util.NullableFunction
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.containers.ContainerUtil.createLockFreeCopyOnWriteList

private val LOG = logger<AbstractCommitter>()

/**
 * @see VetoSavingCommittingDocumentsAdapter
 */
private fun markCommittingDocuments(project: Project, changes: List<Change>): Collection<Document> {
  val result = mutableListOf<Document>()
  for (change in changes) {
    val virtualFile = ChangesUtil.getFilePath(change).virtualFile
    if (virtualFile != null && !virtualFile.fileType.isBinary) {
      val doc = FileDocumentManager.getInstance().getDocument(virtualFile)
      if (doc != null) {
        doc.putUserData<Any>(AbstractCommitter.DOCUMENT_BEING_COMMITTED_KEY, project)
        result.add(doc)
      }
    }
  }
  return result
}

/**
 * @see VetoSavingCommittingDocumentsAdapter
 */
private fun unmarkCommittingDocuments(committingDocuments: Collection<Document>) = committingDocuments.forEach { document ->
  document.putUserData<Any>(AbstractCommitter.DOCUMENT_BEING_COMMITTED_KEY, null)
}

abstract class AbstractCommitter(val project: Project,
                                 val changes: List<Change>,
                                 val commitMessage: String,
                                 val handlers: List<CheckinHandler>,
                                 val additionalData: NullableFunction<Any, Any>) {
  private val committingDocuments = mutableListOf<Document>()
  private val resultHandlers = createLockFreeCopyOnWriteList<CommitResultHandler>()

  private val _feedback = mutableSetOf<String>()
  private val _failedToCommitChanges = mutableListOf<Change>()
  private val _exceptions = mutableListOf<VcsException>()
  private val _pathsToRefresh = mutableListOf<FilePath>()

  val feedback: Set<String> get() = _feedback.toSet()
  val failedToCommitChanges: List<Change> get() = _failedToCommitChanges.toList()
  val exceptions: List<VcsException> get() = _exceptions.toList()
  val pathsToRefresh: List<FilePath> get() = _pathsToRefresh.toList()

  val configuration: VcsConfiguration = VcsConfiguration.getInstance(project)

  fun addResultHandler(resultHandler: CommitResultHandler) {
    resultHandlers += resultHandler
  }

  fun runCommit(taskName: String, sync: Boolean) {
    val task = object : Task.Backgroundable(project, taskName, true, configuration.commitOption) {
      override fun run(indicator: ProgressIndicator) {
        val vcsManager = ProjectLevelVcsManager.getInstance(myProject)
        vcsManager.startBackgroundVcsOperation()
        try {
          delegateCommitToVcsThread()
        }
        finally {
          vcsManager.stopBackgroundVcsOperation()
        }
      }

      override fun shouldStartInBackground(): Boolean = !sync && super.shouldStartInBackground()

      override fun isConditionalModal(): Boolean = sync
    }

    task.queue()
  }

  protected abstract fun commit()

  protected abstract fun afterCommit()

  protected abstract fun onSuccess()

  protected abstract fun onFailure()

  protected abstract fun onFinish()

  protected fun commit(vcs: AbstractVcs<*>, changes: List<Change>) {
    val environment = vcs.checkinEnvironment
    if (environment != null) {
      _pathsToRefresh.addAll(ChangesUtil.getPaths(changes))
      val exceptions = environment.commit(changes, commitMessage, additionalData, _feedback)
      if (!exceptions.isNullOrEmpty()) {
        _exceptions.addAll(exceptions)
        _failedToCommitChanges.addAll(changes)
      }
    }
  }

  private fun delegateCommitToVcsThread() {
    val indicator = DelegatingProgressIndicator()
    TransactionGuard.getInstance().assertWriteSafeContext(indicator.modalityState)
    val endSemaphore = Semaphore()

    endSemaphore.down()
    ChangeListManagerImpl.getInstanceImpl(project).executeOnUpdaterThread {
      indicator.text = "Performing VCS commit..."
      try {
        ProgressManager.getInstance().runProcess(
          {
            indicator.checkCanceled()
            doRunCommit()
          }, indicator)
      }
      finally {
        endSemaphore.up()
      }
    }

    indicator.text = "Waiting for VCS background tasks to finish..."
    while (!endSemaphore.waitFor(20)) {
      indicator.checkCanceled()
    }
  }

  private fun doRunCommit() {
    try {
      runReadAction { markCommittingDocuments() }
      try {
        commit()
      }
      finally {
        runReadAction { unmarkCommittingDocuments() }
      }

      afterCommit()
    }
    catch (pce: ProcessCanceledException) {
      throw pce
    }
    catch (e: Throwable) {
      LOG.error(e)
      _exceptions.add(VcsException(e))
      ExceptionUtil.rethrow(e)
    }
    finally {
      finishCommit()
      onFinish()
    }
  }

  private fun finishCommit() {
    val errors = collectErrors(_exceptions)
    val noErrors = errors.isEmpty()
    val noWarnings = _exceptions.isEmpty()

    if (noErrors) {
      handlers.forEach { it.checkinSuccessful() }
      onSuccess()
      resultHandlers.forEach { it.onSuccess(commitMessage) }

      if (noWarnings) {
        progress(message("commit.dialog.completed.successfully"))
      }
    }
    else {
      handlers.forEach { it.checkinFailed(errors) }
      onFailure()
      resultHandlers.forEach { it.onFailure() }
    }
  }

  private fun markCommittingDocuments() {
    committingDocuments.addAll(markCommittingDocuments(project, changes))
  }

  private fun unmarkCommittingDocuments() {
    unmarkCommittingDocuments(committingDocuments)
    committingDocuments.clear()
  }

  companion object {
    @JvmField
    val DOCUMENT_BEING_COMMITTED_KEY = Key<Any>("DOCUMENT_BEING_COMMITTED")

    @JvmStatic
    fun collectErrors(exceptions: List<VcsException>): List<VcsException> = exceptions.filterNot { it.isWarning }
  }
}