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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentAdapter
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.ColorUtil
import com.jetbrains.reactivemodel.*
import com.jetbrains.reactivemodel.models.*
import com.jetbrains.reactivemodel.signals.Signal
import com.jetbrains.reactivemodel.signals.reaction
import com.jetbrains.reactivemodel.util.Guard
import com.jetbrains.reactivemodel.util.Lifetime
import java.awt.Color
import java.util.HashMap

public class DocumentHost(val lifetime: Lifetime, val reactiveModel: ReactiveModel, val path: Path, val doc: Document, project: Project?, providesMarkup: Boolean, caretGuard: Guard) {
  private val TIMESTAMP: Key<Int> = Key("com.jetbrains.reactiveidea.timestamp")
  private val recursionGuard = Guard()

  val markupHost: Any

  public val documentUpdated: Signal<Any?>

  init {
    val listener = object : DocumentAdapter() {
      override fun documentChanged(e: DocumentEvent) {
        if (!recursionGuard.locked) {
          recursionGuard.lock {
            val transaction: (MapModel) -> MapModel = { m ->
              if (e.isWholeTextReplaced()) {
                (path / "text").putIn(m, PrimitiveModel(doc.getText()))
              } else {
                val result = (path / "events" / Last).putIn(m, documentEvent(e))
                val events = (path / "events").getIn(result) as ListModel
                doc.putUserData(TIMESTAMP, events.size())
                result
              }
            }
            reactiveModel.transaction(transaction)
          }
        }
      }
    }

    val textSignal = reaction(true, "document text", reactiveModel.subscribe(lifetime, path / "text")) { model ->
      if (model == null) null
      else (model as PrimitiveModel<String>).value
    }

    val updateDocumentText = reaction(true, "init document text", textSignal) { text ->
      if (text != null) {
        ApplicationManager.getApplication().runWriteAction {
          doc.setText(text)
        }
      }
      text
    }

    doc.addDocumentListener(listener)
    lifetime += {
      doc.removeDocumentListener(listener)
    }

    reactiveModel.transaction { m ->
      var result = (path / "text").putIn(m, PrimitiveModel(doc.getText()))
      result = (path / "events").putIn(result, ListModel())

      result
    }

    val eventsList = reaction(true, "cast events to ListModel", reactiveModel.subscribe(lifetime, (path / "events"))) {
      if (it != null) it as ListModel
      else null
    }

    val listenToDocumentEvents = reaction(true, "listen to model events", eventsList) { evts ->
      if (evts != null) {
        var timestamp = doc.getUserData(TIMESTAMP)
        if (timestamp == null) {
          timestamp = 0
        }
        doc.putUserData(TIMESTAMP, evts.size())

        ApplicationManager.getApplication().runWriteAction {
          CommandProcessor.getInstance().executeCommand(null, {
            caretGuard.lock {
              if (!recursionGuard.locked) {
                recursionGuard.lock {
                  for (i in (timestamp..evts.size() - 1)) {
                    val eventModel = evts[i]
                    play(eventModel, doc)
                  }
                }
              }
            }
          }, null, null)
        }

      }
      evts
    }

    documentUpdated = reaction(true, "document updated", listenToDocumentEvents, updateDocumentText) { _, __ -> _ to __ }

    val documentMarkup = DocumentMarkupModel.forDocument(doc, project, true) as MarkupModelEx
    markupHost =
        if (providesMarkup) {
          ServerMarkupHost(
              documentMarkup,
              reactiveModel,
              path / "markup",
              lifetime)
        } else {
          ClientMarkupHost(
              documentMarkup,
              reactiveModel,
              path / "markup",
              lifetime,
              documentUpdated)
        }
  }
}

private fun play(event: Model, doc: Document) {
  if (event !is MapModel) {
    throw AssertionError()
  }

  val offset = (event["offset"] as PrimitiveModel<Int>).value
  val len = (event["len"] as PrimitiveModel<Int>).value
  val text = (event["text"] as PrimitiveModel<String>).value

  doc.replaceString(offset, offset + len, text)
}

fun documentEvent(e: DocumentEvent): Model = MapModel(hashMapOf(
    "offset" to PrimitiveModel(e.getOffset()),
    "len" to PrimitiveModel(e.getOldFragment().length()),
    "text" to PrimitiveModel(e.getNewFragment().toString())
))
