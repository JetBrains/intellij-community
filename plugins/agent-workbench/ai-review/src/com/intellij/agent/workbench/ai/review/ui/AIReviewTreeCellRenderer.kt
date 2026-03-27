// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.ai.review.ui

import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.components.JBHtmlPane
import com.intellij.ui.components.JBHtmlPaneConfiguration
import com.intellij.ui.components.JBHtmlPaneStyleConfiguration
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import com.intellij.util.ui.html.width
import java.awt.Component
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JTree
import javax.swing.plaf.basic.BasicTreeUI
import javax.swing.text.View
import javax.swing.tree.TreeCellRenderer
import kotlin.math.ceil
import kotlin.math.max

/**
 * Cell renderer that renders [AIReviewDescriptionNode] with possible Markdown in description.
 * Delegates to the base cell renderer for other node types.
 *
 * @see MarkdownToHtmlConverter
 */
internal class AIReviewTreeCellRenderer : TreeCellRenderer {

  private val baseCellRenderer = object : NodeRenderer() {
    public override fun getMinHeight(): Int = super.getMinHeight()
  }

  /**
   * Adds mouse listeners to the tree that dispatch all mouse events to the JBHtmlPane renderer components.
   * This enables proper handling of hyperlinks and other interactive elements in the HTML content.
   */
  fun setupInteractiveHTMLContentSupport(tree: JTree) {
    if (!Registry.`is`("ai.review.allow.interactive.elements.in.problem.description.renderer", true)) return

    val mouseListener = object : MouseAdapter() {
      private val initialTreeCursor = tree.cursor
      private var lastRendererComponent: JComponent? = null

      override fun mouseEntered(e: MouseEvent) {
        dispatchToRenderedComponent(tree, e)
      }

      override fun mouseExited(e: MouseEvent) {
        restoreCursor()
      }

      override fun mouseMoved(e: MouseEvent) {
        dispatchToRenderedComponent(tree, e)
      }

      override fun mouseClicked(e: MouseEvent) {
        dispatchToRenderedComponent(tree, e)
      }

      private fun dispatchToRenderedComponent(tree: JTree, e: MouseEvent) {
        val row = tree.getRowForLocation(e.x, e.y)
        if (row < 0) return
        val path = tree.getPathForRow(row)
        val node = path.lastPathComponent
        val bounds = tree.getPathBounds(path) ?: return

        val rendererComponent = getTreeCellRendererComponent(
          tree, node, tree.isRowSelected(row), tree.isExpanded(row),
          tree.model.isLeaf(node), row, false)

        if (rendererComponent !is JComponent) {
          restoreCursor()
          return
        }

        if (rendererComponent is JEditorPane) {
          // Use the actual visible width, not bounds.width (which is the preferred width and
          // can be much wider than the visible area). During painting the tree clips to the
          // visible rect, so the HTML View wraps at this narrower width. We must match that
          // width here so viewToModel() maps coordinates to the correct wrapped positions.
          val visibleWidth = tree.visibleRect.width - bounds.x
          rendererComponent.setBounds(0, 0, visibleWidth, bounds.height)
          val rootView = rendererComponent.ui.getRootView(rendererComponent)
          val componentInsets = rendererComponent.insets

          rootView.setSize(
            (visibleWidth - componentInsets.left - componentInsets.right).toFloat(),
            (bounds.height - componentInsets.top - componentInsets.bottom).toFloat()
          )
        }

        val x = e.x - bounds.x
        val y = e.y - bounds.y

        val convertedEvent = MouseEvent(
          rendererComponent,
          e.id,
          e.`when`,
          e.modifiersEx,
          x,
          y,
          e.clickCount,
          e.isPopupTrigger,
          e.button
        )

        if (rendererComponent is JEditorPane) {
          rendererComponent.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE)
        }
        UIUtil.uiTraverser(rendererComponent).filter(ActionButton::class.java).forEach { button ->
          val event = AnActionEvent.createEvent(
            ActionToolbar.getDataContextFor(button), button.presentation, AIReviewProblemsViewPanel.PLACE,
            ActionUiKind.NONE, convertedEvent)
          ActionUtil.updateAction(button.action, event)
        }
        rendererComponent.dispatchEvent(convertedEvent)

        if (e.id == MouseEvent.MOUSE_MOVED) {
          tree.cursor = rendererComponent.cursor
          lastRendererComponent = rendererComponent
        }
      }

      private fun restoreCursor() {
        tree.cursor = initialTreeCursor
        lastRendererComponent = null
      }
    }

    tree.addMouseListener(mouseListener)
    tree.addMouseMotionListener(mouseListener)
  }

  override fun getTreeCellRendererComponent(
    tree: JTree,
    value: Any,
    selected: Boolean,
    expanded: Boolean,
    leaf: Boolean,
    row: Int,
    hasFocus: Boolean,
  ): Component {

    return when (value) {
      is AIReviewFeedbackNode -> tree.cellEditor.getTreeCellEditorComponent(tree, value, selected, expanded, leaf, row)
      !is AIReviewDescriptionNode -> {
        baseCellRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
      }
      else -> {
        JBHtmlPane(
          JBHtmlPaneStyleConfiguration.builder()
            .editorInlineContext(true)
            .build(),
          JBHtmlPaneConfiguration.builder()
            .customStyleSheet("pre {white-space: pre-wrap}")
            .build())
          .apply {
            isEditable = false
            isOpaque = false
            foreground = UIUtil.getTreeForeground(selected, hasFocus)
            background = UIUtil.getTreeBackground(selected, hasFocus)
            border = JBUI.Borders.empty(0, 10, 0, 20)

            AccessibleContextUtil.setName(this, value.description)
            text = value.htmlDescription

            val rowIndent = JBUI.scale(calculateRowIndent(tree, row))
            val availableWidth = tree.visibleRect.width - rowIndent

            if (availableWidth > 0) {
              val rootView = getUI().getRootView(this)
              // Tell the root View how much horizontal space is available (minus border insets)
              // so it reflows/wraps the HTML content at the correct width.
              // Then query the resulting height — this is the only way to get the true
              // wrapped-content height from the View hierarchy.
              val contentWidth = (availableWidth - insets.width).toFloat()
              rootView.setSize(contentWidth, 0f)

              val contentHeight = ceil(rootView.getPreferredSpan(View.Y_AXIS).toDouble()).toInt()
              val prefSize = preferredSize
              preferredSize = Dimension(prefSize.width, max(contentHeight + insets.top + insets.bottom, prefSize.height))
            }
          }
      }
    }
  }

  /**
   * Calculates the horizontal indentation of a tree row without calling [JTree.getPathBounds],
   * which would cause infinite recursion when called from [getTreeCellRendererComponent].
   */
  private fun calculateRowIndent(tree: JTree, row: Int): Int {
    if (row < 0) return 0
    val path = tree.getPathForRow(row) ?: return 0
    val depth = path.pathCount //- 1 // since root is not visible, logically it should be -1, but it affects last line cutting in some width
    val ui = tree.ui
    if (ui is BasicTreeUI) {
      val totalIndent = ui.leftChildIndent + ui.rightChildIndent
      return totalIndent * depth
    }
    return 0
  }
}
