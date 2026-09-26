// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION")

package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.merge.MergeResolveActionContext
import com.intellij.openapi.vcs.merge.MergeResolveActionProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.vcs.impl.changes.ChangesViewTestBase
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.ui.SimpleTextAttributes
import javax.swing.JTree
import javax.swing.tree.TreePath

internal class ChangesBrowserConflictsNodeTest : ChangesViewTestBase() {
  fun `test renders contributed actions using update presentation order`() {
    val fixture = createConflictsNode("sample.txt")
    maskProviders(
      TestProvider(
        order = 10,
        action = TestAction(templateText = "Wrong") { e ->
          e.presentation.text = "Second"
          e.presentation.isEnabledAndVisible = true
        },
      ),
      TestProvider(
        order = -1,
        action = TestAction(templateText = "Wrong") { e ->
          e.presentation.text = "First"
          e.presentation.isEnabledAndVisible = true
        },
      ),
    )

    val rendered = render(fixture)
    assertEquals(listOf("First", "Second"), rendered.fragments.filter { it.text == "First" || it.text == "Second" }.map(RenderedFragment::text))
  }

  fun `test renders disabled contributed action as grayed with tooltip`() {
    val fixture = createConflictsNode("sample.txt")
    maskProviders(
      TestProvider(
        action = TestAction(templateText = "Resolve with Agent") { e ->
          e.presentation.isVisible = true
          e.presentation.isEnabled = false
          e.presentation.description = "Configure an agent provider"
        },
      ),
    )

    val rendered = render(fixture)
    val fragment = rendered.fragments.single { it.text == "Resolve with Agent" }
    assertEquals(SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES, fragment.attributes)
    assertNull(fragment.tag)
    assertEquals("Configure an agent provider", rendered.tooltip)
  }

  fun `test click path goes through ActionUtil performAction`() {
    val fixture = createConflictsNode("sample.txt")
    val action = TestAction(templateText = "Resolve with Agent") { e ->
      e.presentation.isEnabledAndVisible = true
      e.presentation.putClientProperty(ActionUtil.SKIP_ACTION_EXECUTION, true)
    }
    maskProviders(TestProvider(action = action))

    val rendered = render(fixture)
    assertSame(project, action.lastContext?.project)
    assertEquals(emptyList<VirtualFile>(), action.lastContext?.selectionHintFiles)
    val actionTag = rendered.fragments.single { it.text == "Resolve with Agent" }.tag as Runnable
    actionTag.run()

    assertEquals(0, action.performedCount)
  }

  fun `test selected conflict file contributes only selected files as launch hint`() {
    val fixture = createConflictsNode("first.txt", "second.txt")
    val action = TestAction(templateText = "Resolve with Agent") { e ->
      e.presentation.isEnabledAndVisible = true
    }
    maskProviders(TestProvider(action = action))

    render(fixture, fixture.fileNodes[1])

    assertEquals(listOf(fixture.files[1]), action.lastContext?.selectionHintFiles)
  }

  fun `test selecting conflicts root contributes every conflict file as launch hint`() {
    val fixture = createConflictsNode("first.txt", "second.txt")
    val action = TestAction(templateText = "Resolve with Agent") { e ->
      e.presentation.isEnabledAndVisible = true
    }
    maskProviders(TestProvider(action = action))

    render(fixture, fixture.node)

    assertEquals(fixture.files, action.lastContext?.selectionHintFiles)
  }

  fun `test selecting conflicts root beside one file still contributes every conflict file`() {
    val fixture = createConflictsNode("first.txt", "second.txt")
    val action = TestAction(templateText = "Resolve with Agent") { e ->
      e.presentation.isEnabledAndVisible = true
    }
    maskProviders(TestProvider(action = action))

    render(fixture, fixture.fileNodes[0], fixture.node)

    assertEquals(fixture.files, action.lastContext?.selectionHintFiles)
  }

