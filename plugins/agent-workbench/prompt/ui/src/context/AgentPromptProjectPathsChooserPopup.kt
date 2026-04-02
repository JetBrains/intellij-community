// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.context

import com.intellij.agent.workbench.prompt.ui.AgentPromptBundle
import com.intellij.ide.IdeBundle
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.AbstractProjectTreeStructure
import com.intellij.ide.projectView.impl.AbstractProjectViewPane
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase
import com.intellij.ide.projectView.impl.nodes.AbstractProjectNode
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.scratch.RootType
import com.intellij.ide.scratch.ScratchTreeStructureProvider
import com.intellij.ide.scratch.ScratchesNamedScope
import com.intellij.ide.util.TreeFileChooserSupport
import com.intellij.ide.util.gotoByName.ChooseByNamePanel
import com.intellij.ide.util.gotoByName.ChooseByNamePopupComponent
import com.intellij.ide.util.gotoByName.GotoFileModel
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.TabbedPaneWrapper
import com.intellij.ui.TreeUIHelper
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.tree.TreeVisitor
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.concurrency.collectResults
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

private const val SEARCH_TAB_INDEX = 1

internal fun showProjectPathsChooserPopup(
  project: Project,
  scopedRootPaths: List<String>,
  initialSelection: List<ManualPathSelectionEntry>,
  initialTreePreselection: ManualPathSelectionEntry?,
  anchorComponent: Component,
  onConfirmed: (List<ManualPathSelectionEntry>) -> Unit,
) {
  val disposable = Disposer.newDisposable("AgentPromptProjectPathsChooserPopup")
  val selectionState = ManualPathSelectionState(initialSelection)
  val treeSupport = TreeFileChooserSupport.getInstance(project)
  val scopedRootPathsProvider = { scopedRootPaths }
  val fileCondition = Condition<PsiFile> { psiFile ->
    val virtualFile = psiFile.virtualFile ?: return@Condition false
    isSelectableVirtualFile(virtualFile, scopedRootPathsProvider())
  }

  val treeStructure = createTreeStructure(project, treeSupport, scopedRootPathsProvider, fileCondition)
  val treeModel = StructureTreeModel(treeStructure, disposable)
  treeModel.setComparator(treeSupport.getDefaultComparator())

  val tree = Tree(AsyncTreeModel(treeModel, disposable)).apply {
    isRootVisible = false
    showsRootHandles = true
    expandRow(0)
    selectionModel.selectionMode = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION
    cellRenderer = NodeRenderer()
  }
  TreeUIHelper.getInstance().installTreeSpeedSearch(tree)

  val treeScrollPane = ScrollPaneFactory.createScrollPane(tree).apply {
    preferredSize = JBUI.size(500, 300)
  }

  val searchDummyPanel = JPanel(BorderLayout())
  val searchPanel = ScopedSearchByNamePanel(project, scopedRootPathsProvider, searchDummyPanel, selectionState)
  Disposer.register(disposable, searchPanel)

  val tabbedPane = TabbedPaneWrapper(disposable)
  tabbedPane.addTab(IdeBundle.message("tab.chooser.project"), treeScrollPane)
  tabbedPane.addTab(IdeBundle.message("tab.chooser.search.by.name"), searchDummyPanel)
  val popupContent = JPanel(BorderLayout()).apply {
    add(tabbedPane.component, BorderLayout.CENTER)
  }

  var restoringTreeSelection = false
  var searchPanelInitialized = false
  lateinit var confirmSelection: () -> Boolean

  val popup = JBPopupFactory.getInstance()
    .createComponentPopupBuilder(popupContent, tree)
    .setMovable(true)
    .setResizable(true)
    .setRequestFocus(true)
    .setFocusable(true)
    .setCancelOnClickOutside(true)
    .setDimensionServiceKey(project, "AgentWorkbench.Prompt.FilesAndFoldersChooserPopup", false)
    .setAdText(AgentPromptBundle.message("manual.context.paths.selection.hint"))
    .createPopup()
  Disposer.register(popup, disposable)

  fun restoreTreeSelectionFromState() {
    restoreTreeSelection(
      project = project,
      tree = tree,
      selection = resolveTreeSelectionToRestore(selectionState.snapshot(), initialTreePreselection),
      scopedRootPaths = scopedRootPathsProvider(),
      popup = popup,
      beforeRestore = { restoringTreeSelection = true },
      afterRestore = { restoringTreeSelection = false },
    )
  }

  fun ensureSearchPanelInitialized() {
    if (searchPanelInitialized || project.isDisposed || popup.isDisposed) {
      return
    }

    searchPanel.invoke(
      object : ChooseByNamePopupComponent.MultiElementsCallback() {
        override fun elementsChosen(elements: MutableList<Any>) {
          confirmSelection()
        }
      },
      ModalityState.stateForComponent(tabbedPane.component),
      true,
    )
    searchPanelInitialized = true
  }

  val syncTreeSelection = {
    selectionState.addTreeSelection(collectTreeSelection(tree, treeSupport, scopedRootPathsProvider()))
  }

  confirmSelection = fun(): Boolean {
    val normalized = collectConfirmedSelection(
      isSearchTabSelected = tabbedPane.selectedIndex == SEARCH_TAB_INDEX,
      selectionState = selectionState,
      syncTreeSelection = syncTreeSelection,
      syncSearchSelection = {
        ensureSearchPanelInitialized()
        searchPanel.syncSelectionFromVisibleResults()
      },
    )
    if (normalized.isEmpty()) {
      return false
    }
    popup.cancel()
    onConfirmed(normalized)
    return true
  }

  installConfirmSelectionOnEnter(tree, confirmSelection)

  var selectedTabIndex = tabbedPane.selectedIndex
  tabbedPane.addChangeListener {
    val previousSelectedIndex = selectedTabIndex
    selectedTabIndex = tabbedPane.selectedIndex
    handleChooserTabSelectionChanged(
      previousSelectedIndex = previousSelectedIndex,
      selectedIndex = selectedTabIndex,
      syncTreeSelection = {
        if (!restoringTreeSelection) {
          syncTreeSelection()
        }
      },
      ensureSearchPanelInitialized = { ensureSearchPanelInitialized() },
      restoreSearchSelection = { searchPanel.restoreVisibleSelectionFromState() },
      restoreTreeSelection = { restoreTreeSelectionFromState() },
      requestTreeFocus = { tree.requestFocusInWindow() },
    )
  }

  popup.showUnderneathOf(anchorComponent)

  SwingUtilities.invokeLater {
    if (project.isDisposed || popup.isDisposed) return@invokeLater
    if (resolveTreeSelectionToRestore(selectionState.snapshot(), initialTreePreselection).isNotEmpty() && tabbedPane.selectedIndex != SEARCH_TAB_INDEX) {
      restoreTreeSelectionFromState()
    }
    if (tabbedPane.selectedIndex != SEARCH_TAB_INDEX) {
      tree.requestFocusInWindow()
    }
  }
}

