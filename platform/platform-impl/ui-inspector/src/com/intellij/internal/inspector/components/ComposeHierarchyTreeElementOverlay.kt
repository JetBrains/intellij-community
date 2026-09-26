// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.components

import com.intellij.internal.inspector.CompositionObserver
import com.intellij.internal.inspector.IdeUiInspectorBundle
import com.intellij.openapi.util.IconLoader
import com.intellij.platform.compose.swing.ComposeSwingPanel
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.tree.ui.DefaultTreeUI
import com.intellij.util.ui.JBUI
import com.intellij.xml.util.XmlStringUtil
import java.awt.Component
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.util.function.BooleanSupplier
import javax.swing.Icon

/** Adds Compose icons and recomposition counts to the component tree. */
internal class ComposeHierarchyTreeElementOverlay(
  private val compositionObserver: CompositionObserver,
  private val showRecompositionCounts: BooleanSupplier,
) : HierarchyTree.ElementOverlay {
  private var compositionListener: Runnable? = null

  override fun getIcon(component: Component): Icon? = if (component is ComposeSwingPanel) ICON else null

  override fun paint(
    tree: HierarchyTree,
    graphics: Graphics,
    component: Component,
    row: Int,
    bounds: Rectangle,
  ) {
    if (!showRecompositionCounts.asBoolean) return

    val visible = tree.visibleRect
    val visibleRight = visible.x + visible.width
    val selectionRight = visibleRight - JBUI.scale(DefaultTreeUI.HORIZONTAL_SELECTION_OFFSET)
    val metrics = graphics.fontMetrics
    val count = getRecompositionCount(component, bounds, selectionRight, metrics) ?: return

    // Clear renderer text behind the count.
    graphics.color = RenderingUtil.getBackground(tree)
    graphics.fillRect(
      count.bounds.x - COUNT_PADDING,
      bounds.y,
      visibleRight - (count.bounds.x - COUNT_PADDING),
      bounds.height,
    )

    graphics.color = RenderingUtil.getForeground(tree, tree.isRowSelected(row))
    graphics.drawString(count.text, count.bounds.x, bounds.y + metrics.ascent + (bounds.height - metrics.height) / 2)
  }

  override fun getToolTipText(
    tree: HierarchyTree,
    event: MouseEvent,
    component: Component,
    bounds: Rectangle,
  ): String? {
    if (!showRecompositionCounts.asBoolean) return null

    val visible = tree.visibleRect
    val selectionRight = visible.x + visible.width - JBUI.scale(DefaultTreeUI.HORIZONTAL_SELECTION_OFFSET)
    val count = getRecompositionCount(component, bounds, selectionRight, tree.getFontMetrics(tree.font)) ?: return null
    return if (count.bounds.contains(event.point)) {
      XmlStringUtil.wrapInHtml(IdeUiInspectorBundle.message("action.Anonymous.description.ShowComposeRecompositionCounts"))
    }
    else {
      null
    }
  }

  override fun install(repaint: Runnable) {
    if (compositionListener != null) return
    compositionListener = repaint
    compositionObserver.addCompositionListener(repaint)
  }

  override fun uninstall() {
    compositionListener?.let(compositionObserver::removeCompositionListener)
    compositionListener = null
  }

  private fun getRecompositionCount(
    component: Component,
    bounds: Rectangle,
    selectionRight: Int,
    metrics: FontMetrics,
  ): RecompositionCount? {
    val recompositions = compositionObserver.recompositionsOf(component) ?: return null

    val text = recompositions.toString()
    val textWidth = metrics.stringWidth(text)
    return RecompositionCount(
      text,
      Rectangle(selectionRight - textWidth - COUNT_PADDING, bounds.y, textWidth, bounds.height),
    )
  }

  private data class RecompositionCount(val text: String, val bounds: Rectangle)

  companion object {
    private val COUNT_PADDING = JBUI.scale(6)

    @JvmField
    val ICON: Icon = IconLoader.getIcon(
      "com/intellij/internal/inspector/icons/compose.svg",
      ComposeHierarchyTreeElementOverlay::class.java.classLoader,
    )
  }
}
