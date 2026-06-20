// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

// @spec community/plugins/agent-workbench/spec/chat/agent-chat-structure-view.spec.md

import com.intellij.agent.workbench.common.AgentWorkbenchActionIds
import com.intellij.agent.workbench.core.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TreeUIHelper
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.LeafState
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.ui.EdtInvocationManager
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.concurrent.CompletableFuture
import javax.swing.JTree
import javax.swing.KeyStroke
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.ToolTipManager
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

private val LOG = logger<AgentChatThreadOutlinePanel>()

class AgentChatThreadOutlinePanel(
  private val project: Project,
  outlineScope: CoroutineScope? = null,
  startSelectionSubscription: Boolean = true,
) : JPanel(BorderLayout()), Disposable, UiDataProvider {
  private val ownedJob = if (outlineScope == null) SupervisorJob() else null

  @Suppress("RAW_SCOPE_CREATION")
  private val cs = outlineScope ?: CoroutineScope(checkNotNull(ownedJob) + Dispatchers.Default)

  private var outlineModel: AgentChatThreadOutlineModel = AgentChatThreadOutlineModel.EMPTY
  private var outlineController: AgentChatThreadOutlineController? = null
  private var outlineControllerKey: AgentChatThreadOutlineControllerKey? = null
  private var selectedFile: AgentChatVirtualFile? = null
  private var selectedFileRefreshJob: Job? = null

  private val treeStructure = AgentChatThreadOutlineTreeStructure { outlineModel }
  private val structureTreeModel = StructureTreeModel(treeStructure, this)
  private val tree = object : Tree(AsyncTreeModel(structureTreeModel, this)), UiDataProvider {
    override fun getToolTipText(event: MouseEvent?): String? {
      val mouseEvent = event ?: return null
      val path = getPathForLocation(mouseEvent.x, mouseEvent.y) ?: return null
      val id = idFromPath(path) ?: return null
      val node = outlineNode(id) ?: return null
      return node.tooltip
    }

    override fun uiDataSnapshot(sink: DataSink) {
      this@AgentChatThreadOutlinePanel.uiDataSnapshot(sink)
    }
  }

  init {
    configureTree()
    add(ScrollPaneFactory.createScrollPane(tree, true).apply {
      horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    }, BorderLayout.CENTER)
    if (startSelectionSubscription) {
      startSelectedEditorSubscription()
    }
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[AgentChatThreadOutlineDataKeys.SELECTED_TARGET] = selectedTarget()
  }

  override fun dispose() {
    disposeSelectedFileRefreshJob()
    disposeOutlineController()
    selectedFile = null
    ownedJob?.cancel()
  }

  @TestOnly
  internal fun selectFileForTests(file: AgentChatVirtualFile?) {
    bindSelectedFile(file)
  }

  @TestOnly
  internal fun modelForTests(): AgentChatThreadOutlineModel = outlineModel

  @TestOnly
  internal fun navigateOutlineIdForTests(id: AgentChatThreadOutlineId): Boolean {
    val target = outlineModel.entriesById[id]?.node?.target ?: return false
    return navigateTarget(target)
  }

  @TestOnly
  internal fun canShowPopupForOutlineIdForTests(id: AgentChatThreadOutlineId): Boolean {
    val target = outlineModel.entriesById[id]?.node?.target ?: return false
    return canShowAgentChatThreadOutlineForkAction(target)
  }

  @TestOnly
  internal fun hasPopupKeyboardActivationForTests(): Boolean {
    return tree.getActionForKeyStroke(AGENT_CHAT_THREAD_OUTLINE_CONTEXT_MENU_KEY_STROKE) != null &&
           tree.getActionForKeyStroke(AGENT_CHAT_THREAD_OUTLINE_SHIFT_F10_KEY_STROKE) != null
  }

  private fun configureTree() {
    tree.isRootVisible = false
    tree.showsRootHandles = true
    tree.emptyText.text = AgentChatBundle.message("chat.thread.outline.no.selection")
    tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
    tree.cellRenderer = AgentChatThreadOutlineTreeCellRenderer(::outlineNode)
    TreeUtil.installActions(tree)
    TreeUIHelper.getInstance().installTreeSpeedSearch(tree)
    ToolTipManager.sharedInstance().registerComponent(tree)
    EditSourceOnDoubleClickHandler.install(tree) { navigateSelectedTarget() }
    installEnterKeyActivation()
    installPopupMenu()
    com.intellij.util.ui.tree.ExpandOnDoubleClick.DEFAULT.installOn(tree)
  }

  private fun installEnterKeyActivation() {
    val enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)
    val fallbackListener = tree.getActionForKeyStroke(enter)
    tree.registerKeyboardAction({ event ->
                                  val handled = navigateSelectedTarget()
                                  if (!handled) {
                                    fallbackListener?.actionPerformed(event)
                                  }
                                }, enter, 0)
  }

  private fun installPopupMenu() {
    tree.addMouseListener(object : PopupHandler() {
      override fun invokePopup(comp: Component, x: Int, y: Int) {
        showPopupAt(x = x, y = y)
      }
    })
    tree.registerKeyboardAction(
      { showPopupForSelectedRow() },
      AGENT_CHAT_THREAD_OUTLINE_CONTEXT_MENU_KEY_STROKE,
      WHEN_FOCUSED,
    )
    tree.registerKeyboardAction(
      { showPopupForSelectedRow() },
      AGENT_CHAT_THREAD_OUTLINE_SHIFT_F10_KEY_STROKE,
      WHEN_FOCUSED,
    )
  }

  private fun showPopupAt(x: Int, y: Int): Boolean {
    val path = tree.getPathForLocation(x, y) ?: return false
    if (!tree.selectionModel.isPathSelected(path)) {
      tree.selectionPath = path
    }
    val target = targetFromPath(path) ?: return false
    return showPopup(target = target, x = x, y = y)
  }

  private fun showPopupForSelectedRow(): Boolean {
    val path = TreeUtil.getSelectedPathIfOne(tree) ?: return false
    val target = targetFromPath(path) ?: return false
    val bounds = tree.getPathBounds(path)
    val x = bounds?.x ?: 0
    val y = bounds?.let { it.y + it.height } ?: 0
    return showPopup(target = target, x = x, y = y)
  }

  private fun showPopup(target: AgentChatThreadOutlineTarget, x: Int, y: Int): Boolean {
    if (!canShowAgentChatThreadOutlineForkAction(target)) {
      logHiddenThreadOutlinePopup(target)
      return false
    }
    val group = ActionManager.getInstance().getAction(AgentWorkbenchActionIds.Sessions.ThreadOutline.POPUP_GROUP) as? ActionGroup
                ?: return false
    val popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.TOOLWINDOW_POPUP, group)
    popupMenu.setTargetComponent(tree)
    popupMenu.component.show(tree, x, y)
    return true
  }

  private fun logHiddenThreadOutlinePopup(target: AgentChatThreadOutlineTarget) {
    val file = target.file
    val provider = file.provider
    val staticSupport = provider != null &&
                        !file.isPendingThread &&
                        target.source.canShowThreadOutlineForkAction(
                          path = file.projectPath,
                          threadId = file.threadId,
                          itemId = target.item.id,
                          subAgentId = file.subAgentId,
                          tabKey = file.tabKey,
                        )
    val liveSupport = staticSupport && target.source.canForkThreadFromOutlineItem(
      path = file.projectPath,
      threadId = file.threadId,
      itemId = target.item.id,
      subAgentId = file.subAgentId,
      tabKey = file.tabKey,
    )
    LOG.info(
      "Thread outline popup hidden: provider=${provider?.value}, path=${file.projectPath}, threadId=${file.threadId}, " +
      "itemId=${target.item.id}, itemKind=${target.item.kind}, subAgent=${file.subAgentId != null}, tabKey=${file.tabKey}, " +
      "pending=${file.isPendingThread}, staticForkSupport=$staticSupport, liveForkSupport=$liveSupport"
    )
  }

  private fun startSelectedEditorSubscription() {
    cs.launch {
      val manager = project.serviceAsync<FileEditorManager>()
      manager.selectedEditorFlow
        .map { selectedEditor -> selectedEditor?.file as? AgentChatVirtualFile }
        .distinctUntilChanged()
        .collect { file ->
          withContext(Dispatchers.EDT) {
            bindSelectedFile(file)
          }
        }
    }
  }

  private fun bindSelectedFile(file: AgentChatVirtualFile?, force: Boolean = false) {
    val fileChanged = selectedFile != file
    if (!force && !fileChanged) {
      return
    }
    selectedFile = file
    if (fileChanged) {
      restartSelectedFileRefreshSubscription(file)
    }
    disposeOutlineController()
    if (file == null) {
      setOutlineModel(AgentChatThreadOutlineModel.EMPTY)
      return
    }
    if (!file.canShowAgentChatThreadOutline()) {
      setOutlineModel(
        AgentChatThreadOutlineModel.status(
          title = file.threadTitle,
          status = AgentChatBundle.message("chat.thread.outline.unavailable"),
          icon = providerIcon(provider = file.provider, threadActivity = file.threadActivity),
        )
      )
      return
    }
    setOutlineModel(
      AgentChatThreadOutlineModel.status(
        title = file.threadTitle,
        status = AgentChatBundle.message("chat.thread.outline.loading"),
        icon = providerIcon(provider = file.provider, threadActivity = file.threadActivity),
      )
    )
    val controllerKey = file.toThreadOutlineControllerKey()
    outlineControllerKey = controllerKey
    outlineController = AgentChatThreadOutlineController(
      file = file,
      cs = cs,
      applyModel = ::setOutlineModel,
    ).also { controller ->
      Disposer.register(this, controller)
      controller.start()
    }
  }

  private fun restartSelectedFileRefreshSubscription(file: AgentChatVirtualFile?) {
    disposeSelectedFileRefreshJob()
    val provider = file?.provider ?: return
    selectedFileRefreshJob = cs.launch {
      agentChatScopedRefreshSignals(provider).collect { updateEvent ->
        withContext(Dispatchers.EDT) {
          refreshSelectedFileForUpdate(updateEvent)
        }
      }
    }
  }

  private fun disposeSelectedFileRefreshJob() {
    selectedFileRefreshJob?.cancel()
    selectedFileRefreshJob = null
  }

  private fun refreshSelectedFileForUpdate(updateEvent: AgentSessionSourceUpdateEvent) {
    val file = selectedFile ?: return
    if (!updateEvent.matches(file)) {
      return
    }

    val currentControllerKey = file.toThreadOutlineControllerKey()
    val controller = outlineController
    if (!file.canShowAgentChatThreadOutline() || controller == null || outlineControllerKey != currentControllerKey) {
      bindSelectedFile(file, force = true)
      return
    }

    controller.requestReload()
  }

  private fun disposeOutlineController() {
    val controller = outlineController
    outlineController = null
    outlineControllerKey = null
    controller?.let(Disposer::dispose)
  }

  private fun setOutlineModel(model: AgentChatThreadOutlineModel) {
    val previousModel = outlineModel
    outlineModel = model
    invalidateTreeModel(diffAgentChatThreadOutlineModels(previousModel, model))
      .thenRun {
        EdtInvocationManager.invokeLaterIfNeeded {
          model.autoExpandIds.forEach { id ->
            structureTreeModel.expand(id, tree) { }
          }
        }
      }
  }

  private fun invalidateTreeModel(diff: AgentChatThreadOutlineModelDiff): CompletableFuture<*> {
    if (diff.rootChanged) {
      return structureTreeModel.invalidateAsync().handle { _, _ -> null }
    }

    var future: CompletableFuture<*> = CompletableFuture.completedFuture(null)
    diff.structureChangedIds.forEach { id ->
      future = future
        .handle { _, _ -> null }
        .thenCompose { structureTreeModel.invalidateAsync(id, true) }
    }
    diff.contentChangedIds.forEach { id ->
      future = future
        .handle { _, _ -> null }
        .thenCompose { structureTreeModel.invalidateAsync(id, false) }
    }
    return future.handle { _, _ -> null }
  }

  private fun navigateSelectedTarget(): Boolean {
    val target = selectedTarget() ?: return false
    return navigateTarget(target)
  }

  private fun navigateTarget(target: AgentChatThreadOutlineTarget): Boolean {
    if (!canNavigate(target)) {
      return false
    }
    cs.launch {
      target.source.navigateThreadOutlineItem(
        path = target.file.projectPath,
        threadId = target.file.threadId,
        itemId = target.item.id,
        subAgentId = target.file.subAgentId,
        tabKey = target.file.tabKey,
      )
    }
    return true
  }

  private fun selectedTarget(): AgentChatThreadOutlineTarget? {
    return targetFromPath(TreeUtil.getSelectedPathIfOne(tree))
  }

  private fun targetFromPath(path: TreePath?): AgentChatThreadOutlineTarget? {
    val id = idFromPath(path) ?: return null
    return outlineNode(id)?.target
  }

  private fun canNavigate(target: AgentChatThreadOutlineTarget): Boolean {
    return target.source.canNavigateThreadOutlineItem(
      path = target.file.projectPath,
      threadId = target.file.threadId,
      itemId = target.item.id,
      subAgentId = target.file.subAgentId,
      tabKey = target.file.tabKey,
    )
  }

  private fun idFromPath(path: TreePath?): AgentChatThreadOutlineId? {
    return path?.lastPathComponent?.let(::extractAgentChatThreadOutlineId)
  }

  private fun outlineNode(id: AgentChatThreadOutlineId): AgentChatThreadOutlineNode? {
    return outlineModel.entriesById[id]?.node
  }
}

