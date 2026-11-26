// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.ui.IslandsState
import com.intellij.ui.IslandsStateExtension
import com.intellij.ui.tabs.TabInfo
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.awt.Shape
import javax.swing.JLabel

/**
 * Tests the shape of the drop placeholder that the editor shows while a tab is dragged over the content of an editor window.
 *
 * The placeholder must have one form for each dragged tab: it covers the header (IJPL-257638).
 * It shows the window that the drop creates, so only its corners at a corner of the editor area are rounded.
 */
@TestApplication
@ExtendWith(IslandsStateExtension::class)
internal class DockableEditorTabDropAreaTest {
  private val projectFixture = projectFixture(
    openProjectTask = OpenProjectTask {
      beforeInitTasks += { it.putUserData(FileEditorManagerKeys.ALLOW_IN_LIGHT_PROJECT, true) }
    },
    openAfterCreation = true,
  )
  private val managerFixture = projectFixture.fileEditorManagerFixture(initDockableContentFactory = true)

  @Test
  fun `the placeholder covers the header when the first tab is dragged to a side`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val shape = dropAreaShape(dragged = "A", dropPoint = LEFT_SIDE)
    assertThat(shape.bounds).isEqualTo(Rectangle(0, 0, WIDTH / 2, HEIGHT))
  }

  @Test
  fun `the placeholder covers the header when the second tab is dragged to a side`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val shape = dropAreaShape(dragged = "B", dropPoint = LEFT_SIDE)
    assertThat(shape.bounds).isEqualTo(Rectangle(0, 0, WIDTH / 2, HEIGHT))
  }

  @Test
  fun `the placeholder covers the right half for a drop on the right side`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val shape = dropAreaShape(dragged = "B", dropPoint = Point(WIDTH - 50, HEIGHT / 2))
    assertThat(shape.bounds).isEqualTo(Rectangle(WIDTH / 2, 0, WIDTH / 2, HEIGHT))
  }

  @Test
  fun `the placeholder rounds only the corners at the editor area corners`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val shape = dropAreaShape(dragged = "B", dropPoint = LEFT_SIDE)
    assertThat(shape.contains(WIDTH / 4.0, HEIGHT / 2.0)).isTrue()
    assertThat(shape.contains(0.5, 0.5)).isFalse()
    assertThat(shape.contains(0.5, HEIGHT - 0.5)).isFalse()
    assertThat(shape.contains(WIDTH / 2 - 0.5, 0.5)).isTrue()
    assertThat(shape.contains(WIDTH / 2 - 0.5, HEIGHT - 0.5)).isTrue()
  }

  @Test
  fun `the placeholder has square corners outside the Islands theme`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    IslandsState.setEnabled(false, false)
    val shape = dropAreaShape(dragged = "B", dropPoint = LEFT_SIDE)
    assertThat(shape.contains(0.5, 0.5)).isTrue()
    assertThat(shape.contains(0.5, HEIGHT - 0.5)).isTrue()
  }

  @Test
  fun `the border of the editor area does not make a corner square`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val shape = dropAreaShape(dragged = "B", dropPoint = LEFT_SIDE, borderWidth = 2)
    assertThat(shape.contains(0.5, 0.5)).isFalse()
    assertThat(shape.contains(0.5, HEIGHT - 0.5)).isFalse()
  }

  @Test
  fun `the placeholder has square corners beside a window narrower than the arc`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val shape = dropAreaShape(dragged = "B", dropPoint = LEFT_SIDE, leftWindowWidth = 12)
    assertThat(shape.contains(0.5, 0.5)).isTrue()
    assertThat(shape.contains(0.5, HEIGHT - 0.5)).isTrue()
  }

  /**
   * Opens the tabs `A` and `B`, drags the [dragged] tab out, and moves it over [dropPoint] in the same editor window.
   *
   * The editor area has a border of [borderWidth], and a window of [leftWindowWidth] is on the left of the editor window.
   *
   * @return the shape of the drop placeholder
   */
  private suspend fun dropAreaShape(dragged: String, dropPoint: Point, borderWidth: Int = 0, leftWindowWidth: Int = 0): Shape {
    val manager = managerFixture.get()
    val files = listOf("A", "B").associateWith { LightVirtualFile("$it.txt") }
    for (file in files.values) {
      manager.openFile(file, true)
    }
    val window = requireNotNull(manager.currentWindow)
    val tabs = window.tabbedPane.editorTabs
    tabs.setBounds(borderWidth + leftWindowWidth, borderWidth, WIDTH, HEIGHT)
    tabs.doLayout()

    val file = files.getValue(dragged)
    val source = requireNotNull(tabs.findInfo(file))
    (source.dragOutDelegate as EditorTabbedContainerDragOutDelegate).hideDragSource(source)
    tabs.doLayout()

    val container = manager.dockContainer as DockableEditorTabbedContainer
    container.splitters.border = JBUI.Borders.empty(borderWidth)
    container.splitters.size = Dimension(WIDTH + leftWindowWidth + 2 * borderWidth, HEIGHT + 2 * borderWidth)
    val dropInfo = TabInfo(JLabel()).setText(dragged).setObject(file)
    container.setDropTarget(tabs, dropInfo)
    val point = pointOn(tabs, dropPoint)
    tabs.startDropOver(dropInfo, point)
    tabs.doLayout()
    tabs.processDropOver(dropInfo, point)
    tabs.doLayout()
    return requireNotNull(container.getDropAreaShape())
  }
}

private const val WIDTH = 1000
private const val HEIGHT = 200
/** A point in the left drop zone, below the header. A center drop into the source window shows no placeholder. */
private val LEFT_SIDE = Point(50, HEIGHT * 3 / 5)
