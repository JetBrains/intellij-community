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
import com.intellij.openapi.command.CommandAdapter
import com.intellij.openapi.command.CommandEvent
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId
import com.intellij.openapi.editor.event.DocumentAdapter
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.jetbrains.reactivemodel.*
import com.jetbrains.reactivemodel.models.ListModel
import com.jetbrains.reactivemodel.models.MapModel
import com.jetbrains.reactivemodel.models.PrimitiveModel
import com.jetbrains.reactivemodel.util.Guard

public class DocumentHost(reactModel: ReactiveModel, path: Path, val doc: Document, project: Project?,
                          providesMarkup: Boolean, caretGuard: Guard) : MetaHost(reactModel, path) {
  private val TIMESTAMP: Key<Int> = Key("com.jetbrains.reactiveidea.timestamp")
  private val recursionGuard = Guard()

  val documentUpdated: Signal<Any?>

  private val pendingEvents = arrayListOf<DocumentEvent>()


  init {
    initModel()
    val listener = object : DocumentAdapter() {
      override fun documentChanged(e: DocumentEvent) {
        if(recursionGuard.locked) {
          return
        }
        pendingEvents.add(e)
      }
    }

    val eventListenerCommandName = "doc sync " + reactModel.name

    val commandListener = object : CommandAdapter() {
      override fun commandFinished(event: CommandEvent?) {
        if (event?.getCommandName() != eventListenerCommandName) {
          val transaction: (MapModel) -> MapModel = { m ->
            var result = m
            for (e in pendingEvents) {
              result = (path / "events" / Last).putIn(result, documentEvent(e))
            }
            var i = doc.getUserData(TIMESTAMP)
            if (i == null) i = 0
            doc.putUserData(TIMESTAMP, i + pendingEvents.size())
            result
          }
          reactiveModel.transaction(transaction)
        } else {
          pendingEvents.clear()
        }
      }
    }

    CommandProcessor.getInstance().addCommandListener(commandListener)
    lifetime += {
      CommandProcessor.getInstance().removeCommandListener(commandListener)
    }

    val textSignal = reaction(true, "document text", reactModel.subscribe(lifetime, path / "text")) { model ->
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

    reactModel.transaction { m ->
      var result = (path / "text").putIn(m, PrimitiveModel(doc.getText()))
      result = (path / "events").putIn(result, ListModel())

      result
    }

    val listenToDocumentEvents = reaction(true, "listen to model events", reactModel.subscribe(lifetime, (path / "events"))) { model ->
      val evts = model as ListModel?

      if (evts != null) {
        var timestamp = doc.getUserData(TIMESTAMP)
        if (timestamp == null) {
          timestamp = 0
        }
        val size = evts.size()
        doc.putUserData(TIMESTAMP, size)

        ApplicationManager.getApplication().runWriteAction {
          CommandProcessor.getInstance().executeCommand(project, {
            caretGuard.lock {
              if (!recursionGuard.locked) {
                recursionGuard.lock {
                  for (i in (timestamp..size - 1)) {
                    val eventModel = evts[i]
                    play(eventModel, doc)
                  }
                }
              }
            }
          }, eventListenerCommandName, DocCommandGroupId.noneGroupId(doc))
        }

      }
      evts
    }

    documentUpdated = reaction(true, "document updated", listenToDocumentEvents, updateDocumentText) { _, __ -> _ to __ }

    val documentMarkup = DocumentMarkupModel.forDocument(doc, project, true) as MarkupModelEx
    if (providesMarkup) {
      ServerMarkupHost(documentMarkup, reactModel, path / "markup")
    } else {
      ClientMarkupHost(
          documentMarkup,
          reactModel,
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
