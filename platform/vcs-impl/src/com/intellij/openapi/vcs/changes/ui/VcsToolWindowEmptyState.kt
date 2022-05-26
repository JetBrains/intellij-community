// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.ide.impl.isTrusted
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ActionPlaces.CHANGES_VIEW_EMPTY_STATE
import com.intellij.openapi.actionSystem.ActionPlaces.COMMIT_VIEW_EMPTY_STATE
import com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT
import com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE
import com.intellij.openapi.actionSystem.IdeActions.ACTION_CONTEXT_HELP
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys.HELP_ID
import com.intellij.openapi.actionSystem.ex.ActionUtil.invokeAction
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi.HIDE_ID_LABEL
import com.intellij.ui.SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.ui.content.impl.ContentManagerImpl
import com.intellij.util.ui.StatusText
import java.awt.event.InputEvent

private const val ACTION_LOCAL_HISTORY = "LocalHistory.ShowHistory"

internal fun StatusText.setChangesViewEmptyState(project: Project) {
  fun invokeAction(source: Any?, actionId: String) = invokeAction(project, source, actionId, CHANGES_VIEW_EMPTY_STATE)
  fun invokeAction(source: Any?, action: AnAction) = invokeAction(project, source, action, CHANGES_VIEW_EMPTY_STATE)

  appendLine(message("status.text.vcs.toolwindow"))
  findCreateRepositoryAction()?.let { action ->
    appendLine(message("status.text.vcs.toolwindow.create.repository"), LINK_PLAIN_ATTRIBUTES) {
      invokeAction(it.source, action)
    }
  }
  appendLine(message("status.text.vcs.toolwindow.local.history"), LINK_PLAIN_ATTRIBUTES) {
    invokeAction(it.source, ACTION_LOCAL_HISTORY)
  }
  appendLine("")
  appendLine(AllIcons.General.ContextHelp, message("status.text.vcs.toolwindow.help"), LINK_PLAIN_ATTRIBUTES) {
    invokeAction(it.source, ACTION_CONTEXT_HELP)
  }
}

internal fun StatusText.setCommitViewEmptyState(project: Project) {
  fun invokeAction(source: Any?, actionId: String) = invokeAction(project, source, actionId, COMMIT_VIEW_EMPTY_STATE)
  fun invokeAction(source: Any?, action: AnAction) = invokeAction(project, source, action, COMMIT_VIEW_EMPTY_STATE)

  findCreateRepositoryAction()?.let { action ->
    appendLine(message("status.text.commit.toolwindow.create.repository.prefix"))
      .appendText(" ")
      .appendText(message("status.text.commit.toolwindow.create.repository"), LINK_PLAIN_ATTRIBUTES) {
        invokeAction(it.source, action)
      }
  }
  appendLine(message("status.text.commit.toolwindow.local.history.prefix"))
    .appendText(" ")
    .appendText(message("status.text.commit.toolwindow.local.history"), LINK_PLAIN_ATTRIBUTES) {
      invokeAction(it.source, ACTION_LOCAL_HISTORY)
    }
  appendLine("")
  appendLine(AllIcons.General.ContextHelp, message("status.text.vcs.toolwindow.help"), LINK_PLAIN_ATTRIBUTES) {
    invokeAction(it.source, ACTION_CONTEXT_HELP)
  }
}

internal class ActivateCommitToolWindowAction : ActivateToolWindowAction(ToolWindowId.COMMIT) {
  init {
    templatePresentation.setText(IdeBundle.messagePointer("toolwindow.stripe.Commit"))
    templatePresentation.icon = AllIcons.Toolwindows.ToolWindowCommit
  }

  override fun hasEmptyState(): Boolean = true

  override fun update(e: AnActionEvent) {
    if (e.project?.isTrusted() == false) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    super.update(e)
  }
}

private fun findCreateRepositoryAction(): AnAction? {
  val group = ActionManager.getInstance().getAction("Vcs.ToolWindow.CreateRepository") as? ActionGroup
  return group?.getChildren(null)?.firstOrNull()
}

private fun invokeAction(project: Project, source: Any?, actionId: String, place: String) {
  val action = ActionManager.getInstance().getAction(actionId) ?: return
  invokeAction(project, source, action, place)
}

private fun invokeAction(project: Project, source: Any?, action: AnAction, place: String) {
  invokeAction(action, createDataContext(project), place, source as? InputEvent, null)
}

private fun createDataContext(project: Project): DataContext {
  return SimpleDataContext.builder()
    .add(PROJECT, project)
    .add(VIRTUAL_FILE, project.guessProjectDir())
    .add(HELP_ID, "version.control.empty.state")
    .build()
}


internal fun ToolWindow.hideIdLabelIfNotEmptyState() {
  contentManager.addContentManagerListener(object : ContentManagerListener {
    override fun contentAdded(event: ContentManagerEvent) {
      if (contentManager.contentCount != 1) return

      component.putClientProperty(HIDE_ID_LABEL, "true")
      contentManager.updateContentUi()
    }

    override fun contentRemoved(event: ContentManagerEvent) {
      if (!contentManager.isEmpty) return

      component.putClientProperty(HIDE_ID_LABEL, null)
      contentManager.updateContentUi()
    }
  })
}

private fun ContentManager.updateContentUi() {
  if (this !is ContentManagerImpl) return

  (ui as? ToolWindowContentUi)?.update()
}