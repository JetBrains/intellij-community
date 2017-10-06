/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testGuiFramework.recorder.ui

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testGuiFramework.recorder.Writer
import com.intellij.ui.EditorTextField
import kotlin.with

/**
 * @author Sergey Karashevich
 */
class GuiScriptEditor {

  val myEditor: EditorEx

  fun getPanel() = myEditor.component

  init {
    val editorFactory = EditorFactory.getInstance()
    val editorDocument = editorFactory.createDocument(Writer.getScript())
    myEditor = editorFactory.createEditor(editorDocument, ProjectManager.getInstance().defaultProject) as EditorEx
    EditorTextField.SUPPLEMENTARY_KEY.set(myEditor, true)
    myEditor.colorsScheme = EditorColorsManager.getInstance().globalScheme
    with(myEditor.settings) {
      isLineNumbersShown = true
      isWhitespacesShown = true
      isLineMarkerAreaShown = false
      isIndentGuidesShown = false
      isFoldingOutlineShown = false
      additionalColumnsCount = 0
      additionalLinesCount = 0
      isRightMarginShown = true
    }

    val pos = LogicalPosition(0, 0)
    myEditor.caretModel.moveToLogicalPosition(pos)
    myEditor.highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(LightVirtualFile("a.kt"), myEditor.colorsScheme, null)
  }

  //Editor should be realised before Application is closed
  fun releaseEditor() {
    EditorFactory.getInstance().releaseEditor(myEditor)
  }

}