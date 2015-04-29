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

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretAdapter
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.ColorUtil
import com.jetbrains.reactivemodel.*
import com.jetbrains.reactivemodel.models.AbsentModel
import com.jetbrains.reactivemodel.models.MapDiff
import com.jetbrains.reactivemodel.models.MapModel
import com.jetbrains.reactivemodel.models.PrimitiveModel
import com.jetbrains.reactivemodel.signals.reaction
import com.jetbrains.reactivemodel.util.Guard
import com.jetbrains.reactivemodel.util.Lifetime
import java.awt.Color
import java.util.HashMap

public class EditorHost(val lifetime: Lifetime, val reactiveModel: ReactiveModel, val path: Path, val editor: Editor, val providesMarkup: Boolean) {

  val documentHost = DocumentHost(lifetime, reactiveModel, path / "document", editor.getDocument(), editor.getProject(), providesMarkup)

  val caretGuard = Guard()

  init {
    val selectionSignal = reactiveModel.subscribe(lifetime, path / "selection")
    val caretSignal = reactiveModel.subscribe(lifetime, path / "caret")

    val caretReaction = reaction(true, "update caret in editor from the model", caretSignal, documentHost.documentUpdated) {
      caret, _ ->
      if (caret != null) {
        caret as MapModel
        if (!caretGuard.locked) {
          caretGuard.lock {
            editor.getCaretModel().moveToOffset((caret["offset"] as PrimitiveModel<Int>).value)
          }
        }
      }
      caret
    }

    val selectionReaction = reaction(true, "update selection in editor from the model", selectionSignal, documentHost.documentUpdated, caretReaction) {
      selection, _, __ ->

      selection as MapModel?

      if (selection != null) {
        editor.getSelectionModel().setSelection(
            (selection["startOffset"] as PrimitiveModel<Int>).value,
            (selection["endOffset"] as PrimitiveModel<Int>).value)
      }
      selection
    }

    reactiveModel.transaction { m ->
      (path / "caret" / "offset").putIn(m, PrimitiveModel(editor.getCaretModel().getOffset()))
    }

    val caretListener = object : CaretAdapter() {
      override fun caretPositionChanged(e: CaretEvent) {
        if (!caretGuard.locked) {
          caretGuard.lock {
            val newOffset = editor.logicalPositionToOffset(e.getNewPosition())
            reactiveModel.transaction { m ->
              (path / "caret" / "offset").putIn(m, PrimitiveModel(newOffset))
            }
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
        val textRange = e.getNewRange()
        reactiveModel.transaction { m ->
          (path / "selection").putIn(m, MapModel(hashMapOf(
              "startOffset" to PrimitiveModel(textRange.getStartOffset()),
              "endOffset" to PrimitiveModel(textRange.getEndOffset())
          )))
        }
      }
    }

    editor.getSelectionModel().addSelectionListener(selectionListener)
    lifetime += {
      editor.getSelectionModel().removeSelectionListener(selectionListener)
    }
  }
}