private data class AgentChatThreadOutlineControllerKey(
  val provider: String,
  val projectPath: String,
  val threadId: String,
  val subAgentId: String?,
)

private fun AgentChatVirtualFile.toThreadOutlineControllerKey(): AgentChatThreadOutlineControllerKey? {
  val provider = provider ?: return null
  val normalizedProjectPath = normalizeAgentWorkbenchPath(projectPath).takeIf(String::isNotBlank) ?: return null
  val threadId = threadId.takeIf(String::isNotBlank) ?: return null
  return AgentChatThreadOutlineControllerKey(
    provider = provider.value,
    projectPath = normalizedProjectPath,
    threadId = threadId,
    subAgentId = subAgentId,
  )
}

private fun AgentSessionSourceUpdateEvent.matches(file: AgentChatVirtualFile): Boolean {
  val normalizedProjectPath = normalizeAgentWorkbenchPath(file.projectPath).takeIf(String::isNotBlank) ?: return false
  val scopedPaths = scopedPaths
  if (scopedPaths != null && scopedPaths.none { path -> normalizeAgentWorkbenchPath(path) == normalizedProjectPath }) {
    return false
  }

  val threadIds = threadIds ?: return true
  if (threadIds.isEmpty()) {
    return true
  }
  val fileThreadIds = sequenceOf(file.threadId, file.sessionId)
    .map { id -> id.trim() }
    .filter { id -> id.isNotEmpty() }
    .toSet()
  return fileThreadIds.isNotEmpty() && threadIds.any { threadId -> threadId.trim() in fileThreadIds }
}

