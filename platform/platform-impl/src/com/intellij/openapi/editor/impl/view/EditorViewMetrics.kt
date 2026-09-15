// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

internal class EditorViewMetrics(
  @JvmField val plainSpaceWidth: Float,
  @JvmField val lineHeight: Int,
  @JvmField val descent: Int,
  @JvmField val charHeight: Int,
  @JvmField val maxCharWidth: Float,
  @JvmField val capHeight: Int,
  @JvmField val topOverhang: Int,
  @JvmField val bottomOverhang: Int,
  @JvmField val caretHeight: Int,
) {
  @JvmField val ascent: Int = lineHeight - descent
}
