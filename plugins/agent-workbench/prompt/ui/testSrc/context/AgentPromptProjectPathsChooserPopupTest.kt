// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.context

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.AbstractProjectViewPane
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.ide.scratch.ScratchesNamedScope
import com.intellij.ide.util.TreeFileChooserSupport
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.tree.TreeVisitor
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.concurrency.AppExecutorUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.nio.file.Files
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

@TestApplication
class AgentPromptProjectPathsChooserPopupTest {
  @Test
  fun collectConfirmedSelectionUsesTreeSelectionWhenProjectTabIsActive() {
    val existing = ManualPathSelectionEntry(path = "/repo/project/keep.txt", isDirectory = false)
    val added = ManualPathSelectionEntry(path = "/repo/project/src", isDirectory = true)
    val selectionState = ManualPathSelectionState(listOf(existing))
    var treeSyncCalls = 0
    var searchSyncCalls = 0

    val result = collectConfirmedSelection(
      isSearchTabSelected = false,
      selectionState = selectionState,
      syncTreeSelection = {
        treeSyncCalls++
        selectionState.addTreeSelection(listOf(added))
      },
      syncSearchSelection = {
        searchSyncCalls++
      },
    )

    assertThat(treeSyncCalls).isEqualTo(1)
    assertThat(searchSyncCalls).isZero()
    assertThat(result).containsExactly(existing, added)
  }

  @Test
  fun handleChooserTabSelectionChangedCommitsOnlyCurrentTreeSelectionWhenLeavingProjectTab() {
    var treeSyncCalls = 0
    var searchInitCalls = 0
    var searchRestoreCalls = 0
    var treeRestoreCalls = 0
    var treeFocusCalls = 0

    handleChooserTabSelectionChanged(
      previousSelectedIndex = 0,
      selectedIndex = 1,
      syncTreeSelection = { treeSyncCalls++ },
      ensureSearchPanelInitialized = { searchInitCalls++ },
      restoreSearchSelection = { searchRestoreCalls++ },
      restoreTreeSelection = { treeRestoreCalls++ },
      requestTreeFocus = { treeFocusCalls++ },
    )

    assertThat(treeSyncCalls).isEqualTo(1)
    assertThat(searchInitCalls).isEqualTo(1)
    assertThat(searchRestoreCalls).isEqualTo(1)
    assertThat(treeRestoreCalls).isZero()
    assertThat(treeFocusCalls).isZero()
  }

  @Test
  fun handleChooserTabSelectionChangedDoesNotCommitTreeSelectionWhenReturningFromSearch() {
    var treeSyncCalls = 0
    var searchInitCalls = 0
    var searchRestoreCalls = 0
    var treeRestoreCalls = 0
    var treeFocusCalls = 0

    handleChooserTabSelectionChanged(
      previousSelectedIndex = 1,
      selectedIndex = 0,
      syncTreeSelection = { treeSyncCalls++ },
      ensureSearchPanelInitialized = { searchInitCalls++ },
      restoreSearchSelection = { searchRestoreCalls++ },
      restoreTreeSelection = { treeRestoreCalls++ },
      requestTreeFocus = { treeFocusCalls++ },
    )

    assertThat(treeSyncCalls).isZero()
    assertThat(searchInitCalls).isZero()
    assertThat(searchRestoreCalls).isZero()
    assertThat(treeRestoreCalls).isEqualTo(1)
    assertThat(treeFocusCalls).isEqualTo(1)
  }

  @Test
  fun collectConfirmedSelectionUsesSearchSelectionWhenSearchTabIsActive() {
    val existing = ManualPathSelectionEntry(path = "/repo/project/keep.txt", isDirectory = false)
    val added = ManualPathSelectionEntry(path = "/repo/project/new.txt", isDirectory = false)
    val selectionState = ManualPathSelectionState(listOf(existing))
    var treeSyncCalls = 0
    var searchSyncCalls = 0

    val result = collectConfirmedSelection(
      isSearchTabSelected = true,
      selectionState = selectionState,
      syncTreeSelection = {
        treeSyncCalls++
      },
      syncSearchSelection = {
        searchSyncCalls++
        selectionState.addSearchSelection(listOf(added))
      },
    )

    assertThat(treeSyncCalls).isZero()
    assertThat(searchSyncCalls).isEqualTo(1)
    assertThat(result).containsExactly(existing, added)
  }

  @Test
  fun resolveTreeSelectionToRestoreUsesInitialTreePreselectionWhenManualSelectionIsEmpty() {
    val initialTreePreselection = ManualPathSelectionEntry(path = "/repo/project/src", isDirectory = true)

    val restoredSelection = resolveTreeSelectionToRestore(emptyList(), initialTreePreselection)

    assertThat(restoredSelection).containsExactly(initialTreePreselection)
  }

  @Test
  fun resolveTreeSelectionToRestorePrefersManualSelectionOverInitialTreePreselection() {
    val existing = ManualPathSelectionEntry(path = "/repo/project/src/Main.java", isDirectory = false)
    val initialTreePreselection = ManualPathSelectionEntry(path = "/repo/project/src", isDirectory = true)

    val restoredSelection = resolveTreeSelectionToRestore(listOf(existing), initialTreePreselection)

    assertThat(restoredSelection).containsExactly(existing)
  }

