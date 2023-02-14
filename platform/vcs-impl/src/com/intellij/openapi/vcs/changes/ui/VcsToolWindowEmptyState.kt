// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.ide.impl.isTrusted
import com.intellij.openapi.actionSystem.*
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
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.ui.content.impl.ContentManagerImpl
import com.intellij.util.ui.StatusText
import java.awt.event.InputEvent

private const val ACTION_LOCAL_HISTORY = "LocalHistory.ShowHistory"

internal fun setChangesViewEmptyState(statusText: StatusText, project: Project) {
  fun invokeAction(source: Any?, actionId: String) = invokeAction(project, source, actionId, ActionPlaces.CHANGES_VIEW_EMPTY_STATE)
  fun invokeAction(source: Any?, action: AnAction) = invokeAction(project, source, action, ActionPlaces.CHANGES_VIEW_EMPTY_STATE)

  statusText.appendLine(message("status.text.vcs.toolwindow"))
  findCreateRepositoryAction()?.let { action ->
    statusText.appendLine(message("status.text.vcs.toolwindow.create.repository"), LINK_PLAIN_ATTRIBUTES) {
      invokeAction(it.source, action)
    }
  }
  statusText.appendLine(message("status.text.vcs.toolwindow.local.history"), LINK_PLAIN_ATTRIBUTES) {
    invokeAction(it.source, ACTION_LOCAL_HISTORY)
  }
  statusText.appendLine("")
  statusText.appendLine(AllIcons.General.ContextHelp, message("status.text.vcs.toolwindow.help"), LINK_PLAIN_ATTRIBUTES) {
    invokeAction(it.source, ACTION_CONTEXT_HELP)
  }
}

internal fun setCommitViewEmptyState(statusText: StatusText, project: Project) {
  fun invokeAction(source: Any?, actionId: String) = invokeAction(project, source, actionId, ActionPlaces.COMMIT_VIEW_EMPTY_STATE)
  fun invokeAction(source: Any?, action: AnAction) = invokeAction(project, source, action, ActionPlaces.COMMIT_VIEW_EMPTY_STATE)

  findCreateRepositoryAction()?.let { action ->
    statusText.appendLine(message("status.text.commit.toolwindow.create.repository.prefix"))
      .appendText(" ")
      .appendText(message("status.text.commit.toolwindow.create.repository"), LINK_PLAIN_ATTRIBUTES) {
        invokeAction(it.source, action)
      }
  }
  statusText.appendLine(message("status.text.commit.toolwindow.local.history.prefix"))
    .appendText(" ")
    .appendText(message("status.text.commit.toolwindow.local.history"), LINK_PLAIN_ATTRIBUTES) {
      invokeAction(it.source, ACTION_LOCAL_HISTORY)
    }
  statusText.appendLine("")
  statusText.appendLine(AllIcons.General.ContextHelp, message("status.text.vcs.toolwindow.help"), LINK_PLAIN_ATTRIBUTES) {
    invokeAction(it.source, ACTION_CONTEXT_HELP)
  }
}

internal class ActivateCommitToolWindowAction : ActivateToolWindowAction(ToolWindowId.COMMIT) {
  init {
    templatePresentation.setText(IdeBundle.messagePointer("toolwindow.stripe.Commit"))
    templatePresentation.icon = AllIcons.Toolwindows.ToolWindowCommit
  }

  override fun hasEmptyState(project: Project): Boolean = ChangesViewContentManager.isCommitToolWindowShown(project)

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


internal fun hideIdLabelIfNotEmptyState(toolWindow: ToolWindow) {
  fun updateIdLabel() {
    val hideIdLabel = when {
      toolWindow.contentManager.contentCount == 1 && ExperimentalUI.isNewUI() -> null
      toolWindow.contentManager.isEmpty -> null
      else -> "true"
    }
    if (toolWindow.component.getClientProperty(ToolWindowContentUi.HIDE_ID_LABEL) != hideIdLabel) {
      toolWindow.component.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, hideIdLabel)
      updateContentUi(toolWindow.contentManager, toolWindow.project)
    }
  }

  toolWindow.contentManager.addContentManagerListener(object : ContentManagerListener {
    override fun contentAdded(event: ContentManagerEvent) {
      updateIdLabel()
    }

    override fun contentRemoved(event: ContentManagerEvent) {
      updateIdLabel()
    }
  })
  updateIdLabel()
}

private fun updateContentUi(contentManager: ContentManager, project: Project) {
  if (contentManager is ContentManagerImpl) {
    (contentManager.ui as? ToolWindowContentUi)?.update()
  }

  updateCommitTabName(contentManager, project)
}

private fun updateCommitTabName(contentManager: ContentManager, project: Project) {
  val singleContent = contentManager.contents.singleOrNull()

  if (ExperimentalUI.isNewUI() && singleContent != null && singleContent.tabName == ChangesViewContentManager.LOCAL_CHANGES) {
    singleContent.displayName = null
  }
  else {
    contentManager.contents.filter { it.tabName == ChangesViewContentManager.LOCAL_CHANGES }.forEach {
      val displayName = it.getUserData(CHANGES_VIEW_EXTENSION)?.getDisplayName(project)
      it.displayName = displayName
    }
  }
}