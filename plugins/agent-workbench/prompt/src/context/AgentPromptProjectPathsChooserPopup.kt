// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.context

import com.intellij.agent.workbench.prompt.AgentPromptBundle
import com.intellij.ide.IdeBundle
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.AbstractProjectTreeStructure
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase
import com.intellij.ide.projectView.impl.nodes.AbstractProjectNode
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
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
import com.intellij.psi.PsiManager
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.TabbedPaneWrapper
import com.intellij.ui.TreeUIHelper
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
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
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

private const val SEARCH_TAB_INDEX = 1

internal fun showProjectPathsChooserPopup(
  project: Project,
  scopedRoots: List<VirtualFile>,
  initialSelection: List<ManualPathSelectionEntry>,
  anchorComponent: Component,
  onConfirmed: (List<ManualPathSelectionEntry>) -> Unit,
) {
  val disposable = Disposer.newDisposable("AgentPromptProjectPathsChooserPopup")
  val selectionState = ManualPathSelectionState(initialSelection)
  val scopedRootPaths = scopedRoots.map(VirtualFile::getPath)
  val treeSupport = TreeFileChooserSupport.getInstance(project)
  val fileCondition = Condition<PsiFile> { psiFile ->
    val virtualFile = psiFile.virtualFile ?: return@Condition false
    isSelectableVirtualFile(virtualFile, scopedRootPaths)
  }

  val treeStructure = createTreeStructure(project, treeSupport, scopedRootPaths, fileCondition)
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
  val searchPanel = ScopedSearchByNamePanel(project, scopedRootPaths, searchDummyPanel, selectionState)
  Disposer.register(disposable, searchPanel)

  val tabbedPane = TabbedPaneWrapper(disposable)
  tabbedPane.addTab(IdeBundle.message("tab.chooser.project"), treeScrollPane)
  tabbedPane.addTab(IdeBundle.message("tab.chooser.search.by.name"), searchDummyPanel)

  var restoringTreeSelection = false
  var searchPanelInitialized = false
  lateinit var confirmSelection: () -> Boolean

  val popup = JBPopupFactory.getInstance()
    .createComponentPopupBuilder(tabbedPane.component, tree)
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
      treeModel = treeModel,
      selection = selectionState.snapshot(),
      scopedRootPaths = scopedRootPaths,
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
    selectionState.addTreeSelection(collectTreeSelection(tree, treeSupport, scopedRootPaths))
  }

  tree.addTreeSelectionListener {
    if (!restoringTreeSelection) {
      syncTreeSelection()
    }
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

  tabbedPane.addChangeListener {
    if (tabbedPane.selectedIndex == SEARCH_TAB_INDEX) {
      ensureSearchPanelInitialized()
      searchPanel.restoreVisibleSelectionFromState()
    }
    else {
      restoreTreeSelectionFromState()
      tree.requestFocusInWindow()
    }
  }

  if (selectionState.size() > 0) {
    restoreTreeSelectionFromState()
  }

  popup.showUnderneathOf(anchorComponent)

  SwingUtilities.invokeLater {
    if (project.isDisposed || popup.isDisposed) return@invokeLater
    if (tabbedPane.selectedIndex != SEARCH_TAB_INDEX) {
      tree.requestFocusInWindow()
    }
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
    .mapNotNull(treeSupport::getVirtualFile)
    .filter { isSelectableVirtualFile(it, scopedRootPaths) }
    .map { ManualPathSelectionEntry(path = it.path, isDirectory = it.isDirectory) }
}

private fun restoreTreeSelection(
  project: Project,
  tree: Tree,
  treeModel: StructureTreeModel<ProjectAbstractTreeStructureBase>,
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
  val selectionNodes = runReadActionBlocking {
    val psiManager = PsiManager.getInstance(project)
    selection.mapNotNull { entry ->
      val virtualFile = LocalFileSystem.getInstance().findFileByPath(entry.path) ?: return@mapNotNull null
      if (!isSelectableVirtualFile(virtualFile, scopedRootPaths)) return@mapNotNull null
      if (virtualFile.isDirectory) {
        psiManager.findDirectory(virtualFile)?.let { PsiDirectoryNode(project, it, null) }
      }
      else {
        psiManager.findFile(virtualFile)?.let { PsiFileNode(project, it, null) }
      }
    }
  }

  if (selectionNodes.isEmpty()) {
    try {
      tree.clearSelection()
    }
    finally {
      afterRestore()
    }
    return
  }

  selectionNodes.map(treeModel::promiseVisitor)
    .collectResults(ignoreErrors = true)
    .onSuccess { visitors ->
      ApplicationManager.getApplication().invokeLater({
                                                        if (project.isDisposed || popup.isDisposed) return@invokeLater
                                                        applyTreeSelection(tree, visitors, afterRestore)
                                                      }, ModalityState.stateForComponent(tree))
    }
    .onError {
      ApplicationManager.getApplication().invokeLater(afterRestore, ModalityState.stateForComponent(tree))
    }
}

private fun applyTreeSelection(
  tree: Tree,
  visitors: List<com.intellij.ui.tree.TreeVisitor>,
  afterApply: () -> Unit = {},
) {
  if (visitors.isEmpty()) {
    try {
      tree.clearSelection()
    }
    finally {
      afterApply()
    }
    return
  }

  val paths = ArrayList<TreePath>(visitors.size)

  fun selectNext(index: Int) {
    if (index >= visitors.size) {
      try {
        tree.selectionPaths = paths.toTypedArray()
        paths.firstOrNull()?.let { path ->
          TreeUtil.scrollToVisible(tree, path, false)
        }
      }
      finally {
        afterApply()
      }
      return
    }

    TreeUtil.promiseMakeVisible(tree, visitors[index])
      .onSuccess { path ->
        paths += path
        selectNext(index + 1)
      }
      .onError {
        selectNext(index + 1)
      }
  }

  selectNext(0)
}

private fun createTreeStructure(
  project: Project,
  treeSupport: TreeFileChooserSupport,
  scopedRootPaths: List<String>,
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
      return filterScopedProjectNodes(super.getChildElements(element), scopedRootPaths, fileCondition)
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
  private val scopedRootPaths: List<String>,
  private val dummyPanel: JPanel,
  private val selectionState: ManualPathSelectionState,
) : ChooseByNamePanel(project, ScopedGotoFileModel(project, scopedRootPaths), "", false, null) {

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
        val entry = selectionEntryFor(myList.model.getElementAt(index), scopedRootPaths) ?: return@filter false
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
    selectionState.addSearchSelection(chosenElements.mapNotNull { element -> selectionEntryFor(element, scopedRootPaths) })
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
  private val scopedRootPaths: Collection<String>,
) : GotoFileModel(project) {
  override fun acceptItem(item: NavigationItem): Boolean {
    if (item !is PsiFile) {
      return false
    }
    val virtualFile = item.virtualFile ?: return false
    return virtualFile.isInLocalFileSystem && isUnderAnyRoot(virtualFile.path, scopedRootPaths) && super.acceptItem(item)
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
