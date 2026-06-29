// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.actions

import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.service.AgentSessionArchiveRequestResult
import com.intellij.agent.workbench.sessions.service.AgentSessionArchiveService
import com.intellij.agent.workbench.sessions.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile
import com.intellij.agent.workbench.sessions.task.folders.AgentTaskFolder
import com.intellij.agent.workbench.sessions.task.folders.AgentTaskFolderService
import com.intellij.agent.workbench.sessions.task.folders.AgentTaskFolderStatus
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.SessionActionTarget
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.event.ActionEvent
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel

internal class AgentSessionsTreePopupCreateTaskFolderAction : DumbAwareAction {
  private val resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext?
  private val promptForCreateRequest: (Project, Boolean) -> CreateTaskFolderRequest?
  private val createFolder: (String, String) -> AgentTaskFolder?
  private val taskFolderAgentProfile: (Project) -> AgentPromptLaunchProfile?
  private val openTaskFolderAgent: (String, AgentTaskFolder, AgentPromptLaunchProfile, Project) -> Unit

  @Suppress("unused")
  constructor() {
    resolveContext = ::resolveAgentSessionsTreePopupActionContext
    promptForCreateRequest = ::showCreateTaskFolderDialog
    createFolder = { path, name -> service<AgentTaskFolderService>().createFolder(path, name) }
    taskFolderAgentProfile = ::resolveTaskFolderAgentQuickStartProfile
    openTaskFolderAgent = { path, folder, profile, project ->
      createTaskFolderAgentViaService(path, profile, project, AgentWorkbenchEntryPoint.TREE_POPUP, folder)
    }
  }