internal fun resolveTreeSelectionToRestore(
  selection: List<ManualPathSelectionEntry>,
  initialTreePreselection: ManualPathSelectionEntry?,
): List<ManualPathSelectionEntry> {
  return selection.ifEmpty {
    initialTreePreselection?.let(::listOf).orEmpty()
  }
}

internal fun handleChooserTabSelectionChanged(
  previousSelectedIndex: Int,
  selectedIndex: Int,
  syncTreeSelection: () -> Unit,
  ensureSearchPanelInitialized: () -> Unit,
  restoreSearchSelection: () -> Unit,
  restoreTreeSelection: () -> Unit,
  requestTreeFocus: () -> Unit,
) {
  if (previousSelectedIndex != SEARCH_TAB_INDEX && selectedIndex == SEARCH_TAB_INDEX) {
    syncTreeSelection()
  }

  if (selectedIndex == SEARCH_TAB_INDEX) {
    ensureSearchPanelInitialized()
    restoreSearchSelection()
  }
  else {
    restoreTreeSelection()
    requestTreeFocus()
  }
}

internal fun collectConfirmedSelection(
  isSearchTabSelected: Boolean,
  selectionState: ManualPathSelectionState,
  syncTreeSelection: () -> Unit,
  syncSearchSelection: () -> Unit,
): List<ManualPathSelectionEntry> {
  if (isSearchTabSelected) {
    syncSearchSelection()
  }
  else {
    syncTreeSelection()
  }
  return selectionState.snapshot()
}