private class AgentChatThreadOutlineController(
  private val file: AgentChatVirtualFile,
  private val cs: CoroutineScope,
  private val applyModel: (AgentChatThreadOutlineModel) -> Unit,
) : Disposable {
  private val reloadRequests = Channel<Unit>(Channel.CONFLATED)
  private val jobs = mutableListOf<Job>()

  @Volatile
  private var disposed = false

  fun start() {
    jobs += cs.launch {
      while (!disposed) {
        val model = loadModel()
        withContext(Dispatchers.EDT) {
          if (!disposed) {
            applyModel(model)
          }
        }
        if (reloadRequests.receiveCatching().isClosed) {
          return@launch
        }
      }
    }
    jobs += cs.launch {
      val provider = file.provider ?: return@launch
      val source = AgentSessionProviders.find(provider)?.sessionSource ?: return@launch
      val projectPath = file.projectPath.takeIf(String::isNotBlank) ?: return@launch
      val threadId = file.threadId.takeIf(String::isNotBlank) ?: return@launch
      source.activeThreadUpdateEvents(path = projectPath, threadId = threadId).collect {
        reloadRequests.trySend(Unit)
      }
    }
  }

  fun requestReload() {
    reloadRequests.trySend(Unit)
  }

  override fun dispose() {
    disposed = true
    reloadRequests.close()
    jobs.forEach(Job::cancel)
    jobs.clear()
  }

  private suspend fun loadModel(): AgentChatThreadOutlineModel {
    val provider = file.provider
    val source = provider?.let { AgentSessionProviders.find(it)?.sessionSource }
    if (source == null) {
      return unavailableModel()
    }
    val outline = try {
      source.loadThreadOutline(path = file.projectPath, threadId = file.threadId, subAgentId = file.subAgentId)
    }
    catch (e: Throwable) {
      if (e is CancellationException) throw e
      null
    }
    return if (outline == null) unavailableModel() else buildAgentChatThreadOutlineModel(file = file, source = source, outline = outline)
  }

  private fun unavailableModel(): AgentChatThreadOutlineModel {
    return AgentChatThreadOutlineModel.status(
      title = file.threadTitle,
      status = AgentChatBundle.message("chat.thread.outline.unavailable"),
      icon = providerIcon(provider = file.provider, threadActivity = file.threadActivity),
    )
  }
}