  internal constructor(
    resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext?,
    promptForCreateRequest: (Project, Boolean) -> CreateTaskFolderRequest?,
    createFolder: (String, String) -> AgentTaskFolder?,
    taskFolderAgentProfile: (Project) -> AgentPromptLaunchProfile? = { null },
    openTaskFolderAgent: (String, AgentTaskFolder, AgentPromptLaunchProfile, Project) -> Unit = { _, _, _, _ -> },
  ) {
    this.resolveContext = resolveContext
    this.promptForCreateRequest = promptForCreateRequest
    this.createFolder = createFolder
    this.taskFolderAgentProfile = taskFolderAgentProfile
    this.openTaskFolderAgent = openTaskFolderAgent
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = createFolderPath(resolveContext(e)?.target) != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveContext(e) ?: return
    val path = createFolderPath(context.target) ?: return
    val profile = taskFolderAgentProfile(context.project)
    val request = promptForCreateRequest(context.project, profile != null) ?: return
    val folder = createFolder(path, request.name) ?: return
    if (request.createWithAgent) {
      val effectiveProfile = profile ?: taskFolderAgentProfile(context.project) ?: return
      openTaskFolderAgent(path, folder, effectiveProfile, context.project)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

internal class AgentSessionsTreePopupRenameTaskFolderAction : DumbAwareAction() {
  private val resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext? =
    ::resolveAgentSessionsTreePopupActionContext
  private val promptForName: (Project, String) -> String? = ::showRenameTaskFolderDialog
  private val renameFolder: (AgentTaskFolderActionTarget, String) -> Unit = { target, name ->
    service<AgentTaskFolderService>().renameFolder(target.folderId, name)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = resolveContext(e)?.taskFolderTarget != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveContext(e) ?: return
    val target = context.taskFolderTarget ?: return
    val name = promptForName(context.project, target.name) ?: return
    renameFolder(target, name)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

internal class AgentSessionsTreePopupDeleteTaskFolderAction : DumbAwareAction() {
  private val resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext? =
    ::resolveAgentSessionsTreePopupActionContext
  private val confirmDelete: (Project, AgentTaskFolderActionTarget) -> Boolean = ::confirmDeleteTaskFolder
  private val deleteFolder: (AgentTaskFolderActionTarget) -> Unit = { target ->
    service<AgentTaskFolderService>().deleteFolder(target.folderId)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = resolveContext(e)?.taskFolderTarget != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveContext(e) ?: return
    val target = context.taskFolderTarget ?: return
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
      MoveToTaskFolderAction(menu.move, folder, assignThread)
    }.toTypedArray()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private fun moveMenu(e: AnActionEvent): MoveToTaskFolderMenu? {
    val context = resolveContext(e) ?: return null
    val move = resolveTaskFolderThreadMove(context.target, context.selectedThreadTargets) ?: return null
    return MoveToTaskFolderMenu(move = move, folders = listFolders(move.path))
  }

  private data class MoveToTaskFolderMenu(
    @JvmField val move: TaskFolderThreadMove,
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

internal class AgentSessionsTreePopupSetTaskFolderMetadataAction : DumbAwareAction {
  private val resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext?
  private val promptForMetadata: (Project, AgentTaskFolderActionTarget) -> TaskFolderMetadataEdit?
  private val setMetadata: (AgentTaskFolderActionTarget, String, String) -> Unit

  @Suppress("unused")
  constructor() {
    resolveContext = ::resolveAgentSessionsTreePopupActionContext
    promptForMetadata = ::showSetTaskFolderMetadataDialog
    setMetadata = { target, key, value -> service<AgentTaskFolderService>().setMetadata(target.folderId, key, value) }
  }

  internal constructor(
    resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext?,
    promptForMetadata: (Project, AgentTaskFolderActionTarget) -> TaskFolderMetadataEdit?,
    setMetadata: (AgentTaskFolderActionTarget, String, String) -> Unit,
  ) {
    this.resolveContext = resolveContext
    this.promptForMetadata = promptForMetadata
    this.setMetadata = setMetadata
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = resolveContext(e)?.taskFolderTarget != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveContext(e) ?: return
    val target = context.taskFolderTarget ?: return
    val metadata = promptForMetadata(context.project, target)?.let(::resolveTaskFolderMetadataUpdate) ?: return
    setMetadata(target, metadata.key, metadata.value)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

private val TASK_FOLDER_METADATA_KEY_PRESETS: List<@NlsSafe String> = listOf("issue", "review")

internal data class TaskFolderMetadataEdit(
  @JvmField val key: String,
  @JvmField val value: String,
)

internal data class CreateTaskFolderRequest(
  @JvmField val name: String,
  @JvmField val createWithAgent: Boolean,
)

internal data class TaskFolderMetadataUpdate(
  @JvmField val key: String,
  @JvmField val value: String,
)

internal fun resolveTaskFolderMetadataUpdate(edit: TaskFolderMetadataEdit): TaskFolderMetadataUpdate? {
  val key = edit.key.trim().takeIf { it.isNotEmpty() } ?: return null
  return TaskFolderMetadataUpdate(key = key, value = edit.value)
}

internal class AgentSessionsTreePopupDeleteTaskFolderMetadataAction : DumbAwareAction() {
  private val resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext? =
    ::resolveAgentSessionsTreePopupActionContext
  private val promptForKey: (Project, AgentTaskFolderActionTarget) -> String? = ::showDeleteTaskFolderMetadataDialog
  private val deleteMetadata: (AgentTaskFolderActionTarget, String) -> Unit = { target, key ->
    service<AgentTaskFolderService>().deleteMetadata(target.folderId, key)
  }

  override fun update(e: AnActionEvent) {
    val target = resolveContext(e)?.taskFolderTarget
    e.presentation.isEnabledAndVisible = target?.metadata?.isNotEmpty() == true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveContext(e) ?: return
    val target = context.taskFolderTarget ?: return
    val key = promptForKey(context.project, target) ?: return
    deleteMetadata(target, key)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

internal class AgentSessionsTreePopupMarkTaskFolderDoneAction : DumbAwareAction {
  private val resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext?
  private val canArchiveProvider: (AgentSessionProvider) -> Boolean
  private val archiveThreads: (List<ArchiveThreadTarget>, AgentWorkbenchEntryPoint, (AgentSessionArchiveRequestResult) -> Unit) -> Unit
  private val setFolderDone: (AgentTaskFolderActionTarget) -> Unit

  @Suppress("unused")
  constructor() {
    resolveContext = ::resolveAgentSessionsTreePopupActionContext
    canArchiveProvider = { provider -> service<AgentSessionArchiveService>().canArchiveProvider(provider) }
    archiveThreads = { targets, entryPoint, onComplete ->
      service<AgentSessionArchiveService>().archiveThreads(targets, entryPoint, onComplete = onComplete)
    }
    setFolderDone =
      { target -> service<AgentTaskFolderService>().setFolderStatus(target.folderId, AgentTaskFolderStatus.DONE) }
  }

  internal constructor(
    resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext?,
    canArchiveProvider: (AgentSessionProvider) -> Boolean,
    archiveThreads: (List<ArchiveThreadTarget>, AgentWorkbenchEntryPoint, (AgentSessionArchiveRequestResult) -> Unit) -> Unit,
    setFolderDone: (AgentTaskFolderActionTarget) -> Unit,
  ) {
    this.resolveContext = resolveContext
    this.canArchiveProvider = canArchiveProvider
    this.archiveThreads = archiveThreads
    this.setFolderDone = setFolderDone
  }

  override fun update(e: AnActionEvent) {
    val context = resolveContext(e)
    val target = context?.taskFolderTarget
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
    val target = context.taskFolderTarget ?: return
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
  private val move: TaskFolderThreadMove,
  private val folder: AgentTaskFolder,
  private val assignThread: (SessionActionTarget.Thread, AgentTaskFolder) -> Unit,
) : DumbAwareAction(folder.name) {
  override fun actionPerformed(e: AnActionEvent) {
    assignThreadsToTaskFolder(move, folder, assignThread)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

internal data class TaskFolderThreadMove(
  @JvmField val path: String,
  @JvmField val targets: List<SessionActionTarget.Thread>,
)

internal fun resolveTaskFolderThreadMove(
  target: SessionActionTarget?,
  selectedThreadTargets: List<SessionActionTarget.Thread>,
): TaskFolderThreadMove? {
  val targets = selectedThreadTargets.ifEmpty {
    listOfNotNull(target as? SessionActionTarget.Thread)
  }
  if (targets.isEmpty()) return null
  val path = targets.map { it.path }.distinct().singleOrNull() ?: return null
  return TaskFolderThreadMove(path = path, targets = targets)
}

internal fun canMoveThreadsToTaskFolder(move: TaskFolderThreadMove, folder: AgentTaskFolder): Boolean {
  return folder.status == AgentTaskFolderStatus.IN_PROGRESS && move.path == folder.path
}

internal fun assignThreadsToTaskFolder(
  move: TaskFolderThreadMove,
  folder: AgentTaskFolder,
  assignThread: (SessionActionTarget.Thread, AgentTaskFolder) -> Unit,
) {
  move.targets.forEach { target -> assignThread(target, folder) }
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

private fun showCreateTaskFolderDialog(project: Project, canCreateWithAgent: Boolean): CreateTaskFolderRequest? {
  val dialog = CreateTaskFolderDialog(project, canCreateWithAgent)
  if (!dialog.showAndGet()) return null
  return dialog.createRequest()
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

private fun confirmDeleteTaskFolder(project: Project, target: AgentTaskFolderActionTarget): Boolean {
  return Messages.showYesNoDialog(
    project,
    AgentSessionsBundle.message("toolwindow.task.folder.delete.dialog.message", target.name),
    AgentSessionsBundle.message("toolwindow.task.folder.delete.dialog.title"),
    Messages.getQuestionIcon(),
  ) == Messages.YES
}

private fun showSetTaskFolderMetadataDialog(project: Project, target: AgentTaskFolderActionTarget): TaskFolderMetadataEdit? {
  val dialog = TaskFolderMetadataDialog(project, target)
  return if (dialog.showAndGet()) dialog.metadataEdit() else null
}

private fun showDeleteTaskFolderMetadataDialog(project: Project, target: AgentTaskFolderActionTarget): String? {
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

private class CreateTaskFolderDialog(
  project: Project,
  private val canCreateWithAgent: Boolean,
) : DialogWrapper(project) {
  private val nameField = JBTextField(32)
  private var createWithAgent = false

  init {
    title = AgentSessionsBundle.message("toolwindow.task.folder.create.dialog.title")
    setOKButtonText(AgentSessionsBundle.message("toolwindow.task.folder.create.dialog.create"))
    init()
    initValidation()
  }

  override fun createCenterPanel(): JComponent {
    return JPanel(GridBagLayout()).apply {
      border = JBUI.Borders.empty(8)
      addMetadataRow(0, AgentSessionsBundle.message("toolwindow.task.folder.create.dialog.message"), nameField)
    }
  }

  override fun createActions(): Array<Action> {
    val createWithAgentAction = object : DialogWrapperAction(AgentSessionsBundle.message("toolwindow.task.folder.create.dialog.with.agent")) {
      init {
        isEnabled = canCreateWithAgent
      }

      override fun doAction(e: ActionEvent?) {
        createWithAgent = true
        doOKAction()
        if (!isOK) {
          createWithAgent = false
        }
      }
    }
    return arrayOf(okAction, createWithAgentAction, cancelAction)
  }

  override fun getPreferredFocusedComponent(): JComponent = nameField

  override fun doValidate(): ValidationInfo? {
    if (nameField.text.isBlank()) {
      return ValidationInfo(AgentSessionsBundle.message("toolwindow.task.folder.dialog.validation.empty"), nameField)
    }
    return null
  }

  fun createRequest(): CreateTaskFolderRequest {
    return CreateTaskFolderRequest(name = nameField.text, createWithAgent = createWithAgent)
  }
}

private class TaskFolderMetadataDialog(
  project: Project,
  private val target: AgentTaskFolderActionTarget,
) : DialogWrapper(project) {
  private val keyCombo = ComboBox<@NlsSafe String>().apply {
    model = DefaultComboBoxModel(taskFolderMetadataKeyOptions(target.metadata))
    isEditable = true
  }
  private val valueField = JBTextField(32)
  private var loadedKey: String? = null

  init {
    title = AgentSessionsBundle.message("toolwindow.task.folder.metadata.set.dialog.title")
    val initialKey = initialMetadataKey(target.metadata)
    keyCombo.selectedItem = initialKey
    loadedKey = initialKey
    valueField.text = target.metadata[initialKey].orEmpty()
    keyCombo.addActionListener { handleKeyChanged() }
    init()
    initValidation()
  }

  override fun createCenterPanel(): JComponent {
    return JPanel(GridBagLayout()).apply {
      border = JBUI.Borders.empty(8)
      addMetadataRow(0, AgentSessionsBundle.message("toolwindow.task.folder.metadata.key.dialog.message"), keyCombo)
      addMetadataRow(1, AgentSessionsBundle.message("toolwindow.task.folder.metadata.value.dialog.message"), valueField)
    }
  }

  override fun getPreferredFocusedComponent(): JComponent = keyCombo

  override fun doValidate(): ValidationInfo? {
    if (metadataKey().isBlank()) {
      return ValidationInfo(AgentSessionsBundle.message("toolwindow.task.folder.metadata.key.validation.empty"), keyCombo)
    }
    return null
  }

  fun metadataEdit(): TaskFolderMetadataEdit {
    return TaskFolderMetadataEdit(
      key = metadataKey(),
      value = valueField.text,
    )
  }

  private fun handleKeyChanged() {
    val key = metadataKey()
    if (key == loadedKey) return
    target.metadata[key]?.let { value ->
      valueField.text = value
    }
    loadedKey = key
  }

  private fun metadataKey(): @NlsSafe String {
    return keyCombo.editor.item?.toString().orEmpty()
  }
}

private fun JPanel.addMetadataRow(row: Int, labelText: @NlsContexts.Label String, component: JComponent) {
  val label = JBLabel(labelText).apply { setLabelFor(component) }
  add(label, GridBagConstraints().apply {
    gridx = 0
    gridy = row
    anchor = GridBagConstraints.WEST
    insets = JBUI.insets(0, 0, 8, 8)
  })
  add(component, GridBagConstraints().apply {
    gridx = 1
    gridy = row
    fill = GridBagConstraints.HORIZONTAL
    weightx = 1.0
    insets = JBUI.insetsBottom(8)
  })
}

private fun taskFolderMetadataKeyOptions(metadata: Map<String, String>): Array<@NlsSafe String> {
  val keys = LinkedHashSet<@NlsSafe String>()
  keys.addAll(TASK_FOLDER_METADATA_KEY_PRESETS)
  metadata.keys.filterTo(keys) { key -> key.isNotBlank() }
  return keys.toTypedArray()
}

private fun initialMetadataKey(metadata: Map<String, String>): @NlsSafe String {
  return TASK_FOLDER_METADATA_KEY_PRESETS.firstOrNull(metadata::containsKey)
         ?: metadata.keys.firstOrNull { key -> key.isNotBlank() }
         ?: TASK_FOLDER_METADATA_KEY_PRESETS.first()
}
