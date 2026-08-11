// Copyright 2000-2026 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.application.ThreadingSupport
import com.intellij.openapi.application.impl.TestOnlyThreading
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.editor.NullGraphics2D
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.testFramework.LoggedErrorProcessor
import java.awt.Rectangle
import java.util.concurrent.atomic.AtomicBoolean

class EditorComponentImplTest : AbstractEditorTest() {
  fun testPaintComponentSoftlyProhibitsLockAcquisitionByCustomRenderer() {
    initText("abc")
    setEditorVisibleSize(20, 5)

    val rendererInvoked = AtomicBoolean()
    editor.markupModel.addRangeHighlighter(0, 1, HighlighterLayer.ERROR, null, HighlighterTargetArea.EXACT_RANGE).customRenderer =
      CustomHighlighterRenderer { _, _, _ ->
        rendererInvoked.set(true)
        runReadActionBlocking { }
      }

    var error: Throwable? = null
    TestOnlyThreading.releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack {
      error = LoggedErrorProcessor.executeAndReturnLoggedError {
        (editor as EditorImpl).contentComponent.paintComponent(NullGraphics2D(Rectangle(0, 0, 100, 100)))
      }

    }

    assertTrue(rendererInvoked.get())
    assertTrue(error is ThreadingSupport.LockAccessDisallowed)
    assertTrue(error?.message?.contains("The Read/Write lock is disallowed during paint") == true)
  }
}
