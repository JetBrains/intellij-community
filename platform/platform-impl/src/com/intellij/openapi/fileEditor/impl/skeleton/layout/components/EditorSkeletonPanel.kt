// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.skeleton.layout.components

import java.awt.Graphics2D

internal class EditorSkeletonPanel private constructor(
  private val children: List<EditorSkeletonComponent>,
  private val layout: Layout,
  private val gap: Int = 0,
  private val padding: Padding = Padding(),
  private val rightAligned: Boolean = false,
  private val fillLast: Boolean = false,
) : EditorSkeletonComponent() {
  private val gaps = (children.size - 1).coerceAtLeast(0) * gap

  override val preferredWidth: Int = padding.horizontal + when (layout) {
    Layout.HORIZONTAL -> children.sumOf { it.preferredWidth } + gaps
    Layout.VERTICAL, Layout.FILL -> children.maxOfOrNull { it.preferredWidth } ?: 0
  }

  override val preferredHeight: Int = padding.vertical + when (layout) {
    Layout.VERTICAL -> children.sumOf { it.preferredHeight } + gaps
    Layout.HORIZONTAL, Layout.FILL -> children.maxOfOrNull { it.preferredHeight } ?: 0
  }

  override fun paintComponent(g: Graphics2D, width: Int, height: Int) {
    padding.paintBorder(g, width, height)
    val contentWidth = padding.contentWidth(width)
    val contentHeight = padding.contentHeight(height)
    var x = padding.leftInset
    var y = padding.topInset
    for ((index, child) in children.withIndex()) {
      when (layout) {
        Layout.HORIZONTAL -> {
          val childWidth = if (fillLast && index == children.lastIndex) {
            padding.contentWidth(width, x)
          }
          else child.preferredWidth
          child.paint(g, x, y, childWidth, contentHeight)
          x += childWidth + gap
        }
        Layout.VERTICAL -> {
          val childWidth = if (rightAligned) child.preferredWidth else contentWidth
          val childX = if (rightAligned) padding.leftInset + contentWidth - childWidth else padding.leftInset
          child.paint(g, childX, y, childWidth, child.preferredHeight)
          y += child.preferredHeight + gap
        }
        Layout.FILL -> child.paint(g, x, y, contentWidth, contentHeight)
      }
    }
  }

  companion object {
    operator fun invoke(
      layout: Layout,
      gap: Int = 0,
      padding: Padding = Padding(),
      rightAligned: Boolean = false,
      fillLast: Boolean = false,
      content: EditorSkeletonBuilder.() -> Unit,
    ): EditorSkeletonPanel = EditorSkeletonPanel(
      buildList { EditorSkeletonBuilder(this).content() }, layout, gap, padding, rightAligned, fillLast,
    )
  }
}
