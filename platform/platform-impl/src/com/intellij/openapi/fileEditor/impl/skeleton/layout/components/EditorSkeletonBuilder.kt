// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.skeleton.layout.components

import com.intellij.openapi.fileEditor.impl.skeleton.layout.components.EditorSkeletonBlock.Width

@SkeletonDsl
@Suppress("FunctionName")
internal class EditorSkeletonBuilder(private val children: MutableList<EditorSkeletonComponent>) {
  fun Panel(
    layout: Layout,
    gap: Int = 0,
    padding: Padding = Padding(),
    rightAligned: Boolean = false,
    fillLast: Boolean = false,
    content: EditorSkeletonBuilder.() -> Unit,
  ) {
    children.add(EditorSkeletonPanel(layout, gap, padding, rightAligned, fillLast, content))
  }

  fun Block(width: Width) {
    children.add(EditorSkeletonBlock(width))
  }

  fun Empty(width: Width = Width.NORMAL) {
    children.add(EditorSkeletonSpace(width.width, EditorSkeletonBlock.HEIGHT))
  }
}

@DslMarker
@Target(AnnotationTarget.CLASS)
private annotation class SkeletonDsl
