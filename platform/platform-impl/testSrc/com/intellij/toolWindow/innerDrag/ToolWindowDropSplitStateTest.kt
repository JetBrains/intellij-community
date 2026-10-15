// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.innerDrag

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.wm.impl.ToolWindowImpl
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.openapi.wm.impl.content.tabActions.ContentTabActionProvider
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.toolWindow.InternalDecoratorImpl.Mode
import com.intellij.ui.content.Content
import com.intellij.ui.content.Content.TEMPORARY_REMOVED_KEY
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Checks the decorator state that [ToolWindowInnerDragHelper] reads after a drop to report the split actions.
 *
 * The helper reports a split action when the target decorator was not a split before the drop and is a split after it.
 * The helper reports `TW.Unsplit` when the source decorator was a cell and its content manager is disposed after the drop.
 * [drag] does the same calls on the decorators as a drag and a drop of the helper.
 */
@TestApplication
internal class ToolWindowDropSplitStateTest {
  private val project by projectFixture()

  @TestDisposable
  private lateinit var disposable: Disposable

  private lateinit var toolWindow: ToolWindowImpl
  private lateinit var root: InternalDecoratorImpl
  private lateinit var initialContent: Content

  @BeforeEach
  fun setUp(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    // Real ToolWindowImpl cells initialize tab-label actions. The Code With Me provider hard-casts the tool window manager.
    ExtensionTestUtil.maskExtensions(ContentTabActionProvider.EP_NAME, emptyList(), disposable)
    toolWindow = ToolWindowManagerImpl.registerLocalToolWindowForTest(project, "TestToolWindow", disposable)
    root = toolWindow.getOrCreateDecoratorComponent()
    initialContent = toolWindow.contentManager.contents.single()
  }

  @Test
  fun `a drop on the right side of a single decorator makes a horizontal split`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val dragged = addContent(root, "dragged")

      drag(dragged, from = root, to = root, dropSide = SwingConstants.RIGHT)