internal fun installConfirmSelectionOnEnter(
  tree: Tree,
  onConfirmSelection: () -> Boolean,
) {
  val enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)
  val fallbackListener = tree.getActionForKeyStroke(enter)
  tree.registerKeyboardAction({ event ->
    val handled = onConfirmSelection()
    if (!handled) {
      fallbackListener?.actionPerformed(event)
    }
  }, enter, JComponent.WHEN_FOCUSED)
}

private fun collectTreeSelection(
  tree: Tree,
  treeSupport: TreeFileChooserSupport,
  scopedRootPaths: List<String>,
): List<ManualPathSelectionEntry> {
  return tree.selectionPaths.orEmpty()
    .mapNotNull { resolveSelectableVirtualFile(it, treeSupport, scopedRootPaths) }
    .filter { isSelectableVirtualFile(it, scopedRootPaths) }
    .map { ManualPathSelectionEntry(path = it.path, isDirectory = it.isDirectory) }
}

internal fun resolveSelectableVirtualFile(
  selectionPath: TreePath,
  treeSupport: TreeFileChooserSupport,
  scopedRootPaths: List<String>,
): VirtualFile? {
  val pathComponents = selectionPath.path
  for (size in pathComponents.size downTo 1) {
    val candidatePath = TreePath(pathComponents.copyOfRange(0, size))
    val virtualFile = treeSupport.getVirtualFile(candidatePath) ?: continue
    if (isSelectableVirtualFile(virtualFile, scopedRootPaths)) {
      return virtualFile
    }
  }
  return null
}

private fun restoreTreeSelection(
  project: Project,
  tree: Tree,
  selection: List<ManualPathSelectionEntry>,
  scopedRootPaths: List<String>,
  popup: JBPopup,
  beforeRestore: () -> Unit = {},
  afterRestore: () -> Unit = {},
) {
  if (selection.isEmpty()) {
    beforeRestore()
    try {
      tree.clearSelection()
    }
    finally {
      afterRestore()
    }
    return
  }

  beforeRestore()
  val selectionVisitors = runReadActionBlocking {
    selection.mapNotNull { entry ->
      val virtualFile = findRestorableVirtualFile(entry.path) ?: return@mapNotNull null
      if (!isSelectableVirtualFile(virtualFile, scopedRootPaths)) return@mapNotNull null
      createRestorableSelectionVisitor(virtualFile)
    }
  }

  if (selectionVisitors.isEmpty()) {
    try {
      tree.clearSelection()
    }
    finally {
      afterRestore()
    }
    return
  }

  selectionVisitors.map { visitor -> TreeUtil.promiseMakeVisible(tree, visitor) }
    .collectResults(ignoreErrors = true)
    .onSuccess { paths ->
      ApplicationManager.getApplication().invokeLater({
                                                        if (project.isDisposed || popup.isDisposed) return@invokeLater
                                                        applyTreeSelection(tree, paths, afterRestore)
                                                      }, ModalityState.stateForComponent(tree))
    }
    .onError {
      ApplicationManager.getApplication().invokeLater(afterRestore, ModalityState.stateForComponent(tree))
    }
}

internal fun findRestorableVirtualFile(path: String): VirtualFile? {
  val fileSystem = LocalFileSystem.getInstance()
  return fileSystem.findFileByPath(path) ?: fileSystem.refreshAndFindFileByPath(path)
}

internal fun createRestorableSelectionVisitor(virtualFile: VirtualFile): TreeVisitor {
  val rootType = RootType.forFile(virtualFile)
  if (rootType != null) {
    val scratchVisitor = createScratchSelectionVisitor(virtualFile, rootType)
    if (scratchVisitor != null) {
      return scratchVisitor
    }
  }
  return AbstractProjectViewPane.createVisitor(virtualFile)
}

private fun createScratchSelectionVisitor(virtualFile: VirtualFile, rootType: RootType): TreeVisitor? {
  val scratchRoot = ScratchTreeStructureProvider.getVirtualFile(rootType) ?: return null
  val targetSegments = buildScratchSelectionSegments(virtualFile, scratchRoot, rootType) ?: return null
  return ScratchSelectionTreeVisitor(targetSegments)
}

