// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed

import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages.showErrorDialog
import com.intellij.openapi.vcs.CommittedChangesProvider
import com.intellij.openapi.vcs.RepositoryLocation
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsDataKeys.REMOTE_HISTORY_CHANGED_LISTENER
import com.intellij.openapi.vcs.VcsDataKeys.REMOTE_HISTORY_LOCATION
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList
import com.intellij.util.AsynchConsumer
import com.intellij.util.BufferedListConsumer
import com.intellij.util.Consumer

private val LOG = logger<RepositoryLocationCommittedChangesPanel<*>>()

internal class RepositoryLocationCommittedChangesPanel<S : ChangeBrowserSettings>(
  project: Project,
  val provider: CommittedChangesProvider<*, S>,
  val repositoryLocation: RepositoryLocation,
  extraActions: DefaultActionGroup
) : CommittedChangesPanel(project) {

  @Volatile
  private var isDisposed = false

  var maxCount: Int = 0

  var settings: S = provider.createDefaultSettings()

  var isLoading: Boolean = false
    private set

  init {
    setup(extraActions, provider.createActions(browser, repositoryLocation))
  }

  override fun refreshChanges() = LoadCommittedChangesTask().queue()

  override fun uiDataSnapshot(sink: DataSink) {
    super.uiDataSnapshot(sink)
    sink[REMOTE_HISTORY_CHANGED_LISTENER] = Consumer<String> { refreshChanges() }
    sink[REMOTE_HISTORY_LOCATION] = repositoryLocation
  }

  override fun dispose() {
    isDisposed = true
  }

  private inner class LoadCommittedChangesTask : Backgroundable(project, VcsBundle.message("changes.title.loading.changes"), true) {
    private var error: VcsException? = null

    init {
      browser.reset()
      isLoading = true
      browser.setLoading(true)
    }

    override fun run(indicator: ProgressIndicator) =
      try {
        val appender = { changeLists: List<CommittedChangeList> ->
          runInEdt(ModalityState.stateForComponent(browser)) {
            if (project.isDisposed) return@runInEdt

            browser.append(changeLists)
          }
        }
        val bufferedAppender = BufferedListConsumer(30, appender, -1)

        provider.loadCommittedChanges(settings, repositoryLocation, maxCount, object : AsynchConsumer<CommittedChangeList> {
          override fun consume(changeList: CommittedChangeList) {
            if (isDisposed) indicator.cancel()

            ProgressManager.checkCanceled()
            bufferedAppender.consumeOne(changeList)
          }

          override fun finished() = bufferedAppender.flush()
        })
      }
      catch (e: VcsException) {
        LOG.info(e)
        error = e
      }

    override fun onSuccess() {
      error?.let {
        showErrorDialog(myProject, VcsBundle.message("changes.error.refreshing.view", it.messages.joinToString("\n")),
                        VcsBundle.message("changes.committed.changes"))
      }
    }

    override fun onFinished() {
      isLoading = false
      browser.setLoading(false)
    }
  }
}