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
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.command.CommandAdapter
import com.intellij.openapi.command.CommandEvent
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
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
import com.jetbrains.reactivemodel.util.Lifetime

public class EditorHost(val reactiveModel: ReactiveModel,
                        val path: Path,
                        val lifetime: Lifetime,
                        val file: VirtualFile,
                        val textEditor: TextEditor,
                        init: Initializer) : Host, DataProvider {

  override val tags: Array<String>
    get() = arrayOf("editor")

  public val editor: Editor = textEditor.getEditor()
  private val caretGuard = Guard()
  public val name: String = file.getName()
  private val updateCaretAndSelectionCommand = "UpdateCaretAndSelection$name"


  override fun getData(dataId: String?): Any? {
    if (PlatformDataKeys.FILE_EDITOR.`is`(dataId)) {
      return textEditor
    }

    val data = (editor.getContentComponent() as EditorComponentImpl).getData(dataId)
    if (data != null) {
      return data;
    }

    val project = editor.getProject()
    if (dataId != null && project != null) {
      val dat = FileEditorManager.getInstance(project).getData(dataId, editor, editor.getCaretModel().getPrimaryCaret())
      if (dat != null) {
        return dat
      }
    }

    if (CommonDataKeys.EDITOR.`is`(dataId)) {
      return editor
    }

    if (CommonDataKeys.PROJECT.`is`(dataId)) {
      return project
    }
    return null
  }

  companion object {
    val editorHostKey: Key<EditorHost> = Key.create("com.jetbrains.reactiveidea.EditorHost")
    val activePath = "active"
    public fun getHost(editor: Editor): EditorHost? = editor.getUserData(editorHostKey)
  }

  init {

    editor.putUserData(editorHostKey, this)
    editor.putUserData(pathKey, path)

    init += {
      var editorsModel: List<Model?> = (path.dropLast(1).getIn(it) as? MapModel)
          ?.values()
          ?.filter { (it as MapModel).isNotEmpty() } ?: emptyList()
      it.putIn(path / activePath, PrimitiveModel(editorsModel.isEmpty()))
    }

    lifetime += {
      val project = editor.getProject()
      if (project != null) {
        val manager = FileEditorManager.getInstance(project);
        manager.closeFile(file)
      }
    }

    val documentHost = reactiveModel.host(path / "document") { path, lifetime, init ->
      DocumentHost(reactiveModel, path, lifetime, editor.getDocument(), editor.getProject(), caretGuard, init)
    }

    val selectionSignal = reactiveModel.subscribe(lifetime, path / "selection")
    val caretSignal = reactiveModel.subscribe(lifetime, path / "caret")

    val caretModel = editor.getCaretModel()
    val selectionReaction = reaction(true, "update selection/caret in editor from the model", selectionSignal, caretSignal, documentHost.documentUpdated!!) { selection, caret, _ ->

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
            IdeDocumentHistory.getInstance(editor.getProject()).onSelectionChanged()
          }, updateCaretAndSelectionCommand, DocCommandGroupId.noneGroupId(editor.getDocument()), UndoConfirmationPolicy.DEFAULT, editor.getDocument())
        }
      }
      selection
    }

    init += {
      writeSelectionAndCaret(it)
    }

    val commandListener = object : CommandAdapter() {
      override fun commandFinished(event: CommandEvent?) {
        if (event?.getCommandName() != updateCaretAndSelectionCommand &&
            event?.getCommandName() != documentHost.eventListenerCommandName) {
          reactiveModel.transaction { writeSelectionAndCaret(it) }
        }
      }
    }

    CommandProcessor.getInstance().addCommandListener(commandListener)
    lifetime += {
      CommandProcessor.getInstance().removeCommandListener(commandListener)
    }

    reactiveModel.host(path / "markup") { path, lifetime, init ->
      MarkupHost(editor.getMarkupModel() as MarkupModelEx, reactiveModel, path, lifetime, init)
    }

    val disposable = Disposer.newDisposable()
    TemplateManager.getInstance(editor.getProject()).addTemplateManagerListener(disposable, object : TemplateManagerListener {
      override fun templateStarted(state: TemplateState) {
        if (state.getEditor() == editor) {
          reactiveModel.transaction { m -> m.putIn(path / "live-template", PrimitiveModel(true)) }
          val listener = object : CaretListener {
            override fun caretAdded(e: CaretEvent?) {
              throw UnsupportedOperationException()
            }

            override fun caretPositionChanged(e: CaretEvent?) {
              if (!caretGuard.locked) caretGuard.lock {
                reactiveModel.transaction { writeSelectionAndCaret(it) }
              }
            }

            override fun caretRemoved(e: CaretEvent?) {
              throw UnsupportedOperationException()
            }
          }
          editor.getCaretModel().addCaretListener(listener)
          state.addTemplateStateListener(object : TemplateEditingAdapter() {
            override fun templateFinished(template: Template?, brokenOff: Boolean) {
              finished();
            }

            override fun templateCancelled(template: Template?) {
              finished()
            }

            private fun finished() {
              reactiveModel.transaction { it.putIn(path / "live-template", AbsentModel()) }
              editor.getCaretModel().removeCaretListener(listener)
            }
          })
        }
      }
    })
    lifetime += {
      Disposer.dispose(disposable)
    }

    init += {
      it.putIn(path / "name", PrimitiveModel(name))
    }
  }

  private fun writeSelectionAndCaret(m: MapModel): MapModel {
    val textRange = TextRange(editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd())
    val caretOffset = editor.getCaretModel().getOffset()
    return m
        .putIn(path / "selection",
            MapModel(hashMapOf(
                "startOffset" to PrimitiveModel(textRange.getStartOffset()),
                "endOffset" to PrimitiveModel(textRange.getEndOffset())
            )))
        .putIn(path / "caret" / "offset", PrimitiveModel(caretOffset))
  }
}