  private fun createConflictsNode(vararg fileNames: String): ConflictsNodeFixture {
    val root = ChangesBrowserNode.createRoot()
    val node = ChangesBrowserConflictsNode(project)
    root.add(node)
    val basePath = "conflicts/${System.nanoTime()}"

    val files = mutableListOf<VirtualFile>()
    val changeNodes = mutableListOf<ChangesBrowserNode<*>>()
    val fileNodes = mutableListOf<ChangesBrowserNode<*>>()
    for (fileName in fileNames) {
      val file = VfsTestUtil.createFile(getSourceRoot(), "$basePath/$fileName", "text")
      val change = Change(null, TestContentRevision(com.intellij.openapi.vcs.actions.VcsContextFactory.getInstance().createFilePathOn(file)))
      val changeNode = ChangesBrowserNode.createChange(project, change)
      val fileNode = ChangesBrowserNode.createFile(project, file)
      files += file
      changeNodes += changeNode
      fileNodes += fileNode
      node.add(changeNode)
      node.add(fileNode)
    }

    return ConflictsNodeFixture(root, node, files, changeNodes, fileNodes)
  }

  private fun maskProviders(vararg providers: MergeResolveActionProvider) {
    ExtensionTestUtil.maskExtensions(MergeResolveActionProvider.EP_NAME, providers.toList(), testRootDisposable)
  }

  private fun render(fixture: ConflictsNodeFixture, vararg selectedNodes: ChangesBrowserNode<*>): RenderedNode {
    val tree = JTree(fixture.root)
    if (selectedNodes.isNotEmpty()) {
      tree.selectionPaths = selectedNodes.map { node -> pathToNode(fixture, node) }.toTypedArray()
    }
    val renderer = ChangesBrowserNodeRenderer(project, { false }, true)
    renderer.getTreeCellRendererComponent(tree, fixture.node, false, true, false, 0, false)

    val fragments = buildList {
      val iterator = renderer.iterator()
      while (iterator.hasNext()) {
        iterator.next()
        add(RenderedFragment(iterator.fragment, iterator.textAttributes, iterator.tag))
      }
    }
    return RenderedNode(fragments, renderer.toolTipText)
  }

  private fun pathToNode(fixture: ConflictsNodeFixture, node: ChangesBrowserNode<*>): TreePath {
    return when (node) {
      fixture.node -> TreePath(arrayOf(fixture.root, fixture.node))
      in fixture.changeNodes, in fixture.fileNodes -> TreePath(arrayOf(fixture.root, fixture.node, node))
      else -> error("Unexpected node: $node")
    }
  }

  private data class ConflictsNodeFixture(
    val root: ChangesBrowserNode<*>,
    val node: ChangesBrowserConflictsNode,
    val files: List<VirtualFile>,
    val changeNodes: List<ChangesBrowserNode<*>>,
    val fileNodes: List<ChangesBrowserNode<*>>,
  )

  private data class RenderedNode(
    val fragments: List<RenderedFragment>,
    val tooltip: String?,
  )

  private data class RenderedFragment(
    val text: String,
    val attributes: SimpleTextAttributes,
    val tag: Any?,
  )

  private class TestProvider(
    override val action: AnAction,
    override val order: Int = 0,
  ) : MergeResolveActionProvider

  private class TestContentRevision(
    private val filePath: FilePath,
  ) : ContentRevision {
    override fun getContent(): String? = null

    override fun getFile(): FilePath = filePath

    override fun getRevisionNumber(): com.intellij.openapi.vcs.history.VcsRevisionNumber =
      com.intellij.openapi.vcs.history.VcsRevisionNumber.NULL
  }

  private class TestAction(
    templateText: String,
    private val updateHandler: (AnActionEvent) -> Unit,
  ) : AnAction(templateText) {
    var performedCount: Int = 0
      private set
    var lastContext: MergeResolveActionContext? = null
      private set

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
      lastContext = e.getData(MergeResolveActionContext.KEY)
      updateHandler(e)
    }

    override fun actionPerformed(e: AnActionEvent) {
      lastContext = e.getData(MergeResolveActionContext.KEY)
      performedCount++
    }
  }
}
