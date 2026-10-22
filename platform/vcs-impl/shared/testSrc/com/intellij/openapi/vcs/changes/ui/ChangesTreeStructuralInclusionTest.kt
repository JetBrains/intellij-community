// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.pom.Navigatable
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.ui.ClickListener
import com.intellij.ui.tree.ui.DefaultTreeUI
import com.intellij.util.ui.ThreeStateCheckBox.State
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

@TestApplication
internal class ChangesTreeStructuralInclusionTest {
  @Test
  fun `a hunk node reports its own state and falls back to the file state`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val fixture = Fixture(projectFixture.get())
      fixture.tree.includeChange(fixture.change)

      assertEquals(State.SELECTED, fixture.tree.status(fixture.fileNode))
      assertEquals(State.SELECTED, fixture.tree.status(fixture.followsFile))
      fixture.own.state = State.NOT_SELECTED
      assertEquals(State.NOT_SELECTED, fixture.tree.status(fixture.own))
      assertEquals(State.NOT_SELECTED, fixture.tree.status(fixture.helper))
    }
  }

  @Test
  fun `a checkbox click toggles the clicked hunk and keeps the file inclusion`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val fixture = Fixture(projectFixture.get())
      fixture.tree.includeChange(fixture.change)
      fixture.own.state = State.SELECTED

      fixture.clickCheckbox(fixture.own)

      assertEquals(listOf(false), fixture.own.toggles)
      assertTrue(fixture.followsFile.toggles.isEmpty())
      assertEquals(setOf<Any>(fixture.change), fixture.tree.includedSet)
      assertEquals(TreePath(fixture.own.path), fixture.tree.selectionPath)
    }
  }

  @Test
  fun `a click on the checkbox space of a helper node keeps the file inclusion`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val fixture = Fixture(projectFixture.get())
      fixture.tree.includeChange(fixture.change)

      fixture.clickCheckbox(fixture.helper)

      assertEquals(setOf<Any>(fixture.change), fixture.tree.includedSet)
      assertTrue(fixture.own.toggles.isEmpty())
      assertTrue(fixture.followsFile.toggles.isEmpty())
    }
  }

  @Test
  fun `space includes all selected hunks unless all of them are included`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val fixture = Fixture(projectFixture.get())
      fixture.own.state = State.SELECTED
      fixture.followsFile.state = State.NOT_SELECTED
      fixture.tree.selectionPaths = arrayOf(TreePath(fixture.own.path), TreePath(fixture.followsFile.path))

      fixture.pressSpace()
      assertEquals(listOf(true), fixture.own.toggles)
      assertEquals(listOf(true), fixture.followsFile.toggles)

      fixture.followsFile.state = State.SELECTED
      fixture.pressSpace()
      assertEquals(listOf(true, false), fixture.own.toggles)
      assertEquals(listOf(true, false), fixture.followsFile.toggles)
      assertTrue(fixture.tree.includedSet.isEmpty())
    }
  }

  private class Fixture(project: Project) {
    val change = Change(null, revision(LocalFilePath("/sample/Sample.java", false)))
    val fileNode = ChangesBrowserChangeNode(project, change, null)
    val own = HunkNode("own", hasState = true)
    val followsFile = HunkNode("follows the file", hasState = false)
    val helper = HelperNode()
    val tree = InclusionTree(project).apply {
      setUI(DefaultTreeUI())
      val root = ChangesBrowserNode.createRoot()
      root.add(fileNode)
      fileNode.add(own)
      fileNode.add(followsFile)
      fileNode.add(helper)
      model = DefaultTreeModel(root)
      setShowCheckboxes(true)
      expandPath(TreePath(fileNode.path))
      setSize(640, 300)
      doLayout()
    }

    fun checkboxPoint(node: ChangesBrowserNode<*>): java.awt.Point {
      val bounds = checkNotNull(tree.getPathBounds(TreePath(node.path)))
      return java.awt.Point(bounds.x + 4, bounds.y + bounds.height / 2)
    }

    /** Clicks through the click listeners of the tree. The Swing tree UI handles a press with a toolkit call that fails without a display. */
    fun clickCheckbox(node: ChangesBrowserNode<*>) {
      val point = checkboxPoint(node)
      val time = System.currentTimeMillis()
      val pressed = MouseEvent(tree, MouseEvent.MOUSE_PRESSED, time, InputEvent.BUTTON1_DOWN_MASK, point.x, point.y, 1, false, MouseEvent.BUTTON1)
      val released = MouseEvent(tree, MouseEvent.MOUSE_RELEASED, time + 10, 0, point.x, point.y, 1, false, MouseEvent.BUTTON1)
      val listeners = tree.mouseListeners.filter { it.javaClass.name.startsWith(ClickListener::class.java.name) }
      check(listeners.isNotEmpty()) { "The tree has no click listener" }
      listeners.forEach { it.mousePressed(pressed) }
      listeners.forEach { it.mouseReleased(released) }
    }

    fun pressSpace() {
      val action = ActionUtil.getActions(tree).single { it.javaClass.simpleName == "MyToggleSelectionAction" }
      action.actionPerformed(TestActionEvent.createTestEvent(action))
    }
  }

  private class InclusionTree(project: Project) : ChangesTree(project, true, false) {
    override fun rebuildTree() = Unit

    fun status(node: ChangesBrowserNode<*>): State = getNodeStatus(node)
  }

  /** A hunk with its own state, or a hunk that follows the file state until it has one. */
  private class HunkNode(private val name: String, private val hasState: Boolean) : ChangesBrowserStructuralNode<String>(name, hasInclusion = true) {
    var state: State? = null
    val toggles: MutableList<Boolean> = ArrayList()

    override fun getNavigatable(): Navigatable? = null
    override fun getTextPresentation(): String = name
    override fun getInclusionState(fileState: State): State = if (hasState || state != null) state ?: fileState else fileState

    override fun setIncluded(included: Boolean) {
      toggles.add(included)
    }
  }

  private class HelperNode : ChangesBrowserStructuralNode<String>("helper") {
    override fun getNavigatable(): Navigatable? = null
    override fun getTextPresentation(): String = "helper"
  }

  companion object {
    private val projectFixture = projectFixture()

    private fun revision(path: FilePath): ContentRevision = object : ContentRevision {
      override fun getFile(): FilePath = path
      override fun getContent(): String = ""
      override fun getRevisionNumber(): VcsRevisionNumber = VcsRevisionNumber.NULL
    }
  }
}
