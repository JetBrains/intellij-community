// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.editor.addComponentInlay
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.awt.Dimension
import javax.swing.JComponent

@TestApplication
class ComponentInlayTest {
  /**
   * A component can report a negative preferred size before it joins the Swing hierarchy.
   * `ComposePanel` does this. The block inlay model rejects a negative size, so the size must clamp to zero.
   */
  @Test
  @Timeout(30)
  fun `negative preferred size clamps to zero when the inlay is added`(): Unit = timeoutRunBlocking {
    val size = withEditor { editor ->
      val inlay = editor.addComponentInlay(0, InlayProperties(), FixedPreferredSizeComponent(Dimension(-1, -1)))!!
      Dimension(inlay.widthInPixels, inlay.heightInPixels)
    }

    assertThat(size).isEqualTo(Dimension(0, 0))
  }

  @Test
  @Timeout(30)
  fun `negative preferred size clamps to zero during layout`(): Unit = timeoutRunBlocking {
    val (sizeBefore, sizeAfter) = withEditor { editor ->
      val component = FixedPreferredSizeComponent(Dimension(100, 50))
      val inlay = editor.addComponentInlay(0, InlayProperties(), component)!!
      val sizeBefore = Dimension(inlay.widthInPixels, inlay.heightInPixels)

      component.reportedSize = Dimension(-1, -1)
      component.parent.doLayout()

      sizeBefore to Dimension(inlay.widthInPixels, inlay.heightInPixels)
    }

    assertThat(sizeBefore).isEqualTo(Dimension(100, 50))
    assertThat(sizeAfter).isEqualTo(Dimension(0, 0))
  }

  private suspend fun <T> withEditor(action: suspend (EditorImpl) -> T): T {
    return withContext(Dispatchers.EDT) {
      val editorFactory = EditorFactory.getInstance()
      val editor = editorFactory.createEditor(DocumentImpl("abc", true)) as EditorImpl
      try {
        action(editor)
      }
      finally {
        editorFactory.releaseEditor(editor)
      }
    }
  }

  private class FixedPreferredSizeComponent(var reportedSize: Dimension) : JComponent() {
    override fun getPreferredSize(): Dimension = Dimension(reportedSize)

    override fun getMinimumSize(): Dimension = Dimension(reportedSize)
  }
}
