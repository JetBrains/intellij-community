// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("FunctionName")

package com.intellij.openapi.fileEditor.impl.skeleton.layout

import com.intellij.openapi.fileEditor.impl.skeleton.layout.components.EditorSkeletonBlock.Width.GUTTER_NORMAL
import com.intellij.openapi.fileEditor.impl.skeleton.layout.components.EditorSkeletonBlock.Width.GUTTER_SMALL
import com.intellij.openapi.fileEditor.impl.skeleton.layout.components.EditorSkeletonBuilder
import com.intellij.openapi.fileEditor.impl.skeleton.layout.components.Layout.HORIZONTAL
import com.intellij.openapi.fileEditor.impl.skeleton.layout.components.Layout.VERTICAL
import com.intellij.openapi.fileEditor.impl.skeleton.layout.components.Padding
import com.intellij.openapi.fileEditor.impl.skeleton.layout.components.Side

internal fun EditorSkeletonBuilder.Gutter() = Panel(
  layout = HORIZONTAL,
  gap = GUTTER_LINE_NUMBERS_AND_ICONS_GAP,
  padding = Padding(borderSide = Side.RIGHT),
) {
  LineNumbers()
  GutterIcons()
}

private fun EditorSkeletonBuilder.LineNumbers() = Panel(
  layout = VERTICAL,
  gap = LINES_GAP,
  padding = Padding(top = OUTER_PADDING, left = LINE_NUMBERS_LEFT_PADDING, bottom = OUTER_PADDING),
  rightAligned = true,
) {
  repeat(LINE_COUNT) {
    Block(if (it < 9) GUTTER_SMALL else GUTTER_NORMAL)
  }
}

private fun EditorSkeletonBuilder.GutterIcons() = Panel(
  layout = VERTICAL,
  gap = LINES_GAP,
  padding = Padding(top = OUTER_PADDING, bottom = OUTER_PADDING, right = GUTTER_ICONS_RIGHT_PADDING),
  rightAligned = true,
) {
  repeat(LINE_COUNT) {
    if (it in GUTTER_ICON_LINES) Block(GUTTER_NORMAL) else Empty(GUTTER_NORMAL)
  }
}

private val GUTTER_ICON_LINES = setOf(2, 4, 15, 25, 30)
private const val GUTTER_ICONS_RIGHT_PADDING = 22
private const val LINE_NUMBERS_LEFT_PADDING = 20
private const val LINE_COUNT = 100

internal const val GUTTER_LINE_NUMBERS_AND_ICONS_GAP = 4
internal const val OUTER_PADDING = 2
internal const val LINES_GAP = 6
