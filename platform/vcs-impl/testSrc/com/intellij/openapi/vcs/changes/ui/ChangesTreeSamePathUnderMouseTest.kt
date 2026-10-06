// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CurrentContentRevision
import com.intellij.openapi.vcs.changes.LocalChangeListImpl
import com.intellij.platform.vcs.changes.ChangesUtil
import com.intellij.platform.vcs.impl.changes.ChangesViewTestBase
import com.intellij.util.ui.tree.TreeUtil
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

/**
 * A changes view refresh makes new nodes for the same items. A click that crosses the refresh reaches
 * the click listeners only if [ChangesTree.isSamePathUnderMouse] tells that the paths are the same.
 */
internal class ChangesTreeSamePathUnderMouseTest : ChangesViewTestBase() {
  fun `test a checkbox click is not lost when a refresh rebuilds the model between the press and the release`() {
    view.setShowCheckboxes(true)
    view.setSize(400, 400)
    showModel(buildModel("Default" to listOf("Main.java")))
    val point = checkBoxPoint(pathTo(view.model as DefaultTreeModel, "Main.java"))

    press(point)
    showModel(buildModel("Default" to listOf("Main.java")))
    release(point)

    assertTrue(view.isIncluded(Change(null, CurrentContentRevision.create(path("Main.java")))))
  }

  fun `test a path in a rebuilt model with the same content is the same`() {
    val pressed = pathTo(buildModel("Default" to listOf("Main.java")), "Main.java")
    val released = pathTo(buildModel("Default" to listOf("Main.java")), "Main.java")

    assertTrue(view.isSamePathUnderMouse(pressed, released))
  }

  fun `test a path to another file is not the same`() {
    val model = buildModel("Default" to listOf("Main.java", "Other.java"))

    assertFalse(view.isSamePathUnderMouse(pathTo(model, "Main.java"), pathTo(model, "Other.java")))
  }

  fun `test a file that moved to another changelist is not the same`() {
    // the checkbox of a file that moved during the click must not change
    val pressed = pathTo(buildModel("Default" to listOf("Main.java"), "Work" to emptyList()), "Main.java")
    val released = pathTo(buildModel("Default" to emptyList(), "Work" to listOf("Main.java")), "Main.java")

    assertFalse(view.isSamePathUnderMouse(pressed, released))
  }

  fun `test new nodes without user objects are not the same`() {
    val pressed = TreePath(arrayOf(DefaultMutableTreeNode(), DefaultMutableTreeNode()))
    val released = TreePath(arrayOf(DefaultMutableTreeNode(), DefaultMutableTreeNode()))

    assertFalse(view.isSamePathUnderMouse(pressed, released))
  }

  fun `test a missing path is the same only as another missing path`() {
    val path = pathTo(buildModel("Default" to listOf("Main.java")), "Main.java")

    assertTrue(view.isSamePathUnderMouse(null, null))
    assertFalse(view.isSamePathUnderMouse(path, null))
    assertFalse(view.isSamePathUnderMouse(null, path))
  }

  /**
   * Builds the model of the changes view, with new nodes on each call. The first changelist is the default one.
   */
  private fun buildModel(vararg changeLists: Pair<String, List<String>>): DefaultTreeModel {
    val lists = changeLists.mapIndexed { index, (name, fileNames) ->
      LocalChangeListImpl.Builder(project, name)
        .setDefault(index == 0)
        .setChanges(fileNames.map { Change(null, CurrentContentRevision.create(path(it))) })
        .build()
    }
    return buildModel(view, lists, emptyList())
  }

  private fun showModel(model: DefaultTreeModel) {
    view.updateTreeModel(model, ChangesTree.ALWAYS_RESET)
    TreeUtil.expandAll(view)
    view.doLayout()
  }

  private fun checkBoxPoint(path: TreePath): Point {
    val bounds = checkNotNull(view.getPathBounds(path))
    return Point(bounds.x + 2, bounds.y + bounds.height / 2)
  }

  private fun press(point: Point) {
    val event = mouseEvent(MouseEvent.MOUSE_PRESSED, point, InputEvent.BUTTON1_DOWN_MASK)
    clickMouseListeners().forEach { it.mousePressed(event) }
  }

  private fun release(point: Point) {
    val event = mouseEvent(MouseEvent.MOUSE_RELEASED, point, 0)
    clickMouseListeners().forEach { it.mouseReleased(event) }
  }

  /**
   * The listeners in the order AWT notifies them, without the listener of the tree UI.
   * `DefaultTreeUI` wraps the selection handler of `BasicTreeUI`, which needs a non-headless toolkit.
   * The handler does not take part in the click handling under test.
   */
  private fun clickMouseListeners(): List<MouseListener> {
    val uiClasses = generateSequence<Class<*>>(view.ui.javaClass) { it.superclass }.toSet()
    return view.mouseListeners.filterNot { it.javaClass.enclosingClass in uiClasses }
  }

  private fun mouseEvent(id: Int, point: Point, modifiers: Int): MouseEvent =
    MouseEvent(view, id, System.currentTimeMillis(), modifiers, point.x, point.y, 1, false, MouseEvent.BUTTON1)

  private fun pathTo(model: DefaultTreeModel, fileName: String): TreePath {
    val filePath: FilePath = path(fileName)
    val node = TreeUtil.findNode(model.root as DefaultMutableTreeNode) { node ->
      (node.userObject as? Change)?.let(ChangesUtil::getAfterPath) == filePath
    }
    return TreePath(checkNotNull(node) { "$fileName is not in the model" }.path)
  }
}
