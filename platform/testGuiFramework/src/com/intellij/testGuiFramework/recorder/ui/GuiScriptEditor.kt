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
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testGuiFramework.recorder.Writer
import com.intellij.ui.EditorTextField
import java.lang.Boolean
import kotlin.with

/**
 * @author Sergey Karashevich
 */
class GuiScriptEditor {

  var myEditor: EditorEx? = null
  var syncEditor = false

  fun getPanel() = myEditor!!.component

  init {
    val editorFactory = EditorFactory.getInstance()
    val editorDocument = editorFactory.createDocument(Writer.getScript())
    val editor = (editorFactory.createEditor(editorDocument, ProjectManager.getInstance().defaultProject) as EditorEx)
    EditorTextField.SUPPLEMENTARY_KEY.set(editor, Boolean.TRUE)
    editor.colorsScheme = EditorColorsManager.getInstance().globalScheme
    with(editor.settings) {
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
    editor.caretModel.moveToLogicalPosition(pos)
    editor.highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(LightVirtualFile("a.kt"), editor.colorsScheme, null)

    myEditor = editor

    //let editor be synchronised by default
    syncEditor = true
    val editorImpl = (myEditor as EditorImpl)

//        Disposer.register(editorImpl.disposable, Disposable {
//            GuiRecorderComponent.getFrame()!!.getGuiScriptEditorPanel().createAndAddGuiScriptEditor()
//            editorImpl
//        })
  }

  //Editor should be realised before Application is closed
  fun releaseEditor() {
    EditorFactory.getInstance().releaseEditor(myEditor!!)
    myEditor = null
  }

}