// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.application.EDT
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.changes.LocalChangeListImpl
import com.intellij.openapi.vcs.changes.LocalChangesListView
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.pom.Navigatable
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.ui.ThreeStateCheckBox
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.swing.tree.DefaultTreeModel

@TestApplication
internal class StructuralChangesTreeCellRendererTest {
  @Test
  fun `structural rows reserve checkbox space when the renderer is reused`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val project = projectFixture.get()
      val tree = LocalChangesListView(project)
      val textRenderer = ChangesBrowserNodeRenderer(project, { false }, false)
      val renderer = ChangesTreeCellRenderer(textRenderer)
      val checkBox = checkNotNull(UIUtil.findComponentOfType(renderer, ThreeStateCheckBox::class.java))
      val change = Change(null, object : ContentRevision {
        override fun getFile(): FilePath = LocalFilePath("/StructuralRendererTest/changed.txt", false)
        override fun getContent(): String = "member"
        override fun getRevisionNumber(): VcsRevisionNumber = VcsRevisionNumber.NULL
      })
      val changeList = LocalChangeListImpl.Builder(project, "Default").setDefault(true).setChanges(listOf(change)).build()
      val regularNode = ChangesBrowserChangeNode(project, change, null)
      val listNode = ChangesBrowserChangeListNode(project, changeList, ChangeListRemoteState()).apply { add(regularNode) }
      val structuralNode = object : ChangesBrowserStructuralNode<String>("member") {
        override fun getNavigatable(): Navigatable? = null
        override fun getTextPresentation(): String = "member"
      }
      regularNode.add(structuralNode)
      val root = ChangesBrowserNode.createRoot().apply { add(listNode) }
      tree.updateTreeModel(DefaultTreeModel(root), ChangesTree.ALWAYS_RESET)

      fun render(node: ChangesBrowserNode<*>): Int {
        renderer.getTreeCellRendererComponent(tree, node, false, false, true, 0, false)
        renderer.size = renderer.preferredSize
        renderer.doLayout()
        return textRenderer.x
      }

      for (showCheckboxes in listOf(true, false, true)) {
        tree.setShowCheckboxes(showCheckboxes)
        val regularOffset = render(regularNode)
        assertEquals(showCheckboxes, checkBox.isVisible)
        if (showCheckboxes) assertTrue(regularOffset > 0)
        else assertEquals(0, regularOffset)

        assertEquals(regularOffset, render(structuralNode), "Reserve the same space as the file checkbox")
        assertFalse(checkBox.isVisible)
        assertEquals(textRenderer.accessibleContext.accessibleName, renderer.accessibleContext.accessibleName)

        assertEquals(regularOffset, render(regularNode), "Clear the spacer before the next file row")
        assertEquals(showCheckboxes, checkBox.isVisible)
      }
    }
  }

  companion object {
    private val projectFixture = projectFixture()
  }
}
