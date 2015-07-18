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

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateEditingAdapter
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.TemplateManagerListener
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId
import com.intellij.openapi.editor.event.CaretAdapter
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.editor.impl.CaretModelImpl
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.reactivemodel.*
import com.jetbrains.reactivemodel.models.AbsentModel
import com.jetbrains.reactivemodel.models.ListModel
import com.jetbrains.reactivemodel.models.MapModel
import com.jetbrains.reactivemodel.models.PrimitiveModel
import com.jetbrains.reactivemodel.util.Guard

public class EditorHost(reactiveModel: ReactiveModel, path: Path, val file: VirtualFile,
                        val editor: Editor, val providesMarkup: Boolean) : MetaHost(reactiveModel, path), DataProvider {

  override fun getData(dataId: String?): Any? {
    val data = (editor.getContentComponent() as EditorComponentImpl).getData(dataId)
    if (data != null) {
      return data;
    }

    val project = editor.getProject()
    if (dataId != null && project != null) {
      return FileEditorManager.getInstance(project).getData(dataId, editor, editor.getCaretModel().getPrimaryCaret())
    }

    if (CommonDataKeys.EDITOR.`is`(dataId)) {
      return editor
    }
    return null
  }

  companion object {
    val editorHostKey: Key<EditorHost> = Key.create("com.jetbrains.reactiveidea.EditorHost")
    val activePath = "active"

    public fun getHost(editor: Editor): EditorHost? = editor.getUserData(editorHostKey)
  }

  val caretGuard = Guard()
  val name = file.getName()

  override fun buildMeta(): Map<String, Any> = super<MetaHost>.buildMeta()
      .plus("editor" to editor)
      .plus("file" to file)

  init {
    initModel { m ->
      var editorsModel: List<Model?> = (path.dropLast(1).getIn(m) as? MapModel)
          ?.values()
          ?.filter { (it as MapModel).isNotEmpty() } ?: emptyList()
      var model = (path / activePath).putIn(m, PrimitiveModel(editorsModel.isEmpty()))
      model = (path / tagsField).putIn(model, ListModel(arrayListOf(PrimitiveModel("editor"))))
      model
    }
    lifetime += {
      val project = editor.getProject()
      if (project != null) {
        val manager = FileEditorManager.getInstance(project);
        manager.closeFile(file)
      }
    }
    val documentHost = DocumentHost(reactiveModel, path / "document", editor.getDocument(), editor.getProject(), providesMarkup, caretGuard)
    editor.putUserData(editorHostKey, this)
    editor.putUserData(pathKey, path)
    val selectionSignal = reactiveModel.subscribe(lifetime, path / "selection")
    val caretSignal = reactiveModel.subscribe(lifetime, path / "caret")


    val caretModel = editor.getCaretModel()
    val selectionReaction = reaction(true, "update selection/caret in editor from the model", selectionSignal, caretSignal, documentHost.documentUpdated) { selection, caret, _ ->

      selection as MapModel?
      caret as MapModel?

      if (!caretGuard.locked) {
        caretGuard.lock {
          CommandProcessor.getInstance().executeCommand(editor.getProject(), {
            try {
              if (caret != null) {
                caretModel.moveToOffset((caret["offset"] as PrimitiveModel<Int>).value)
              }
              if (selection != null) {
                editor.getSelectionModel().setSelection(
                    (selection["startOffset"] as PrimitiveModel<Int>).value,
                    (selection["endOffset"] as PrimitiveModel<Int>).value)
              } else {
                editor.getSelectionModel().removeSelection()
              }
            } catch(e: Throwable) {
            }
            (IdeDocumentHistory.getInstance(editor.getProject()) as IdeDocumentHistoryImpl).onSelectionChanged()
          }, "Update caret and selection", DocCommandGroupId.noneGroupId(editor.getDocument()), UndoConfirmationPolicy.DEFAULT, editor.getDocument())
        }
      }
      selection
    }

    sendSelectionAndCaret()

    reactiveModel.transaction { m ->
      (path / "name").putIn(m, PrimitiveModel(name))
    }

    val caretListener = object : CaretAdapter() {
      override fun caretPositionChanged(e: CaretEvent) {
        if (!caretGuard.locked && !(caretModel as CaretModelImpl).isDocumentChanged()) {
          caretGuard.lock {
            sendSelectionAndCaret()
          }
        }
      }
    }

    caretModel.addCaretListener(caretListener)
    lifetime += {
      caretModel.removeCaretListener(caretListener)
    }

    val selectionListener = object : SelectionListener {
      override fun selectionChanged(e: SelectionEvent) {
        if (!caretGuard.locked && !(caretModel as CaretModelImpl).isDocumentChanged()) {
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

    val disposable = Disposer.newDisposable()
    TemplateManager.getInstance(editor.getProject()).addTemplateManagerListener(disposable, object: TemplateManagerListener {
      override fun templateStarted(state: TemplateState) {
        if (state.getEditor() == editor) {
          reactiveModel.transaction { m -> (path / "live-template").putIn(m, PrimitiveModel(true))}
          state.addTemplateStateListener(object: TemplateEditingAdapter() {
            override fun templateFinished(template: Template?, brokenOff: Boolean) {
              finished();
            }

            override fun templateCancelled(template: Template?) {
              finished()
            }

            private fun finished() {
              reactiveModel.transaction { m -> (path / "live-template").putIn(m, AbsentModel()) }
            }
          })
        }
      }
    })
    lifetime += {
      Disposer.dispose(disposable)
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