  @Test
  fun resolveSelectableVirtualFileFallsBackToNearestAttachableFileAncestor() {
    val rootNioDir = Files.createTempDirectory("agent-prompt-tree-select-file")
    val fileNioPath = Files.writeString(rootNioDir.resolve("Sample.kt"), "class Sample")
    val fileSystem = LocalFileSystem.getInstance()
    val rootDir = checkNotNull(fileSystem.refreshAndFindFileByNioFile(rootNioDir))
    val file = checkNotNull(fileSystem.refreshAndFindFileByNioFile(fileNioPath))
    val rootPath = treePathOf(
      node(
        TestProjectViewNode(rootDir) { candidate -> VfsUtilCore.isAncestor(rootDir, candidate, false) || rootDir == candidate },
        node(
          TestProjectViewNode(file) { candidate -> file == candidate },
          node(TestProjectViewValueNode("Sample")),
        ),
      ),
    )
    val filePath = rootPath.pathByAddingChild((rootPath.lastPathComponent as DefaultMutableTreeNode).firstChild)
    val memberPath = filePath.pathByAddingChild((filePath.lastPathComponent as DefaultMutableTreeNode).firstChild)
    val treeSupport = TreeFileChooserSupport(ProjectManager.getInstance().defaultProject)

    val resolved = resolveSelectableVirtualFile(memberPath, treeSupport, listOf(rootDir.path))

    assertThat(resolved).isEqualTo(file)
  }

  @Test
  fun resolveSelectableVirtualFileFallsBackToNearestAttachableDirectoryAncestor() {
    val rootNioDir = Files.createTempDirectory("agent-prompt-tree-select-dir")
    val nestedNioDir = Files.createDirectories(rootNioDir.resolve("src/com/example"))
    val fileSystem = LocalFileSystem.getInstance()
    val rootDir = checkNotNull(fileSystem.refreshAndFindFileByNioFile(rootNioDir))
    val nestedDir = checkNotNull(fileSystem.refreshAndFindFileByNioFile(nestedNioDir))
    val rootPath = treePathOf(
      node(
        TestProjectViewNode(rootDir) { candidate -> VfsUtilCore.isAncestor(rootDir, candidate, false) || rootDir == candidate },
        node(
          TestProjectViewNode(nestedDir) { candidate -> VfsUtilCore.isAncestor(nestedDir, candidate, false) || nestedDir == candidate },
          node(TestProjectViewValueNode("example")),
        ),
      ),
    )
    val nestedPath = rootPath.pathByAddingChild((rootPath.lastPathComponent as DefaultMutableTreeNode).firstChild)
    val packagePath = nestedPath.pathByAddingChild((nestedPath.lastPathComponent as DefaultMutableTreeNode).firstChild)
    val treeSupport = TreeFileChooserSupport(ProjectManager.getInstance().defaultProject)

    val resolved = resolveSelectableVirtualFile(packagePath, treeSupport, listOf(rootDir.path))

    assertThat(resolved).isEqualTo(nestedDir)
  }