private object AgentChatThreadOutlineRootElement

private fun extractAgentChatThreadOutlineId(value: Any?): AgentChatThreadOutlineId? {
  val descriptor = TreeUtil.getUserObject(NodeDescriptor::class.java, value) ?: return null
  return descriptor.element as? AgentChatThreadOutlineId
}

private class AgentChatThreadOutlineTreeStructure(
  private val modelProvider: () -> AgentChatThreadOutlineModel,
) : AbstractTreeStructure() {
  override fun getRootElement(): Any = AgentChatThreadOutlineRootElement

  override fun getChildElements(element: Any): Array<Any> {
    val model = modelProvider()
    return when (element) {
      AgentChatThreadOutlineRootElement -> model.rootIds.toTypedArray()
      is AgentChatThreadOutlineId -> model.entriesById[element]?.childIds?.toTypedArray() ?: emptyArray()
      else -> emptyArray()
    }
  }

  override fun getParentElement(element: Any): Any? {
    if (element === AgentChatThreadOutlineRootElement) return null
    val id = element as? AgentChatThreadOutlineId ?: return null
    val entry = modelProvider().entriesById[id] ?: return null
    return entry.parentId ?: AgentChatThreadOutlineRootElement
  }

  override fun createDescriptor(element: Any, parentDescriptor: NodeDescriptor<*>?): NodeDescriptor<*> {
    return AgentChatThreadOutlineNodeDescriptor(parentDescriptor = parentDescriptor, element = element, modelProvider = modelProvider)
  }

  override fun commit() = Unit

  override fun hasSomethingToCommit(): Boolean = false

  override fun getLeafState(element: Any): LeafState {
    if (element === AgentChatThreadOutlineRootElement) return LeafState.NEVER
    val id = element as? AgentChatThreadOutlineId ?: return LeafState.DEFAULT
    val entry = modelProvider().entriesById[id] ?: return LeafState.DEFAULT
    return if (entry.childIds.isEmpty()) LeafState.ALWAYS else LeafState.DEFAULT
  }

  override fun isValid(element: Any): Boolean {
    return element === AgentChatThreadOutlineRootElement || element is AgentChatThreadOutlineId && modelProvider().entriesById.containsKey(
      element)
  }
}

