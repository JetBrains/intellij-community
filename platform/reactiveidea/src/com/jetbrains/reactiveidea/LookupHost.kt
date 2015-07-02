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

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.impl.LookupUi
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId
import com.jetbrains.reactivemodel.*
import com.jetbrains.reactivemodel.models.AbsentModel
import com.jetbrains.reactivemodel.models.ListModel
import com.jetbrains.reactivemodel.models.MapModel
import com.jetbrains.reactivemodel.models.PrimitiveModel
import com.jetbrains.reactivemodel.util.Lifetime

public class LookupHost(val reactiveModel: ReactiveModel, val editorPath: Path, val lookup : LookupImpl) : LookupUi {
  val tags = "@@@--^tags"
  val metadata: MutableMap<Path, LookupElement> = hashMapOf()
  val life = Lifetime.create(reactiveModel.lifetime)
  val path = Path("lookup")

  init {
    life.lifetime += {
      reactiveModel.transaction { m ->
        path.putIn(m, AbsentModel())
      }
      metadata.clear()
    }
    reactiveModel.registerHandler(life.lifetime, "insert-item") { args, model ->

      val item_path = args["item-path"] as ListModel
      val path = path(item_path)
      val lookupElement = metadata[path]
      if (lookupElement != null) {
        val editor = lookup.getEditor()
        CommandProcessor.getInstance().executeCommand(editor.getProject(), {
          lookup.finishLookup('\n', lookupElement)
        }, "Insert lookup string",
            DocCommandGroupId.noneGroupId(editor.getDocument()),
            UndoConfirmationPolicy.DEFAULT,
            editor.getDocument())
      }
      model
    }
  }

  private fun path(item_path: ListModel) = item_path.list.fold(Path()) { p, m -> p / (m as PrimitiveModel<*>).value }

  override fun setCalculating(calculating: Boolean) {

  }

  override fun refreshUi(selectionVisible: Boolean, itemsChanged: Boolean, reused: Boolean, onExplicitAction: Boolean) {
    writeData()
  }

  override fun isPositionedAboveCaret(): Boolean = false

  override fun lookupDisposed() {
    life.terminate()
  }

  override fun show() {
    writeData()
  }

  private fun writeData() {
    reactiveModel.transaction { m ->
      path.putIn(m, MapModel(hashMapOf(
          tags to ListModel(arrayListOf(PrimitiveModel("lookup"))),
          "editor" to editorPath.toList(),
          "items" to MapModel(marshalItems(path))
      )))
    }
  }

  private fun marshalItems(path: Path): Map<String, Model> {
    val listModel = lookup.getList().getModel()
    val result: MutableMap<String, Model> = hashMapOf()
    for (i in (0..listModel.getSize()-1)) {
      val el = listModel.getElementAt(i) as LookupElement
      val presentation = LookupElementPresentation()
      el.renderElement(presentation)
      result.put("$i", marshalItem(presentation))
      metadata[path / "items" / "$i"] = el
    }
    return result
  }

  private fun marshalItem(presentation: LookupElementPresentation): Model {
    return MapModel(hashMapOf(
        "itemText" to primitiveModel(presentation.getItemText()),
        "itemForeground" to toColorModel(presentation.getItemTextForeground()),
        "tailText" to primitiveModel(presentation.getTailText()),
        "typeText" to primitiveModel(presentation.getTypeText()),
        "tailForeground" to toColorModel(presentation.getTailForeground())
    ))
  }

  private fun primitiveModel(s: String?): Model = if (s == null) AbsentModel() else PrimitiveModel(s)

  override fun updateLocation() {

  }

  override fun isAvailableToUser(): Boolean = true

}