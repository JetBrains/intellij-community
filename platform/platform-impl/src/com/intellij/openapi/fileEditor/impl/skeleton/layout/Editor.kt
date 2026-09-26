// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("FunctionName")

package com.intellij.openapi.fileEditor.impl.skeleton.layout

import com.intellij.openapi.fileEditor.impl.skeleton.layout.components.EditorSkeletonBlock.Width
import com.intellij.openapi.fileEditor.impl.skeleton.layout.components.EditorSkeletonBlock.Width.EXTRA_LARGE
import com.intellij.openapi.fileEditor.impl.skeleton.layout.components.EditorSkeletonBlock.Width.LARGE
import com.intellij.openapi.fileEditor.impl.skeleton.layout.components.EditorSkeletonBlock.Width.NORMAL
import com.intellij.openapi.fileEditor.impl.skeleton.layout.components.EditorSkeletonBlock.Width.SMALL
import com.intellij.openapi.fileEditor.impl.skeleton.layout.components.EditorSkeletonBuilder
import com.intellij.openapi.fileEditor.impl.skeleton.layout.components.Layout.HORIZONTAL
import com.intellij.openapi.fileEditor.impl.skeleton.layout.components.Layout.VERTICAL
import com.intellij.openapi.fileEditor.impl.skeleton.layout.components.Padding
import com.intellij.openapi.fileEditor.impl.skeleton.layout.components.Side

internal fun EditorSkeletonBuilder.Editor() = Panel(
  layout = HORIZONTAL,
  gap = GUTTER_LINE_NUMBERS_AND_ICONS_GAP,
  padding = Padding(borderSide = Side.LEFT),
) {
  Panel(
    layout = VERTICAL,
    gap = LINES_GAP,
    padding = Padding(OUTER_PADDING, EDITOR_LEFT_GAP, OUTER_PADDING, EDITOR_LEFT_GAP),
  ) {
    EditorLines()
  }
}

private fun EditorSkeletonBuilder.EditorLines() = repeat(5) {
  Empty()
  Blocks(NORMAL, EXTRA_LARGE, SMALL)
  Empty()
  Blocks(LARGE, SMALL, SMALL)
  Empty()
  Blocks(SMALL)
  Blocks(EXTRA_LARGE, SMALL)
  Blocks(LARGE, indents = 1)
  Blocks(LARGE, indents = 1)
  Blocks(NORMAL, indents = 1)
  Blocks(SMALL, indents = 1)
  Blocks(NORMAL, SMALL, indents = 1)
  Empty()
  Blocks(NORMAL, NORMAL)
  Blocks(NORMAL, SMALL, indents = 1)
  Blocks(NORMAL, NORMAL, indents = 2)
  Blocks(NORMAL, NORMAL, SMALL, indents = 2)
  Empty()
  Blocks(NORMAL, NORMAL, indents = 1)
  Empty()
}

private fun EditorSkeletonBuilder.Blocks(vararg widths: Width, indents: Int = 0) = Panel(
  layout = HORIZONTAL,
  gap = BLOCKS_GAP,
  padding = Padding(left = INDENT_WIDTH * indents),
) {
  for (width in widths) {
    Block(width)
  }
}

private const val EDITOR_LEFT_GAP = 6
private const val INDENT_WIDTH = 30
private const val BLOCKS_GAP = 6
