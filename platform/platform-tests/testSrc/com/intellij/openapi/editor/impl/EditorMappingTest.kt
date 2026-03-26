// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import javax.swing.BorderFactory

class EditorMappingTest : AbstractEditorTest() {
  fun `test yToVisualLine with top inset`() {
    initText("foo\nbar")
    editor.contentComponent.border = BorderFactory.createEmptyBorder(5, 0, 0, 0)
    val y = editor.visualLineToY(1)
    assertEquals(1, editor.yToVisualLine(y))
  }
}
