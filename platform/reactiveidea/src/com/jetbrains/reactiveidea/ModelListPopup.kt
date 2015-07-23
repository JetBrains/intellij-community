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

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.popup.list.ListPopupImpl
import com.jetbrains.reactivemodel.*
import com.jetbrains.reactivemodel.models.AbsentModel
import com.jetbrains.reactivemodel.models.MapModel
import com.jetbrains.reactivemodel.models.PrimitiveModel
import com.jetbrains.reactivemodel.util.Lifetime
import java.awt.Component
import java.awt.Point
import javax.swing.JLabel
import javax.swing.ListCellRenderer

public open class ModelListPopup(val aStep: ListPopupStep<Any>,
                                 val reactiveModel: ReactiveModel,
                                 val path: Path) : ListPopupImpl(aStep){
  var selectedIndex: Signal<Int>? = null;

  override fun createList(): ListPopupImpl.MyList {
    return object : ListPopupImpl.MyList(myListModel) {

      //fireSelectionValueChanged

      override fun getSelectedValues(): Array<Any> {
        return arrayOf(getSelectedValue())
      }

      override fun getSelectedIndex(): Int {
        return selectedIndex!!.value
      }

      override fun getSelectedValue(): Any {
        return (myStep as ListPopupStep<Any>).getValues().get(getSelectedIndex())
      }

      override fun setSelectedIndex(index: Int) {
        if (reactiveModel == null) return;
        reactiveModel.transaction {
          it.putIn(path / "selectedIndex", PrimitiveModel(index))
        }
      }

      override fun setSelectedValue(anObject: Any?, shouldScroll: Boolean) {
        super.setSelectedValue(anObject, shouldScroll)
      }
    }
  }

  init {
    clearPopupModel()
  }

  override fun dispose() {
    clearPopupModel()
    super.dispose()
  }

  private fun clearPopupModel() {
    reactiveModel.transaction { it.putIn(path, AbsentModel()) }
  }

  override fun showUnderneathOfLabel(label: JLabel) {
  }

  override fun showInBestPositionFor(editor: Editor) {
    val editorHost = editor.getUserData(EditorHost.editorHostKey)
    assert(editorHost != null)
    val editorPath = editorHost!!.path
    val context = MapModel(hashMapOf("editor" to editorPath.toList()))
    show(context)
  }

  inner class ListModelHost(path: Path, lifetime: Lifetime, initializer: Initializer, context: Model?): Host {
    init {
      val listStep = getListStep()
      val title = listStep.getTitle()
      val values: Map<String, MapModel> = (0..listStep.getValues().size() - 1)
          .map { it.toString() }
          .zip(listStep.getValues())
          .toMap().mapValues { MapModel(hashMapOf("text" to PrimitiveModel(listStep.getTextFor(it.getValue())))) }


      initializer += {
        it.putIn(path / "list", MapModel(values))
      }

      if (title != null) {
        initializer += {
          it.putIn(path / "title", PrimitiveModel(title))
        }
      }

      if (context != null) {
        initializer += {
          it.putIn(path / "context", context)
        }
      }


      lifetime += {
        dispose()
      }

      val selection = reactiveModel.subscribe(lifetime, path / "selectedIndex")
      val actionPerformed = reactiveModel.subscribe(lifetime, path / "action")

      selectedIndex = reaction(true, "selectedIndex", selection) {
        (it as? PrimitiveModel<Int>)?.value ?: 0
      }

      reaction(false, "perform action in list popup", actionPerformed, selectedIndex!!) { action, index ->
        action as MapModel
        val finalChoice = (action["finalChoice"] as PrimitiveModel<Boolean>).value
        handleSelect(finalChoice)
        clearPopupModel()
      };
    }
  }

  private fun show(context: Model?) {

    reactiveModel.host(path) { path, lifetime, initializer ->
      ListModelHost(path, lifetime, initializer, context)
    }
  }

  override fun show(owner: Component, aScreenX: Int, aScreenY: Int, considerForcedXY: Boolean) {
    println("ModelListPopup.show")
    println("owner = [" + owner + "], aScreenX = [" + aScreenX + "], aScreenY = [" + aScreenY + "], considerForcedXY = [" + considerForcedXY + "]")
  }

  override fun showInCenterOf(aContainer: Component) {
    println("ModelListPopup.showInCenterOf")
    println("aContainer = [" + aContainer + "]")
  }

  override fun showCenteredInCurrentWindow(project: Project) {
    println("ModelListPopup.showCenteredInCurrentWindow")
    println("project = [" + project + "]")
  }

  override fun showUnderneathOf(aComponent: Component) {
  }

  override fun show(aPoint: RelativePoint) {
  }

  override fun showInScreenCoordinates(owner: Component, point: Point) {
  }

  override fun showInBestPositionFor(dataContext: DataContext) {
    val editor = CommonDataKeys.EDITOR.getData(dataContext)
    showInBestPositionFor(editor)
  }

  override fun showInFocusCenter() {
  }

  override fun show(owner: Component) {
  }
}

