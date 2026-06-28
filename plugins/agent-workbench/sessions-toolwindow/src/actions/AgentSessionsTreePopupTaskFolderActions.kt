// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.actions

import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.service.AgentSessionArchiveRequestResult
import com.intellij.agent.workbench.sessions.service.AgentSessionArchiveService
import com.intellij.agent.workbench.sessions.statistics.AgentWorkbenchEntryPoint
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.SessionActionTarget
import com.intellij.platform.ai.agent.sessions.core.folders.AgentTaskFolder
import com.intellij.platform.ai.agent.sessions.core.folders.AgentTaskFolderService
import com.intellij.platform.ai.agent.sessions.core.folders.AgentTaskFolderStatus

internal class AgentSessionsTreePopupCreateTaskFolderAction : DumbAwareAction {
  private val resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext?
  private val promptForName: (Project) -> String?
  private val createFolder: (String, String) -> Unit

  @Suppress("unused")
  constructor() {
    resolveContext = ::resolveAgentSessionsTreePopupActionContext
    promptForName = ::showCreateTaskFolderDialog
    createFolder = { path, name -> service<AgentTaskFolderService>().createFolder(path, name) }
  }

  internal constructor(
    resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext?,
    promptForName: (Project) -> String?,
    createFolder: (String, String) -> Unit,
  ) {
    this.resolveContext = resolveContext
    this.promptForName = promptForName
    this.createFolder = createFolder
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = createFolderPath(resolveContext(e)?.target) != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveContext(e) ?: return
    val path = createFolderPath(context.target) ?: return
    val name = promptForName(context.project) ?: return
    createFolder(path, name)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

internal class AgentSessionsTreePopupRenameTaskFolderAction : DumbAwareAction() {
  private val resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext? =
    ::resolveAgentSessionsTreePopupActionContext
  private val promptForName: (Project, String) -> String? = ::showRenameTaskFolderDialog
  private val renameFolder: (SessionActionTarget.TaskFolder, String) -> Unit = { target, name ->
    service<AgentTaskFolderService>().renameFolder(target.path, target.folderId, name)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = resolveContext(e)?.target is SessionActionTarget.TaskFolder
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveContext(e) ?: return
    val target = context.target as? SessionActionTarget.TaskFolder ?: return
    val name = promptForName(context.project, target.name) ?: return
    renameFolder(target, name)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

internal class AgentSessionsTreePopupDeleteTaskFolderAction : DumbAwareAction() {
  private val resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext? =
    ::resolveAgentSessionsTreePopupActionContext
  private val confirmDelete: (Project, SessionActionTarget.TaskFolder) -> Boolean = ::confirmDeleteTaskFolder
  private val deleteFolder: (SessionActionTarget.TaskFolder) -> Unit = { target ->
    service<AgentTaskFolderService>().deleteFolder(target.path, target.folderId)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = resolveContext(e)?.target is SessionActionTarget.TaskFolder
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveContext(e) ?: return
    val target = context.target as? SessionActionTarget.TaskFolder ?: return
    if (confirmDelete(context.project, target)) {
      deleteFolder(target)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

internal class AgentSessionsTreePopupMoveToTaskFolderGroup @JvmOverloads constructor(
  private val resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext? = ::resolveAgentSessionsTreePopupActionContext,
  private val listFolders: (String) -> List<AgentTaskFolder> = { path -> service<AgentTaskFolderService>().listFolders(path) },
  private val assignThread: (SessionActionTarget.Thread, AgentTaskFolder) -> Unit = { target, folder ->
    service<AgentTaskFolderService>().assignThread(target.path, target.provider, target.threadId, folder.id)
  },
) : ActionGroup(), DumbAware {
  override fun update(e: AnActionEvent) {
    val menu = moveMenu(e)
    e.presentation.isEnabledAndVisible = menu != null && menu.folders.isNotEmpty()
    e.presentation.isPopupGroup = true
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val menu = e?.let(::moveMenu) ?: return emptyArray()
    return menu.folders.map { folder ->
      MoveToTaskFolderAction(menu.targets, folder, assignThread)
    }.toTypedArray()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private fun moveMenu(e: AnActionEvent): MoveToTaskFolderMenu? {
    val context = resolveContext(e) ?: return null
    val targets = activeThreadTargets(context)
    if (targets.isEmpty()) return null
    val path = targets.map { it.path }.distinct().singleOrNull() ?: return null
    return MoveToTaskFolderMenu(targets = targets, folders = listFolders(path))
  }

  private data class MoveToTaskFolderMenu(
    @JvmField val targets: List<SessionActionTarget.Thread>,
    @JvmField val folders: List<AgentTaskFolder>,
  )
}

internal class AgentSessionsTreePopupRemoveFromTaskFolderAction : DumbAwareAction {
  private val resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext?
  private val isAssigned: (SessionActionTarget.Thread) -> Boolean
  private val unassignThread: (SessionActionTarget.Thread) -> Unit

  @Suppress("unused")
  constructor() {
    resolveContext = ::resolveAgentSessionsTreePopupActionContext
    isAssigned = { target -> service<AgentTaskFolderService>().getFolderForThread(target.path, target.provider, target.threadId) != null }
    unassignThread = { target -> service<AgentTaskFolderService>().unassignThread(target.path, target.provider, target.threadId) }
  }

  internal constructor(
    resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext?,
    isAssigned: (SessionActionTarget.Thread) -> Boolean,
    unassignThread: (SessionActionTarget.Thread) -> Unit,
  ) {
    this.resolveContext = resolveContext
    this.isAssigned = isAssigned
    this.unassignThread = unassignThread
  }

  override fun update(e: AnActionEvent) {
    val targets = resolveContext(e)?.let(::activeThreadTargets).orEmpty()
    e.presentation.isEnabledAndVisible = targets.any(isAssigned)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val targets = resolveContext(e)?.let(::activeThreadTargets).orEmpty()
    targets.filter(isAssigned).forEach(unassignThread)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

internal class AgentSessionsTreePopupSetTaskFolderMetadataAction : DumbAwareAction() {
  private val resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext? =
    ::resolveAgentSessionsTreePopupActionContext
  private val promptForMetadata: (Project, SessionActionTarget.TaskFolder) -> Pair<String, String>? =
    ::showSetTaskFolderMetadataDialog
  private val setMetadata: (SessionActionTarget.TaskFolder, String, String) -> Unit = { target, key, value ->
    service<AgentTaskFolderService>().setMetadata(target.path, target.folderId, key, value)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = resolveContext(e)?.target is SessionActionTarget.TaskFolder
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveContext(e) ?: return
    val target = context.target as? SessionActionTarget.TaskFolder ?: return
    val (key, value) = promptForMetadata(context.project, target) ?: return
    setMetadata(target, key, value)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

internal class AgentSessionsTreePopupDeleteTaskFolderMetadataAction : DumbAwareAction() {
  private val resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext? =
    ::resolveAgentSessionsTreePopupActionContext
  private val promptForKey: (Project, SessionActionTarget.TaskFolder) -> String? = ::showDeleteTaskFolderMetadataDialog
  private val deleteMetadata: (SessionActionTarget.TaskFolder, String) -> Unit = { target, key ->
    service<AgentTaskFolderService>().deleteMetadata(target.path, target.folderId, key)
  }

  override fun update(e: AnActionEvent) {
    val target = resolveContext(e)?.target as? SessionActionTarget.TaskFolder
    e.presentation.isEnabledAndVisible = target?.metadata?.isNotEmpty() == true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveContext(e) ?: return
    val target = context.target as? SessionActionTarget.TaskFolder ?: return
    val key = promptForKey(context.project, target) ?: return
    deleteMetadata(target, key)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

internal class AgentSessionsTreePopupMarkTaskFolderDoneAction : DumbAwareAction {
  private val resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext?
  private val canArchiveProvider: (AgentSessionProvider) -> Boolean
  private val archiveThreads: (List<ArchiveThreadTarget>, AgentWorkbenchEntryPoint, (AgentSessionArchiveRequestResult) -> Unit) -> Unit
  private val setFolderDone: (SessionActionTarget.TaskFolder) -> Unit

  @Suppress("unused")
  constructor() {
    resolveContext = ::resolveAgentSessionsTreePopupActionContext
    canArchiveProvider = { provider -> service<AgentSessionArchiveService>().canArchiveProvider(provider) }
    archiveThreads = { targets, entryPoint, onComplete ->
      service<AgentSessionArchiveService>().archiveThreads(targets, entryPoint, onComplete = onComplete)
    }
    setFolderDone =
      { target -> service<AgentTaskFolderService>().setFolderStatus(target.path, target.folderId, AgentTaskFolderStatus.DONE) }
  }

  internal constructor(
    resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext?,
    canArchiveProvider: (AgentSessionProvider) -> Boolean,
    archiveThreads: (List<ArchiveThreadTarget>, AgentWorkbenchEntryPoint, (AgentSessionArchiveRequestResult) -> Unit) -> Unit,
    setFolderDone: (SessionActionTarget.TaskFolder) -> Unit,
  ) {
    this.resolveContext = resolveContext
    this.canArchiveProvider = canArchiveProvider
    this.archiveThreads = archiveThreads
    this.setFolderDone = setFolderDone
  }

  override fun update(e: AnActionEvent) {
    val context = resolveContext(e)
    val target = context?.target as? SessionActionTarget.TaskFolder
    if (target == null || target.isDone) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val targets = context.taskFolderArchiveTargets
    e.presentation.isVisible = true
    e.presentation.isEnabled = targets.isEmpty() || targets.all { archiveTarget -> canArchiveProvider(archiveTarget.provider) }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveContext(e) ?: return
    val target = context.target as? SessionActionTarget.TaskFolder ?: return
    if (target.isDone) return
    val archiveTargets = context.taskFolderArchiveTargets
    if (archiveTargets.isEmpty()) {
      setFolderDone(target)
      return
    }
    if (!archiveTargets.all { archiveTarget -> canArchiveProvider(archiveTarget.provider) }) {
      return
    }
    archiveThreads(archiveTargets, AgentWorkbenchEntryPoint.TREE_POPUP) { result ->
      if (result.allRequestedArchived) {
        setFolderDone(target)
      }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

private class MoveToTaskFolderAction(
  private val targets: List<SessionActionTarget.Thread>,
  private val folder: AgentTaskFolder,
  private val assignThread: (SessionActionTarget.Thread, AgentTaskFolder) -> Unit,
) : DumbAwareAction(folder.name) {
  override fun actionPerformed(e: AnActionEvent) {
    targets.forEach { target -> assignThread(target, folder) }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

private fun activeThreadTargets(context: AgentSessionsTreePopupActionContext): List<SessionActionTarget.Thread> {
  return context.selectedThreadTargets.ifEmpty {
    listOfNotNull(context.target as? SessionActionTarget.Thread)
  }
}

private fun createFolderPath(target: SessionActionTarget?): String? {
  return when (target) {
    is SessionActionTarget.Project -> target.path
    is SessionActionTarget.Worktree -> target.path
    else -> null
  }
}

private fun showCreateTaskFolderDialog(project: Project): String? {
  return showTaskFolderNameDialog(
    project = project,
    title = AgentSessionsBundle.message("toolwindow.task.folder.create.dialog.title"),
    message = AgentSessionsBundle.message("toolwindow.task.folder.create.dialog.message"),
    initialValue = "",
  )
}

private fun showRenameTaskFolderDialog(project: Project, currentName: String): String? {
  return showTaskFolderNameDialog(
    project = project,
    title = AgentSessionsBundle.message("toolwindow.task.folder.rename.dialog.title"),
    message = AgentSessionsBundle.message("toolwindow.task.folder.rename.dialog.message"),
    initialValue = currentName,
  )
}

private fun showTaskFolderNameDialog(
  project: Project,
  title: @NlsContexts.DialogTitle String,
  message: @NlsContexts.DialogMessage String,
  initialValue: String,
): String? {
  return Messages.showInputDialog(project, message, title, Messages.getQuestionIcon(), initialValue, NonBlankInputValidator())
}

private fun confirmDeleteTaskFolder(project: Project, target: SessionActionTarget.TaskFolder): Boolean {
  return Messages.showYesNoDialog(
    project,
    AgentSessionsBundle.message("toolwindow.task.folder.delete.dialog.message", target.name),
    AgentSessionsBundle.message("toolwindow.task.folder.delete.dialog.title"),
    Messages.getQuestionIcon(),
  ) == Messages.YES
}

private fun showSetTaskFolderMetadataDialog(project: Project, target: SessionActionTarget.TaskFolder): Pair<String, String>? {
  val key = Messages.showInputDialog(
    project,
    AgentSessionsBundle.message("toolwindow.task.folder.metadata.key.dialog.message"),
    AgentSessionsBundle.message("toolwindow.task.folder.metadata.set.dialog.title"),
    Messages.getQuestionIcon(),
    target.metadata.keys.firstOrNull().orEmpty(),
    NonBlankInputValidator(),
  ) ?: return null
  val value = Messages.showInputDialog(
    project,
    AgentSessionsBundle.message("toolwindow.task.folder.metadata.value.dialog.message"),
    AgentSessionsBundle.message("toolwindow.task.folder.metadata.set.dialog.title"),
    Messages.getQuestionIcon(),
    target.metadata[key].orEmpty(),
    null,
  ) ?: return null
  return key to value
}

private fun showDeleteTaskFolderMetadataDialog(project: Project, target: SessionActionTarget.TaskFolder): String? {
  return Messages.showInputDialog(
    project,
    AgentSessionsBundle.message("toolwindow.task.folder.metadata.delete.dialog.message"),
    AgentSessionsBundle.message("toolwindow.task.folder.metadata.delete.dialog.title"),
    Messages.getQuestionIcon(),
    target.metadata.keys.firstOrNull().orEmpty(),
    NonBlankInputValidator(),
  )
}

private class NonBlankInputValidator : InputValidatorEx {
  override fun checkInput(inputString: String?): Boolean = !inputString.isNullOrBlank()

  override fun canClose(inputString: String?): Boolean = checkInput(inputString)

  override fun getErrorText(inputString: String?): String? {
    return if (checkInput(inputString)) null else AgentSessionsBundle.message("toolwindow.task.folder.dialog.validation.empty")
  }
}
