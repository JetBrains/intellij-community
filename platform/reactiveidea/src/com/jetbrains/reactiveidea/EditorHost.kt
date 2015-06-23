/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.reactiveidea

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId
import com.intellij.openapi.editor.event.CaretAdapter
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.jetbrains.reactivemodel.Path
import com.jetbrains.reactivemodel.ReactiveModel
import com.jetbrains.reactivemodel.models.ListModel
import com.jetbrains.reactivemodel.models.MapModel
import com.jetbrains.reactivemodel.models.PrimitiveModel
import com.jetbrains.reactivemodel.putIn
import com.jetbrains.reactivemodel.signals.reaction
import com.jetbrains.reactivemodel.util.Guard
import com.jetbrains.reactivemodel.util.Lifetime

public class EditorHost(val lifetime: Lifetime, val reactiveModel: ReactiveModel, val path: Path, val editor: Editor, val providesMarkup: Boolean) {
  companion object {
    val editorHostKey: Key<EditorHost> = Key.create("com.jetbrains.reactiveidea.EditorHost")
    val tags = "@@@--^tags"

    public fun getHost(editor: Editor) : EditorHost? = editor.getUserData(editorHostKey)
  }

  val caretGuard = Guard()

  val documentHost = DocumentHost(lifetime, reactiveModel, path / "document", editor.getDocument(), editor.getProject(), providesMarkup, caretGuard)

  init {
    editor.putUserData(editorHostKey, this)
    reactiveModel.transaction { m -> (path / tags).putIn(m, ListModel(arrayListOf(PrimitiveModel("editor")))) }
    val selectionSignal = reactiveModel.subscribe(lifetime, path / "selection")
    val caretSignal = reactiveModel.subscribe(lifetime, path / "caret")


    val selectionReaction = reaction(true, "update selection/caret in editor from the model", selectionSignal, caretSignal, documentHost.documentUpdated) { selection, caret, _ ->

      selection as MapModel?
      caret as MapModel?

      if (!caretGuard.locked) {
        caretGuard.lock {
          CommandProcessor.getInstance().executeCommand(editor.getProject(), {
            if (caret != null) {
              editor.getCaretModel().moveToOffset((caret["offset"] as PrimitiveModel<Int>).value)
            }
            if (selection != null) {
              editor.getSelectionModel().setSelection(
                  (selection["startOffset"] as PrimitiveModel<Int>).value,
                  (selection["endOffset"] as PrimitiveModel<Int>).value)
            } else {
              editor.getSelectionModel().removeSelection()
            }
          }, "Update caret and selection", DocCommandGroupId.noneGroupId(editor.getDocument()), UndoConfirmationPolicy.DEFAULT, editor.getDocument())

        }
      }
      selection
    }

    sendSelectionAndCaret()

    val caretListener = object : CaretAdapter() {
      override fun caretPositionChanged(e: CaretEvent) {
        if (!caretGuard.locked) {
          caretGuard.lock {
            sendSelectionAndCaret()
          }
        }
      }
    }

    editor.getCaretModel().addCaretListener(caretListener)
    lifetime += {
      editor.getCaretModel().removeCaretListener(caretListener)
    }

    val selectionListener = object : SelectionListener {
      override fun selectionChanged(e: SelectionEvent) {
        if (!caretGuard.locked) {
          caretGuard.lock {
            sendSelectionAndCaret()
          }
        }
      }
    }

    editor.getSelectionModel().addSelectionListener(selectionListener)
    lifetime += {
      editor.getSelectionModel().removeSelectionListener(selectionListener)
    }
  }

  private fun sendSelectionAndCaret() {
    val textRange = TextRange(editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd())
    val caretOffset = editor.getCaretModel().getOffset()
    reactiveModel.transaction { m ->
      val m1 = (path / "selection").putIn(m,
          MapModel(hashMapOf(
              "startOffset" to PrimitiveModel(textRange.getStartOffset()),
              "endOffset" to PrimitiveModel(textRange.getEndOffset())
          )))
      (path / "caret" / "offset").putIn(m1, PrimitiveModel(caretOffset))

    }
  }
}