private class AgentChatThreadOutlineNodeDescriptor(
  parentDescriptor: NodeDescriptor<*>?,
  private val element: Any,
  private val modelProvider: () -> AgentChatThreadOutlineModel,
) : NodeDescriptor<Any?>(null, parentDescriptor) {
  private var presentationHash: Int = computePresentationHash()

  init {
    myName = computeName()
  }

  override fun update(): Boolean {
    val nextHash = computePresentationHash()
    if (presentationHash == nextHash) {
      return false
    }
    presentationHash = nextHash
    myName = computeName()
    return true
  }

  override fun getElement(): Any = element

  override fun expandOnDoubleClick(): Boolean {
    if (element !is AgentChatThreadOutlineId) return true
    val node = modelProvider().entriesById[element]?.node ?: return true
    return node.target == null
  }

  private fun computePresentationHash(): Int {
    return when (element) {
      AgentChatThreadOutlineRootElement -> 0
      is AgentChatThreadOutlineId -> modelProvider().entriesById[element]?.node?.hashCode() ?: -1
      else -> 0
    }
  }

  private fun computeName(): String {
    if (element !is AgentChatThreadOutlineId) return ""
    val node = modelProvider().entriesById[element]?.node ?: return ""
    return listOfNotNull(node.title, node.timestamp, node.location).joinToString(" ")
  }
}

