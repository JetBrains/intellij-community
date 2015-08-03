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
package com.jetbrains.reactiveidea.navigation

import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.ide.actions.ChooseByNameFactory
import com.intellij.ide.actions.ChooseByNameItemProvider
import com.intellij.ide.util.gotoByName.ChooseByNameModel
import com.intellij.ide.util.gotoByName.ChooseByNamePopupComponent
import com.intellij.ide.util.gotoByName.ChooseByNameViewModel
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.statistics.StatisticsInfo
import com.intellij.psi.statistics.StatisticsManager
import com.jetbrains.reactivemodel.*
import com.jetbrains.reactivemodel.models.AbsentModel
import com.jetbrains.reactivemodel.models.ListModel
import com.jetbrains.reactivemodel.models.PrimitiveModel
import java.util.ArrayList
import javax.swing.JComponent
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

public class ModelChooseByNameFactory(val project: Project) : ChooseByNameFactory() {
  override fun createChooseByName(model: ChooseByNameModel?,
                                  itemProvider: ChooseByNameItemProvider?,
                                  mayRequestOpenInCurrentWindow: Boolean,
                                  start: Pair<String, Int>?): ChooseByNameViewModel? =
      SearchByName(project,
          model!!,
          itemProvider!!,
          start!!.first!!,
          start.second!!,
          ReactiveModel.current()!!)
}

public class SearchByName(val project: Project,
                          model: ChooseByNameModel,
                          provider: ChooseByNameItemProvider,
                          val initialText: String,
                          val initialIndex: Int,
                          val reactiveModel: ReactiveModel) : ChooseByNameViewModel(project, model, provider, initialText, initialIndex), Host {

  private var textSignal: Signal<String?>? = null
  private var checkSignal: Signal<Boolean?>? = null
  private var indexSignal: Signal<Int?>? = null
  private val path = Path("goto")

  override fun invoke(callback: ChooseByNamePopupComponent.Callback?, modalityState: ModalityState?, allowMultipleSelection: Boolean) {
    myActionListener = callback
    reactiveModel.host(path) { path, lifetime, init ->
      init += {
        it.putIn(path / "text", PrimitiveModel(initialText))
            .putIn(path / "index", PrimitiveModel(initialIndex))
            .putIn(path / "check", PrimitiveModel(myModel.loadInitialCheckBoxState()))
      }

      val checkBoxName = myModel.getCheckBoxName()
      if (checkBoxName != null) {
        init += {
          it.putIn(path / "checkBoxName", PrimitiveModel(checkBoxName))
        }
      }

      val promptText = myModel.getPromptText()
      if (promptText != null) {
        init += {
          it.putIn(path / "promptText", PrimitiveModel(promptText))
        }
      }

      checkSignal = reaction(false, "convert check to bool", reactiveModel.subscribe(lifetime, path / "check")) {
        (it as? PrimitiveModel<Boolean>?)?.value
      }

      textSignal = reaction(false, "convert text to string", reactiveModel.subscribe(lifetime, path / "text")) {
        (it as? PrimitiveModel<String>?)?.value
      }

      indexSignal = reaction(false, "convert index to int", reactiveModel.subscribe(lifetime, path / "selectedIndex")) {
        (it as? PrimitiveModel<Int>?)?.value
      }

      reaction(false, "go to checkbox", checkSignal!!) {
        rebuildList(false)
      }

      reaction(false, "go to text", textSignal!!) {
        clearPostponedOkAction(false)
        rebuildList(false)
      }

      var updateScheduled = false


      fun renderElement(i: Int, e: Any?): Model {
        if (e is PsiNamedElement) {
          return PrimitiveModel(e.getName())
        }
        return PrimitiveModel(e.toString())
      }

      fun renderList(l: List<Any?>): ListModel =
          ListModel(l.mapIndexed { i, e -> renderElement(i, e) })

      fun renderList() {
        if (updateScheduled) return
        else {
          SwingUtilities.invokeLater {
            updateScheduled = false
            val list = ArrayList<Any?>()
            for (i in 0..myListModel.getSize() - 1) {
              list.add(myListModel.getElementAt(i))
            }
            reactiveModel.transaction { it.putIn(path / "list", renderList(list)) }
          }
        }
      }

      myListModel.addListDataListener(object : ListDataListener {
        override fun contentsChanged(e: ListDataEvent) {
          renderList()
        }

        override fun intervalRemoved(e: ListDataEvent) {
          renderList()
        }

        override fun intervalAdded(e: ListDataEvent) {
          renderList()
        }
      })

      lifetime += {
        checkSignal = null
        indexSignal = null
        textSignal = null
        cancelListUpdater()
      }

      this
    }
    myInitialized = true
    if (modalityState != null) {
      rebuildList(myInitialIndex, 0, modalityState, null)
    }
  }

  override val tags: Array<String>
    get() = arrayOf("goto")

  override fun getEnteredText(): String? = textSignal?.value ?: ""

  override fun getSelectedIndex(): Int = indexSignal?.value ?: 0

  override fun showCardImpl(card: String?) {
    if (card == null) {
      reactiveModel.transaction { it.putIn(path / "card", AbsentModel()) }
    } else {
      reactiveModel.transaction { it.putIn(path / "card", PrimitiveModel(card)) }
    }
  }

  override fun setCheckBoxShortcut(shortcutSet: ShortcutSet?) {

  }

  override fun hideHint() {

  }

  override fun doHideHint() {

  }

  override fun updateDocumentation() {

  }

  override fun isCheckboxVisible(): Boolean = myModel.getCheckBoxName() != null


  override fun isShowListForEmptyPattern(): Boolean = true

  override fun isCloseByFocusLost(): Boolean = false

  override fun getModalityStateForTextBox(): ModalityState = ModalityState.defaultModalityState()

  override fun isCheckboxSelected(): Boolean = checkSignal?.value ?: false

  override fun configureListRenderer() {

  }

  override fun setHasResults(b: Boolean) {
    reactiveModel.transaction { it.putIn(path / "hasResults", PrimitiveModel(b)) }
  }

  override fun showList() {

  }

  override fun hideList() {

  }

  public override fun close(isOk: Boolean) {
    super<ChooseByNameViewModel>.close(isOk)
    reactiveModel.transaction { it.putIn(path, AbsentModel()) }
  }

  override fun selectItem(selectionPos: Int) {
    reactiveModel.transaction { it.putIn(path / "index", PrimitiveModel(selectionPos)) }
  }

  override fun repositionHint() {

  }

  override fun updateVisibleRowCount() {

  }

  override fun getChosenElements(): MutableList<Any>? {
    return arrayListOf(myListModel.get(getSelectedIndex()))
  }

  override fun getChosenElement(): Any? {
    return getChosenElements()!![0]
  }

  override fun handlePaste(str: String?) {
    reactiveModel.transaction { it.putIn(path / "text", PrimitiveModel(str ?: "")) }
  }

  override fun lastKeyStrokeIsCompletion(): Boolean = false

  override fun setToolArea(toolArea: JComponent?) {

  }

  override fun repaintList() {

  }

  override fun getTextField(): JTextField? = null

}