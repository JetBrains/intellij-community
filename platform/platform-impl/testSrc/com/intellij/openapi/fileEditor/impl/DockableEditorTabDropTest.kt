// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.tabs.JBTabs
import com.intellij.ui.tabs.JBTabsPosition
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.impl.JBTabsImpl
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import javax.swing.JLabel

/**
 * Tests a tab drop into the editor window that the drag started from.
 *
 * The test runs the drag-out, the drop preview, the close of the source tab, and the drop in the same order as a mouse drag.
 */
@TestApplication
internal class DockableEditorTabDropTest {
  private val projectFixture = projectFixture(
    openProjectTask = OpenProjectTask {
      beforeInitTasks += { it.putUserData(FileEditorManagerKeys.ALLOW_IN_LIGHT_PROJECT, true) }
    },
    openAfterCreation = true,
  )
  private val managerFixture = projectFixture.fileEditorManagerFixture(initDockableContentFactory = true)

  @Test
  fun `drop on the preview after the last tab moves the tab to the end`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val order = dragAndDrop(dragged = "B") { tabs -> pointJustAfter(tabs.labelBounds("D")) }
    assertThat(order).containsExactly("A", "C", "D", "B")
  }

  @Test
  fun `drop on the first tab moves the tab to the start`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val order = dragAndDrop(dragged = "D") { tabs -> center(tabs.labelBounds("A")) }
    assertThat(order).containsExactly("D", "A", "B", "C")
  }

  @Test
  fun `drop on a later tab moves the tab before it`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val order = dragAndDrop(dragged = "A") { tabs -> center(tabs.labelBounds("D")) }
    assertThat(order).containsExactly("B", "C", "A", "D")
  }

  @Test
  fun `hover in empty header space after last tab shows insert preview instead of split`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val (tabs, dropInfo) = hoverDrop(dragged = "B") { tabs -> pointJustAfter(tabs.labelBounds("D")) }
    assertThat(tabs.dropSide).isIn(-1, javax.swing.SwingConstants.CENTER)
    assertThat(tabs.lastLayoutPass!!.visibleInfos.last()).isSameAs(dropInfo)
    tabs.resetDropOver(dropInfo)
  }

  @Test
  fun `hover with last tab in empty header space after it does not split`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val (tabs, dropInfo) = hoverDrop(dragged = "D") { tabs -> pointJustAfter(tabs.labelBounds("D")) }
    assertThat(tabs.dropSide).isIn(-1, javax.swing.SwingConstants.CENTER)
    tabs.resetDropOver(dropInfo)
  }

  @Test
  fun `vertical tabs hover in empty space below last tab shows insert preview instead of split`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val (tabs, dropInfo) = hoverDrop(dragged = "B", position = JBTabsPosition.left) { tabs -> pointJustBelow(tabs.labelBounds("D")) }
    assertThat(tabs.dropSide).isIn(-1, javax.swing.SwingConstants.CENTER)
    assertThat(tabs.lastLayoutPass!!.visibleInfos.last()).isSameAs(dropInfo)
    tabs.resetDropOver(dropInfo)
  }

  @Test
  fun `vertical tabs hover with last tab in empty space below it does not split`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val (tabs, dropInfo) = hoverDrop(dragged = "D", position = JBTabsPosition.left) { tabs -> pointJustBelow(tabs.labelBounds("D")) }
    assertThat(tabs.dropSide).isIn(-1, javax.swing.SwingConstants.CENTER)
    tabs.resetDropOver(dropInfo)
  }

  @Test
  fun `vertical tabs hover just beside the tab column shows insert preview instead of split`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val (tabs, dropInfo) = hoverDrop(dragged = "A", position = JBTabsPosition.left) { tabs ->
      val bounds = tabs.labelBounds("C")
      Point(bounds.x + bounds.width + 5, bounds.y + bounds.height / 2)
    }
    assertThat(tabs.dropSide).isIn(-1, javax.swing.SwingConstants.CENTER)
    assertThat(tabs.lastLayoutPass!!.visibleInfos).contains(dropInfo)
    tabs.resetDropOver(dropInfo)
  }

  @Test
  fun `vertical tabs drop below the last tab moves the tab to the end`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val order = dragAndDrop(dragged = "B", position = JBTabsPosition.left) { tabs -> pointJustBelow(tabs.labelBounds("D")) }
    assertThat(order).containsExactly("A", "C", "D", "B")
  }

  @Test
  fun `drag out keeps source tab visible and selected composite attached`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val manager = managerFixture.get()
    val files = listOf("A", "B", "C", "D").associateWith { LightVirtualFile("$it.txt") }
    for (file in files.values) {
      manager.openFile(file, true)
    }
    val window = requireNotNull(manager.currentWindow)
    val tabs = window.tabbedPane.editorTabs
    tabs.size = Dimension(1000, 200)
    tabs.doLayout()

    val fileB = files.getValue("B")
    val sourceB = requireNotNull(tabs.findInfo(fileB))
    val initialComposite = sourceB.composite
    window.setCurrentCompositeAndSelectTab(initialComposite)

    (sourceB.dragOutDelegate as EditorTabbedContainerDragOutDelegate).hideDragSource(sourceB)
    tabs.doLayout()
    assertThat(sourceB.isHidden).isFalse()
    assertThat(window.selectedComposite).isSameAs(initialComposite)

    val container = manager.dockContainer as DockableEditorTabbedContainer
    val dropInfo = TabInfo(JLabel()).setText("B").setObject(fileB)
    container.setDropTarget(tabs, dropInfo)
    val samePlacePoint = pointOn(tabs, center(tabs.labelBounds("B")))
    tabs.startDropOver(dropInfo, samePlacePoint)
    tabs.doLayout()
    tabs.processDropOver(dropInfo, samePlacePoint)
    tabs.doLayout()
    assertThat(container.isSameWindowCenterDrop(tabs)).isTrue()
    tabs.resetDropOver(dropInfo)
  }

  @Test
  fun `single-tab window drag over editor body keeps center drop side`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val manager = managerFixture.get()
    val fileA = LightVirtualFile("A.txt")
    manager.openFile(fileA, true)
    val window = requireNotNull(manager.currentWindow)
    val tabs = window.tabbedPane.editorTabs
    tabs.size = Dimension(1000, 200)
    tabs.doLayout()

    val sourceA = requireNotNull(tabs.findInfo(fileA))
    (sourceA.dragOutDelegate as EditorTabbedContainerDragOutDelegate).hideDragSource(sourceA)

    val dropInfo = TabInfo(JLabel()).setText("A").setObject(fileA)
    val bodyPoint = pointOn(tabs, Point(500, 180))
    tabs.startDropOver(dropInfo, bodyPoint)
    tabs.doLayout()
    tabs.processDropOver(dropInfo, bodyPoint)
    tabs.doLayout()

    assertThat(tabs.dropSide).isEqualTo(javax.swing.SwingConstants.CENTER)
    assertThat(tabs.lastLayoutPass!!.visibleInfos).containsExactly(sourceA)
    tabs.resetDropOver(dropInfo)
  }

  @Test
  fun `editor tab selects only on mouse release when not dragged`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val manager = managerFixture.get()
    val fileA = LightVirtualFile("A.txt")
    val fileB = LightVirtualFile("B.txt")
    manager.openFile(fileA, true)
    manager.openFile(fileB, true)
    val window = requireNotNull(manager.currentWindow)
    val tabs = window.tabbedPane.editorTabs
    tabs.size = Dimension(1000, 200)
    tabs.doLayout()

    val infoA = requireNotNull(tabs.findInfo(fileA))
    val infoB = requireNotNull(tabs.findInfo(fileB))
    val labelA = requireNotNull(tabs.getTabLabel(infoA))
    assertThat(tabs.selectedInfo).isSameAs(infoB)

    // Pressing mouse down on unselected tab A must not change selection.
    val pressForDrag = MouseEvent(labelA, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), InputEvent.BUTTON1_DOWN_MASK,
                                  labelA.width / 2, labelA.height / 2, 1, false, MouseEvent.BUTTON1)
    labelA.dispatchEvent(pressForDrag)
    assertThat(tabs.selectedInfo).isSameAs(infoB)

    // When a drag starts, pending selection is cancelled so releasing does not select tab A.
    labelA.cancelPendingSelection()
    val releaseAfterDrag = MouseEvent(labelA, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0,
                                      labelA.width / 2, labelA.height / 2, 0, false, MouseEvent.BUTTON1)
    labelA.dispatchEvent(releaseAfterDrag)
    assertThat(tabs.selectedInfo).isSameAs(infoB)

    // A full click (press + release on tab A without drag) selects tab A.
    val pressClick = MouseEvent(labelA, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), InputEvent.BUTTON1_DOWN_MASK,
                                labelA.width / 2, labelA.height / 2, 1, false, MouseEvent.BUTTON1)
    labelA.dispatchEvent(pressClick)
    assertThat(tabs.selectedInfo).isSameAs(infoB)

    val releaseClick = MouseEvent(labelA, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0,
                                  labelA.width / 2 + 2, labelA.height / 2, 0, false, MouseEvent.BUTTON1)
    labelA.dispatchEvent(releaseClick)
    assertThat(tabs.selectedInfo).isSameAs(infoA)
  }

  /**
   * Opens the tabs `A`, `B`, `C`, `D`, drags the [dragged] tab out, and moves the pointer twice to the point that [dropPoint] returns.
   *
   * `DockableEditorTabbedContainer.add` splits the window for every drop side except `-1` and `CENTER`.
   * A headless test has no `EditorWindow` in the data context, so the test checks the drop side before the drop.
   *
   * @return the tabs and the drop preview
   */
  private suspend fun hoverDrop(
    dragged: String,
    position: JBTabsPosition = JBTabsPosition.top,
    dropPoint: (JBTabs) -> Point,
  ): Pair<JBTabsImpl, TabInfo> {
    val manager = managerFixture.get()
    val files = listOf("A", "B", "C", "D").associateWith { LightVirtualFile("$it.txt") }
    for (file in files.values) {
      manager.openFile(file, true)
    }
    val tabs = requireNotNull(manager.currentWindow).tabbedPane.editorTabs
    tabs.setTabsPosition(position)
    tabs.size = Dimension(1000, 600)
    tabs.doLayout()

    val file = files.getValue(dragged)
    val source = requireNotNull(tabs.findInfo(file))
    (source.dragOutDelegate as EditorTabbedContainerDragOutDelegate).hideDragSource(source)
    tabs.doLayout()

    val container = manager.dockContainer as DockableEditorTabbedContainer
    val dropInfo = TabInfo(JLabel()).setText(dragged).setObject(file)
    container.setDropTarget(tabs, dropInfo)
    val point = pointOn(tabs, dropPoint(tabs))
    tabs.startDropOver(dropInfo, point)
    tabs.doLayout()
    // Two mouse moves: the second one can see the drop preview under the pointer.
    repeat(2) {
      tabs.processDropOver(dropInfo, point)
      tabs.doLayout()
    }
    assertThat(container.currentDropSide).isEqualTo(tabs.dropSide)
    return tabs to dropInfo
  }

  /**
   * Opens the tabs `A`, `B`, `C`, `D`, drags the [dragged] tab out, and drops it at the point that [dropPoint] returns.
   *
   * @return the tab names after the drop
   */
  private suspend fun dragAndDrop(
    dragged: String,
    position: JBTabsPosition = JBTabsPosition.top,
    dropPoint: (JBTabs) -> Point,
  ): List<String> {
    val manager = managerFixture.get()
    val files = listOf("A", "B", "C", "D").associateWith { LightVirtualFile("$it.txt") }
    for (file in files.values) {
      manager.openFile(file, true)
    }
    val window = requireNotNull(manager.currentWindow)
    val tabs = window.tabbedPane.editorTabs
    tabs.setTabsPosition(position)
    tabs.size = Dimension(1000, 600)
    tabs.doLayout()
    assertThat(window.tabNames()).containsExactly("A", "B", "C", "D")

    val file = files.getValue(dragged)
    val source = requireNotNull(tabs.findInfo(file))
    val isPinned = window.isFilePinned(file)
    val isSelected = tabs.selectedInfo == source
    (source.dragOutDelegate as EditorTabbedContainerDragOutDelegate).hideDragSource(source)
    tabs.doLayout()

    val container = manager.dockContainer as DockableEditorTabbedContainer
    val dropInfo = TabInfo(JLabel()).setText(dragged).setObject(file)
    container.setDropTarget(tabs, dropInfo)
    val point = pointOn(tabs, dropPoint(tabs))
    tabs.startDropOver(dropInfo, point)
    tabs.doLayout()
    // The next mouse move sees the drop preview under the pointer.
    tabs.processDropOver(dropInfo, point)
    tabs.doLayout()

    file.putUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN, true)
    manager.closeFile(window = window, composite = source.composite, runChecks = false)
    val content = DockableEditor(img = null, file = file, presentation = Presentation(dragged), preferredSize = Dimension(),
                                 isPinned = isPinned, isSingletonEditorInWindow = false, isSelected = isSelected)
    container.add(content, point)
    container.resetDropOver(content)
    file.putUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN, null)

    waitUntil { window.fileList.size == files.size }
    assertThat(window.selectedComposite?.file).isEqualTo(files.getValue("D"))
    return window.tabNames()
  }
}

private fun EditorWindow.tabNames(): List<String> = fileList.map { it.nameWithoutExtension }

private fun JBTabs.labelBounds(name: String): Rectangle {
  val info = tabs.first { (it.`object` as VirtualFile).nameWithoutExtension == name }
  return requireNotNull(getTabLabel(info)).bounds
}

/** Returns a point just after [bounds], where the drop preview appears. */
private fun pointJustAfter(bounds: Rectangle): Point = Point(bounds.x + bounds.width + 5, bounds.y + bounds.height / 2)

/** Returns a point just below [bounds], where the drop preview appears for vertical tabs. */
private fun pointJustBelow(bounds: Rectangle): Point = Point(bounds.x + bounds.width / 2, bounds.y + bounds.height + 5)

private fun center(bounds: Rectangle): Point = Point(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2)

/**
 * Returns a point on [component] that keeps its coordinates.
 *
 * [RelativePoint] returns `(0, 0)` for a component without a window, and a test component has no window.
 */
private fun pointOn(component: Component, point: Point): RelativePoint = object : RelativePoint(component, point) {
  override fun getPoint(aTargetComponent: Component?): Point {
    return if (aTargetComponent === component) Point(point) else super.getPoint(aTargetComponent)
  }
}