private fun buildScratchSelectionSegments(
  virtualFile: VirtualFile,
  scratchRoot: VirtualFile,
  rootType: RootType,
): List<ScratchSelectionSegment>? {
  val pathSegments = ArrayDeque<String>()
  var current: VirtualFile? = virtualFile
  while (current != null && current != scratchRoot) {
    pathSegments.addFirst(current.path)
    current = current.parent
  }
  if (current != scratchRoot) {
    return null
  }

  return buildList {
    add(ScratchSelectionSegment.ScratchesContainer)
    add(ScratchSelectionSegment.RootType(rootType.id))
    pathSegments.forEach { add(ScratchSelectionSegment.Path(it)) }
  }
}

private sealed interface ScratchSelectionSegment {
  data object ScratchesContainer : ScratchSelectionSegment

  data class RootType(val id: String) : ScratchSelectionSegment

  data class Path(val path: String) : ScratchSelectionSegment
}

private class ScratchSelectionTreeVisitor(
  private val targetSegments: List<ScratchSelectionSegment>,
) : TreeVisitor {
  override fun visit(path: TreePath): TreeVisitor.Action {
    val currentSegments = path.path.mapNotNull(::scratchSelectionSegmentFor)
    if (!targetSegments.hasPrefix(currentSegments)) {
      return TreeVisitor.Action.SKIP_CHILDREN
    }
    return if (currentSegments.size == targetSegments.size) TreeVisitor.Action.INTERRUPT else TreeVisitor.Action.CONTINUE
  }
}

private fun scratchSelectionSegmentFor(component: Any?): ScratchSelectionSegment? {
  val userObject = (component as? DefaultMutableTreeNode)?.userObject ?: component
  val node = userObject as? ProjectViewNode<*> ?: return null
  val value = node.value
  return when (value) {
    ScratchesNamedScope.scratchesAndConsoles(), ScratchesNamedScope.ID -> ScratchSelectionSegment.ScratchesContainer
    is RootType -> ScratchSelectionSegment.RootType(value.id)
    else -> {
      val virtualFile = node.virtualFile ?: return null
      if (RootType.forFile(virtualFile) == null) return null
      ScratchSelectionSegment.Path(virtualFile.path)
    }
  }
}

private fun <T> List<T>.hasPrefix(prefix: List<T>): Boolean {
  if (prefix.size > size) {
    return false
  }
  return prefix.indices.all { index -> this[index] == prefix[index] }
}

private fun applyTreeSelection(
  tree: Tree,
  paths: List<TreePath>,
  afterApply: () -> Unit = {},
) {
  if (paths.isEmpty()) {
    try {
      tree.clearSelection()
    }
    finally {
      afterApply()
    }
    return
  }

  try {
    tree.selectionPaths = paths.toTypedArray()
    paths.firstOrNull()?.let { path ->
      TreeUtil.scrollToVisible(tree, path, false)
    }
  }
  finally {
    afterApply()
  }
}

private fun createTreeStructure(
  project: Project,
  treeSupport: TreeFileChooserSupport,
  scopedRootPathsProvider: () -> List<String>,
  fileCondition: Condition<PsiFile>,
): ProjectAbstractTreeStructureBase {
  return object : AbstractProjectTreeStructure(project) {
    override fun createRoot(project: Project, settings: ViewSettings): AbstractTreeNode<*> {
      return treeSupport.createRoot(settings)
    }

    override fun isHideEmptyMiddlePackages(): Boolean = true

    override fun isShowLibraryContents(): Boolean = false

    override fun isShowModules(): Boolean = false

    override fun getChildElements(element: Any): Array<Any> {
      return filterScopedProjectNodes(super.getChildElements(element), scopedRootPathsProvider(), fileCondition)
    }

    override fun getParentElement(element: Any): Any? {
      val abstractProjectNode = rootElement as AbstractProjectNode
      if (element == abstractProjectNode) return null

      val psiDirectory = when (element) {
        is PsiFileNode -> element.value?.parent
        is PsiDirectoryNode -> {
          val basePath = project.basePath
          if (basePath != null && FileUtil.pathsEqual(element.value?.virtualFile?.path, basePath)) {
            return abstractProjectNode
          }
          element.value?.parent
        }
        else -> null
      }
      return when {
        psiDirectory != null -> PsiDirectoryNode(project, psiDirectory, abstractProjectNode.settings)
        element is AbstractTreeNode<*> -> element.parent
        else -> null
      }
    }
  }
}