private class AgentChatThreadOutlineTreeCellRenderer(
  private val nodeResolver: (AgentChatThreadOutlineId) -> AgentChatThreadOutlineNode?,
) : ColoredTreeCellRenderer() {
  override fun customizeCellRenderer(
    tree: JTree,
    value: Any?,
    selected: Boolean,
    expanded: Boolean,
    leaf: Boolean,
    row: Int,
    hasFocus: Boolean,
  ) {
    val id = extractAgentChatThreadOutlineId(value) ?: return
    val node = nodeResolver(id) ?: return
    icon = node.icon
    toolTipText = node.tooltip
    append(node.title, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    val statusParts = ArrayList<@NlsSafe String>(2)
    val timestamp = node.timestamp
    if (!timestamp.isNullOrBlank()) {
      append("  $timestamp", SimpleTextAttributes.GRAYED_ATTRIBUTES)
      statusParts += timestamp
    }
    val location = node.location
    if (!location.isNullOrBlank()) {
      append("  $location", SimpleTextAttributes.GRAYED_ATTRIBUTES)
      statusParts += location
    }
    if (statusParts.isNotEmpty()) {
      setAccessibleStatusText(joinThreadOutlineAccessibleStatus(statusParts))
    }
  }
}

private fun joinThreadOutlineAccessibleStatus(parts: List<@NlsSafe String>): @NlsSafe String {
  return parts.joinToString(" ")
}

private val AGENT_CHAT_THREAD_OUTLINE_CONTEXT_MENU_KEY_STROKE = KeyStroke.getKeyStroke(KeyEvent.VK_CONTEXT_MENU, 0)
private val AGENT_CHAT_THREAD_OUTLINE_SHIFT_F10_KEY_STROKE = KeyStroke.getKeyStroke(KeyEvent.VK_F10, InputEvent.SHIFT_DOWN_MASK)
