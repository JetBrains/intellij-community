// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages.showErrorDialog
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil.equalsIgnoreWhitespaces
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsRoot
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.util.EventDispatcher

private val AMEND_DATA_KEY = Key.create<AmendData>("Vcs.Commit.AmendData")
private var CommitContext.amendData: AmendData? by commitProperty(AMEND_DATA_KEY, null)

private class AmendData(val beforeAmendMessage: String, val amendMessage: String)

private val LOG = logger<AmendCommitHandlerImpl>()

class AmendCommitHandlerImpl(private val workflowHandler: AbstractCommitWorkflowHandler<*, *>) : AmendCommitHandler {
  private val amendCommitEventDispatcher = EventDispatcher.create(AmendCommitModeListener::class.java)

  private val workflow get() = workflowHandler.workflow
  private val commitContext get() = workflow.commitContext

  private val vcsManager = ProjectLevelVcsManager.getInstance(workflow.project)

  var initialMessage: String? = null

  override var isAmendCommitMode: Boolean
    get() = commitContext.isAmendCommitMode
    set(value) {
      val oldValue = isAmendCommitMode
      commitContext.isAmendCommitMode = value

      if (oldValue != value) {
        amendCommitEventDispatcher.multicaster.amendCommitModeToggled()
        if (value) setAmendMessage() else restoreBeforeAmendMessage()
      }
    }

  override var isAmendCommitModeTogglingEnabled: Boolean = true

  override fun isAmendCommitModeSupported(): Boolean =
    workflow.isDefaultCommitEnabled &&
    workflow.vcses.mapNotNull { it.checkinEnvironment }.filterIsInstance<AmendCommitAware>().any { it.isAmendCommitSupported() }

  override fun addAmendCommitModeListener(listener: AmendCommitModeListener, parent: Disposable) =
    amendCommitEventDispatcher.addListener(listener, parent)

  private fun setAmendMessage() {
    val beforeAmendMessage = workflowHandler.getCommitMessage()

    // if initial message set - only update commit message if user hasn't changed it
    if (initialMessage == null || beforeAmendMessage == initialMessage) {
      val roots = workflowHandler.ui.getIncludedPaths().mapNotNull { vcsManager.getVcsRootObjectFor(it) }.toSet()
      val messages = LoadCommitMessagesTask(workflow.project, roots).load() ?: return

      val amendMessage = messages.distinct().joinToString(separator = "\n").takeIf { it.isNotBlank() }
      amendMessage?.let { setAmendMessage(beforeAmendMessage, it) }
    }
  }

  private fun setAmendMessage(beforeAmendMessage: String, amendMessage: String) {
    if (!equalsIgnoreWhitespaces(beforeAmendMessage, amendMessage)) {
      VcsConfiguration.getInstance(workflow.project).saveCommitMessage(beforeAmendMessage)
      setCommitMessageAndFocus(amendMessage)

      commitContext.amendData = AmendData(beforeAmendMessage, amendMessage)
    }
  }

  private fun restoreBeforeAmendMessage() {
    val amendData = commitContext.amendData ?: return
    commitContext.amendData = null

    // only restore if user hasn't changed commit message
    if (amendData.amendMessage == workflowHandler.getCommitMessage()) {
      setCommitMessageAndFocus(amendData.beforeAmendMessage)
    }
  }

  private fun setCommitMessageAndFocus(message: String) = with(workflowHandler) {
    setCommitMessage(message)
    ui.commitMessageUi.focus()
  }

  private inner class LoadCommitMessagesTask(project: Project, private val roots: Collection<VcsRoot>) :
    Task.WithResult<List<String>, VcsException>(project, "Loading Commit Message...", true) {

    fun load(): List<String>? {
      queue()
      return try {
        result
      }
      catch (e: VcsException) {
        showErrorDialog(project, "Couldn't load commit message of the commit to amend.\n${e.message}", "Commit Message not Loaded")
        LOG.info(e)
        null
      }
    }

    override fun compute(indicator: ProgressIndicator): List<String> = roots.mapNotNull { vcsRoot ->
      val amendAware = vcsRoot.vcs?.checkinEnvironment as? AmendCommitAware ?: return@mapNotNull null
      vcsRoot.path?.let { amendAware.getLastCommitMessage(it) }
    }
  }
}