  @Test
  fun installConfirmSelectionOnEnterDelegatesToFallbackWhenConfirmationIsNotHandled() {
    runInEdtAndWait {
      val tree = createTree()
      val enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)
      var fallbackCalls = 0
      var confirmCalls = 0

      tree.registerKeyboardAction({ fallbackCalls++ }, enter, JComponent.WHEN_FOCUSED)
      installConfirmSelectionOnEnter(tree) {
        confirmCalls++
        false
      }

      invokeEnter(tree)

      assertThat(confirmCalls).isEqualTo(1)
      assertThat(fallbackCalls).isEqualTo(1)
    }
  }

  @Test
  fun installConfirmSelectionOnEnterSkipsFallbackWhenConfirmationIsHandled() {
    runInEdtAndWait {
      val tree = createTree()
      val enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)
      var fallbackCalls = 0
      var confirmCalls = 0

      tree.registerKeyboardAction({ fallbackCalls++ }, enter, JComponent.WHEN_FOCUSED)
      installConfirmSelectionOnEnter(tree) {
        confirmCalls++
        true
      }

      invokeEnter(tree)

      assertThat(confirmCalls).isEqualTo(1)
      assertThat(fallbackCalls).isZero()
    }
  }

  @Test
  fun projectViewVisitorTargetsConcretePreviouslySelectedFilePath() {
    val rootNioFile = Files.createTempDirectory("agent-prompt-path-restore")
    val scratchNioDirectory = Files.createDirectories(rootNioFile.resolve("scratches"))
    val selectedNioFile = Files.writeString(scratchNioDirectory.resolve("note.txt"), "content")
    val fileSystem = LocalFileSystem.getInstance()
    val root = checkNotNull(fileSystem.refreshAndFindFileByNioFile(rootNioFile))
    val scratchDirectory = checkNotNull(fileSystem.refreshAndFindFileByNioFile(scratchNioDirectory))
    val selectedFile = checkNotNull(fileSystem.refreshAndFindFileByNioFile(selectedNioFile))
    val rootPath = treePathOf(
      node(
        TestProjectViewNode(root) { file -> VfsUtilCore.isAncestor(root, file, false) || root == file },
        node(
          TestProjectViewNode(scratchDirectory) { file -> VfsUtilCore.isAncestor(scratchDirectory, file, false) || scratchDirectory == file },
          node(TestProjectViewNode(selectedFile) { file -> selectedFile == file }),
        ),
      ),
    )
    val scratchPath = rootPath.pathByAddingChild((rootPath.lastPathComponent as DefaultMutableTreeNode).firstChild)
    val filePath = scratchPath.pathByAddingChild((scratchPath.lastPathComponent as DefaultMutableTreeNode).firstChild)
    val visitor = AbstractProjectViewPane.createVisitor(selectedFile)

    val actions = AppExecutorUtil.getAppExecutorService().submit<List<TreeVisitor.Action>> {
      listOf(visitor.visit(rootPath), visitor.visit(scratchPath), visitor.visit(filePath))
    }.get()

    assertThat(actions).containsExactly(TreeVisitor.Action.CONTINUE, TreeVisitor.Action.CONTINUE, TreeVisitor.Action.INTERRUPT)
  }

  @Test
  fun findRestorableVirtualFileResolvesFreshScratchPath() {
    val selectedNioFile = Files.writeString(Files.createTempFile("agent-prompt-scratch-restore", ".txt"), "content")
    val selectedPath = FileUtil.toSystemIndependentName(selectedNioFile.toString())

    val restoredFile = findRestorableVirtualFile(selectedPath)

    assertThat(restoredFile).isNotNull
    assertThat(restoredFile!!.path).isEqualTo(selectedPath)
  }

  @Test
  fun createRestorableSelectionVisitorUsesScratchNodeChainForScratchFiles() {
    val scratchFile = checkNotNull(ScratchRootType.getInstance().createScratchFile(null, "agent-prompt-restore.txt", null, "content"))
    try {
      val scratchRoot = checkNotNull(scratchFile.parent)
      val rootType = ScratchRootType.getInstance()
      val scratchesPath = treePathOf(
        node(
          TestProjectViewValueNode(ScratchesNamedScope.scratchesAndConsoles()),
          node(
            TestProjectViewValueNode(rootType, scratchRoot),
            node(TestProjectViewValueNode(scratchFile, scratchFile)),
          ),
        ),
      )
      val rootTypePath = scratchesPath.pathByAddingChild((scratchesPath.lastPathComponent as DefaultMutableTreeNode).firstChild)
      val filePath = rootTypePath.pathByAddingChild((rootTypePath.lastPathComponent as DefaultMutableTreeNode).firstChild)
      val visitor = checkNotNull(createRestorableSelectionVisitor(scratchFile))

      val actions = AppExecutorUtil.getAppExecutorService().submit<List<TreeVisitor.Action>> {
        listOf(visitor.visit(scratchesPath), visitor.visit(rootTypePath), visitor.visit(filePath))
      }.get()

      assertThat(actions).containsExactly(TreeVisitor.Action.CONTINUE, TreeVisitor.Action.CONTINUE, TreeVisitor.Action.INTERRUPT)
    }
    finally {
      runCatching { scratchFile.delete(this) }
    }
  }

  private fun createTree(): Tree {
    return Tree(DefaultTreeModel(DefaultMutableTreeNode("Root")))
  }

  private fun node(userObject: Any, vararg children: DefaultMutableTreeNode): DefaultMutableTreeNode {
    return DefaultMutableTreeNode(userObject).apply {
      children.forEach(::add)
    }
  }

  private fun treePathOf(node: DefaultMutableTreeNode): TreePath = TreePath(node.path)

  private fun invokeEnter(tree: Tree) {
    val enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)
    val action = checkNotNull(tree.getActionForKeyStroke(enter)) { "No Enter action registered" }
    action.actionPerformed(ActionEvent(tree, ActionEvent.ACTION_PERFORMED, "Enter"))
  }

  private class TestProjectViewNode(
    private val file: VirtualFile,
    private val containsPredicate: (VirtualFile) -> Boolean,
  ) : ProjectViewNode<VirtualFile>(null, file, ViewSettings.DEFAULT) {
    override fun contains(file: VirtualFile): Boolean = containsPredicate(file)

    override fun getVirtualFile(): VirtualFile = file

    override fun canRepresent(element: Any?): Boolean = element == file

    override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()

    override fun update(presentation: PresentationData) {}
  }

  private class TestProjectViewValueNode(
    value: Any,
    private val virtualFile: VirtualFile? = null,
  ) : ProjectViewNode<Any>(null, value, ViewSettings.DEFAULT) {
    override fun contains(file: VirtualFile): Boolean = false

    override fun getVirtualFile(): VirtualFile? = virtualFile

    override fun canRepresent(element: Any?): Boolean = element == value || element == virtualFile

    override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()

    override fun update(presentation: PresentationData) {}
  }
}