      assertThat(root.mode).isEqualTo(Mode.HORIZONTAL_SPLIT)
      assertThat(contentsOf(findDecorator(dragged))).containsExactly(dragged)
      assertThat(contentsOf(findDecorator(initialContent))).containsExactly(initialContent)
    }

  @Test
  fun `a drop on the bottom side of a single decorator makes a vertical split`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val dragged = addContent(root, "dragged")

      drag(dragged, from = root, to = root, dropSide = SwingConstants.BOTTOM)

      assertThat(root.mode).isEqualTo(Mode.VERTICAL_SPLIT)
    }

  @Test
  fun `a drop on the center or at a tab index does not make a split`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val dragged = addContent(root, "dragged")

      drag(dragged, from = root, to = root, dropSide = SwingConstants.CENTER)
      assertThat(root.mode).isEqualTo(Mode.SINGLE)

      drag(dragged, from = root, to = root, dropSide = -1, dropIndex = 0)
      assertThat(root.mode).isEqualTo(Mode.SINGLE)
      assertThat(contentsOf(root)).containsExactly(dragged, initialContent)
    }

  @Test
  fun `a drag of the only tab of a single decorator on its own side does not make a split`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      drag(initialContent, from = root, to = root, dropSide = SwingConstants.RIGHT)

      assertThat(root.mode).isEqualTo(Mode.SINGLE)
      assertThat(contentsOf(root)).containsExactly(initialContent)
    }

  @Test
  fun `a drop on the side of a cell makes the cell a split`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val right = splitRootToTheRight("right")
      val rightCell = findDecorator(right)
      val dragged = addContent(rightCell, "dragged")
      assertThat(rightCell.mode).isEqualTo(Mode.CELL)

      drag(dragged, from = rightCell, to = rightCell, dropSide = SwingConstants.BOTTOM)

      assertThat(rightCell.mode).isEqualTo(Mode.VERTICAL_SPLIT)
      assertThat(rightCell.contentManager.isDisposed).isFalse()
    }

  @Test
  fun `the unsplit of an emptied cell disposes the content manager of the cell`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val right = splitRootToTheRight("right")
      val leftCell = findDecorator(initialContent)
      val rightCell = findDecorator(right)

      drag(right, from = rightCell, to = leftCell, dropSide = SwingConstants.CENTER)

      assertThat(rightCell.contentManager.isDisposed).isTrue()
      assertThat(root.mode).isEqualTo(Mode.SINGLE)
      assertThat(contentsOf(root)).containsExactlyInAnyOrder(initialContent, right)
    }

  @Test
  fun `the unsplit of an emptied cell next to a split disposes the content manager of the cell`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val right = splitRootToTheRight("right")
      val leftCell = findDecorator(initialContent)
      val rightCell = findDecorator(right)
      val bottom = addContent(rightCell, "bottom")
      drag(bottom, from = rightCell, to = rightCell, dropSide = SwingConstants.BOTTOM)
      assertThat(rightCell.mode).isEqualTo(Mode.VERTICAL_SPLIT)

      drag(initialContent, from = leftCell, to = findDecorator(bottom), dropSide = SwingConstants.CENTER)

      assertThat(leftCell.contentManager.isDisposed).isTrue()
      // The nested split of the right cell takes the place of the root split.
      assertThat(root.mode).isEqualTo(Mode.VERTICAL_SPLIT)
      assertThat(contentsOf(findDecorator(bottom))).containsExactlyInAnyOrder(bottom, initialContent)
    }

  @Test
  fun `a drop on the side of another cell that empties the source cell makes a split and unsplits the source cell`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val right = splitRootToTheRight("right")
      val leftCell = findDecorator(initialContent)
      val rightCell = findDecorator(right)

      drag(right, from = rightCell, to = leftCell, dropSide = SwingConstants.BOTTOM, afterDrop = {
        assertThat(leftCell.mode).isEqualTo(Mode.VERTICAL_SPLIT)
      })

      assertThat(rightCell.contentManager.isDisposed).isTrue()
      assertThat(root.mode).isEqualTo(Mode.VERTICAL_SPLIT)
    }

  @Test
  fun `a cell that keeps a content is not unsplit`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val right = splitRootToTheRight("right")
      val leftCell = findDecorator(initialContent)
      val rightCell = findDecorator(right)
      val dragged = addContent(rightCell, "dragged")

      drag(dragged, from = rightCell, to = leftCell, dropSide = SwingConstants.CENTER)

      assertThat(rightCell.contentManager.isDisposed).isFalse()
      assertThat(root.mode).isEqualTo(Mode.HORIZONTAL_SPLIT)
      assertThat(contentsOf(rightCell)).containsExactly(right)
    }

  /**
   * Does the same calls on the decorators as `startDrag` and `processDragFinish` of [ToolWindowInnerDragHelper]
   * for a drop of [content] from [from] into [to].
   * [afterDrop] runs after the drop and before the unsplit of the source.
   */
  private fun drag(
    content: Content,
    from: InternalDecoratorImpl,
    to: InternalDecoratorImpl,
    dropSide: Int,
    dropIndex: Int = -1,
    afterDrop: () -> Unit = {},
  ) {
    content.putUserData(TEMPORARY_REMOVED_KEY, true)
    try {
      from.setSplitUnsplitInProgress(true)
      try {
        from.contentManager.removeContent(content, false)
      }
      finally {
        from.setSplitUnsplitInProgress(false)
      }

      if (from === to && from.contentManager.contentCount == 0) {
        from.contentManager.addContent(content)
      }
      else {
        to.splitWithContent(content, dropSide, dropIndex)
      }
      afterDrop()

      if (from.contentManager.isEmpty) {
        from.unsplit(content)
      }
    }
    finally {
      content.putUserData(TEMPORARY_REMOVED_KEY, null)
    }
  }

  /**
   * Splits the root decorator with a new content on the right side and returns the new content.
   */
  private fun splitRootToTheRight(displayName: String): Content {
    val content = createContent(displayName)
    root.splitWithContent(content, SwingConstants.RIGHT, -1)
    assertThat(root.mode).isEqualTo(Mode.HORIZONTAL_SPLIT)
    return content
  }

  private fun addContent(decorator: InternalDecoratorImpl, displayName: String): Content {
    val content = createContent(displayName)
    decorator.contentManager.addContent(content)
    return content
  }

  private fun createContent(displayName: String): Content =
    ContentFactory.getInstance().createContent(JPanel(), displayName, false)

  private fun contentsOf(decorator: InternalDecoratorImpl): List<Content> = decorator.contentManager.contentsRecursively

  private fun findDecorator(content: Content): InternalDecoratorImpl {
    val contentManager = requireNotNull(content.manager) { "The content '${content.displayName}' has no content manager" }
    return requireNotNull(InternalDecoratorImpl.findNearestDecorator(contentManager.component)) {
      "No decorator holds the content '${content.displayName}'"
    }
  }
}
