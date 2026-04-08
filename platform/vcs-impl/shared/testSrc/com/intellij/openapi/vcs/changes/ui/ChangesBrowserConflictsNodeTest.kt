// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION")

package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.util.BooleanGetter
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.merge.MergeResolveActionProvider
import com.intellij.openapi.vcs.merge.MergeResolveWithAgentContext
import com.intellij.platform.vcs.impl.changes.ChangesViewTestBase
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.ui.SimpleTextAttributes
import javax.swing.JTree

internal class ChangesBrowserConflictsNodeTest : ChangesViewTestBase() {
  fun `test renders contributed actions using update presentation order`() {
    val node = createConflictsNode()
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

    val rendered = render(node)
    assertEquals(listOf("First", "Second"), rendered.fragments.filter { it.text == "First" || it.text == "Second" }.map(RenderedFragment::text))
  }

  fun `test renders disabled contributed action as grayed with tooltip`() {
    val node = createConflictsNode()
    maskProviders(
      TestProvider(
        action = TestAction(templateText = "Resolve with Agent") { e ->
          e.presentation.isVisible = true
          e.presentation.isEnabled = false
          e.presentation.description = "Configure an agent provider"
        },
      ),
    )

    val rendered = render(node)
    val fragment = rendered.fragments.single { it.text == "Resolve with Agent" }
    assertEquals(SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES, fragment.attributes)
    assertNull(fragment.tag)
    assertEquals("Configure an agent provider", rendered.tooltip)
  }

  fun `test click path goes through ActionUtil performAction`() {
    val node = createConflictsNode()
    val action = TestAction(templateText = "Resolve with Agent") { e ->
      e.presentation.isEnabledAndVisible = true
      e.presentation.putClientProperty(ActionUtil.SKIP_ACTION_EXECUTION, true)
    }
    maskProviders(TestProvider(action = action))

    val rendered = render(node)
    assertSame(project, action.lastContext?.project)
    assertEquals(1, action.lastContext?.files?.size)
    val actionTag = rendered.fragments.single { it.text == "Resolve with Agent" }.tag as Runnable
    actionTag.run()

    assertEquals(0, action.performedCount)
  }

  private fun createConflictsNode(): ChangesBrowserConflictsNode {
    val file = VfsTestUtil.createFile(getSourceRoot(), "conflicts/sample.txt", "text")
    val change = Change(null, TestContentRevision(com.intellij.openapi.vcs.actions.VcsContextFactory.getInstance().createFilePathOn(file)))
    return ChangesBrowserConflictsNode(project).apply {
      add(ChangesBrowserNode.createChange(project, change))
      add(ChangesBrowserNode.createFile(project, file))
    }
  }

  private fun maskProviders(vararg providers: MergeResolveActionProvider) {
    ExtensionTestUtil.maskExtensions(MergeResolveActionProvider.EP_NAME, providers.toList(), testRootDisposable)
  }

  private fun render(node: ChangesBrowserConflictsNode): RenderedNode {
    val renderer = ChangesBrowserNodeRenderer(project, BooleanGetter { false }, true)
    renderer.getTreeCellRendererComponent(JTree(), node, false, true, false, 0, false)

    val fragments = buildList {
      val iterator = renderer.iterator()
      while (iterator.hasNext()) {
        iterator.next()
        add(RenderedFragment(iterator.fragment, iterator.textAttributes, iterator.tag))
      }
    }
    return RenderedNode(fragments, renderer.toolTipText)
  }

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
    var lastContext: MergeResolveWithAgentContext? = null
      private set

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
      lastContext = e.getData(MergeResolveWithAgentContext.KEY)
      updateHandler(e)
    }

    override fun actionPerformed(e: AnActionEvent) {
      lastContext = e.getData(MergeResolveWithAgentContext.KEY)
      performedCount++
    }
  }
}