private class ScopedSearchByNamePanel(
  project: Project,
  private val scopedRootPathsProvider: () -> List<String>,
  private val dummyPanel: JPanel,
  private val selectionState: ManualPathSelectionState,
) : ChooseByNamePanel(project, ScopedGotoFileModel(project, scopedRootPathsProvider), "", false, null) {

  private var restoringVisibleSelection = false

  override fun initUI(
    callback: ChooseByNamePopupComponent.Callback,
    modalityState: ModalityState,
    allowMultipleSelection: Boolean,
  ) {
    super.initUI(callback, modalityState, allowMultipleSelection)
    dummyPanel.add(panel, BorderLayout.CENTER)
    myList.model.addListDataListener(object : ListDataListener {
      override fun intervalAdded(event: ListDataEvent) = restoreVisibleSelectionFromState()

      override fun intervalRemoved(event: ListDataEvent) = restoreVisibleSelectionFromState()

      override fun contentsChanged(event: ListDataEvent) = restoreVisibleSelectionFromState()
    })
  }

  fun restoreVisibleSelectionFromState() {
    if (restoringVisibleSelection) {
      return
    }

    val selectedIndices = (0 until myList.model.size)
      .filter { index ->
        val entry = selectionEntryFor(myList.model.getElementAt(index), scopedRootPathsProvider()) ?: return@filter false
        selectionState.contains(entry.path)
      }
      .toIntArray()

    restoringVisibleSelection = true
    try {
      myList.setSelectedIndices(selectedIndices)
    }
    finally {
      restoringVisibleSelection = false
    }
  }

  fun syncSelectionFromVisibleResults() {
    selectionState.addSearchSelection(chosenElements.mapNotNull { element -> selectionEntryFor(element, scopedRootPathsProvider()) })
    restoreVisibleSelectionFromState()
  }

  override fun chosenElementMightChange() {
    if (!restoringVisibleSelection) {
      syncSelectionFromVisibleResults()
    }
  }

  override fun showTextFieldPanel() {}

  override fun close(isOk: Boolean) {}
}

private fun selectionEntryFor(element: Any?, scopedRootPaths: List<String>): ManualPathSelectionEntry? {
  val file = (element as? PsiFile)?.virtualFile ?: return null
  if (!isSelectableVirtualFile(file, scopedRootPaths)) {
    return null
  }
  return ManualPathSelectionEntry(path = file.path, isDirectory = file.isDirectory)
}

private class ScopedGotoFileModel(
  project: Project,
  private val scopedRootPathsProvider: () -> Collection<String>,
) : GotoFileModel(project) {
  override fun acceptItem(item: NavigationItem): Boolean {
    if (item !is PsiFile) {
      return false
    }
    val virtualFile = item.virtualFile ?: return false
    return virtualFile.isInLocalFileSystem && isUnderAnyRoot(virtualFile.path, scopedRootPathsProvider()) && super.acceptItem(item)
  }

  override fun loadInitialCheckBoxState(): Boolean = false

  override fun saveInitialCheckBoxState(state: Boolean) {}
}

private fun filterScopedProjectNodes(
  elements: Array<Any>,
  scopedRootPaths: List<String>,
  fileCondition: Condition<PsiFile>,
): Array<Any> {
  return elements.filter { node ->
    if (node !is ProjectViewNode<*>) return@filter true

    val virtualFile = node.virtualFile
    if (virtualFile != null && (containsScopedRootPath(virtualFile.path, scopedRootPaths) || isSelectableVirtualFile(virtualFile,
                                                                                                                     scopedRootPaths))) {
      return@filter true
    }
    node.canHaveChildrenMatching(fileCondition)
  }.toTypedArray()
}

private fun isSelectableVirtualFile(virtualFile: VirtualFile, scopedRootPaths: List<String>): Boolean {
  return virtualFile.isInLocalFileSystem && isUnderAnyRoot(virtualFile.path, scopedRootPaths)
}

private fun containsScopedRootPath(path: String, scopedRootPaths: List<String>): Boolean {
  return scopedRootPaths.any { rootPath ->
    FileUtil.isAncestor(path, rootPath, false) || FileUtil.pathsEqual(path, rootPath)
  }
}
