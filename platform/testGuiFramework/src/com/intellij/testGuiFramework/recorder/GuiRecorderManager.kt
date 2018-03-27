// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.recorder

import com.intellij.openapi.editor.LogicalPosition
import com.intellij.testGuiFramework.recorder.ui.GuiScriptEditorFrame
import java.util.concurrent.Future

/**
 * @author Sergey Karashevich
 */
object GuiRecorderManager {

  enum class States {IDLE, COMPILING, COMPILATION_ERROR, COMPILATION_DONE, RUNNING, RUNNING_ERROR, TEST_INIT }

  var state: States = States.IDLE

  val frame by lazy {
    GuiScriptEditorFrame()
  }
  var currentTask: Future<*>? = null

  fun cancelCurrentTask() {
    if (currentTask != null && !currentTask!!.isDone) currentTask!!.cancel(true)
  }

  fun getEditor() = frame.guiScriptEditorPanel.editor

  fun placeCaretToEnd() {
    val caretModel = getEditor().caretModel
    val lineCount = getEditor().document.lineCount
    caretModel.moveToLogicalPosition(LogicalPosition(lineCount + 1, 0))
  }
}

