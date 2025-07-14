// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui.proofreading.component.list

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.ide.ui.components.GrazieUIComponent
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.ide.ui.components.dsl.padding
import com.intellij.grazie.ide.ui.components.dsl.setEmptyTextPlaceholder
import com.intellij.grazie.ide.ui.components.utils.ConfigurableListCellRenderer
import com.intellij.grazie.ide.ui.components.utils.configure
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.utils.toSet
import com.intellij.openapi.actionSystem.ActionToolbarPosition
import com.intellij.ui.*
import com.intellij.ui.CommonActionsPanel.Buttons
import com.intellij.ui.components.JBList
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.util.ui.EditableModel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.ListCellRenderer

class GrazieLanguagesList(private val download: suspend (Collection<Lang>) -> Unit, private val onLanguageRemoved: (lang: Lang) -> Unit) :
  AddDeleteListPanel<Lang>(null, emptyList()), GrazieUIComponent {

  private val decorator: ToolbarDecorator = MyToolbarDecorator(myList)
    .setAddAction { findItemToAdd() }
    .setAddIcon(LayeredIcon.ADD_WITH_DROPDOWN)
    .setToolbarPosition(ActionToolbarPosition.BOTTOM)
    .setRemoveAction {
      myList.selectedValuesList.forEach(onLanguageRemoved)
      ListUtil.removeSelectedItems(myList)
    }

  init {
    layout = BorderLayout()
    add(decorator.createPanel(), BorderLayout.CENTER)

    emptyText.setEmptyTextPlaceholder(
      mainText = msg("grazie.settings.proofreading.languages.empty.text"),
      shortcutText = msg("grazie.settings.proofreading.languages.empty.action"),
      shortcutButton = Buttons.ADD,
      shortcutAction = { addElement(findItemToAdd()) }
    )
  }

  override fun initPanel() {}

  override fun getListCellRenderer(): ListCellRenderer<*> = ConfigurableListCellRenderer<Lang> { component, lang ->
    component.configure {
      border = padding(JBUI.insets(5))
      text = lang.nativeName
    }
  }

  override fun addElement(itemToAdd: Lang?) {
    itemToAdd ?: return
    removeExistedDialects(itemToAdd)
    val positionToInsert = -(myListModel.elements().toList().binarySearch(itemToAdd, Comparator.comparing(Lang::nativeName)) + 1)
    myListModel.add(positionToInsert, itemToAdd)
    myList.clearSelection()
    myList.setSelectedValue(itemToAdd, true)
  }

  override fun findItemToAdd(): Lang? {
    // remove already enabled languages and their dialects
    val (available, toDownload) = getLangsForPopup()

    val step = GrazieLanguagesPopupStep(msg("grazie.settings.proofreading.languages.popup.title"), available, toDownload, download,
                                        ::addElement)
    val menu = MyListPopup(step)

    decorator.actionsPanel?.getAnActionButton(Buttons.ADD)?.preferredPopupPoint?.let(menu::show)

    return null
  }

  /** Returns pair of (available languages, languages to download) */
  private fun getLangsForPopup(): Pair<List<Lang>, List<Lang>> {
    val enabledLangs = myListModel.elements().asSequence().map { it.nativeName }.toSet()
    val (available, toDownload) = Lang.sortedValues().filter { it.nativeName !in enabledLangs }.partition { it.isAvailable() }
    return available to toDownload
  }

  private fun removeExistedDialects(lang: Lang) {
    val dialectsToRemove = ArrayList<Lang>()
    for (existed in myListModel.elements()) {
      if (existed.iso == lang.iso) {
        dialectsToRemove.add(existed)
      }
    }

    for (toRemove in dialectsToRemove) {
      myListModel.removeElement(toRemove)
    }
  }

  private class MyListPopup(step: GrazieLanguagesPopupStep) : ListPopupImpl(null, step) {
    override fun getListElementRenderer() = GrazieLanguagesPopupElementRenderer(this)
  }

  private inner class MyToolbarDecorator(private val list: JBList<Lang>) : ToolbarDecorator() {
    init {
      myRemoveActionEnabled = true
      myAddActionEnabled = true

      list.configure {
        addListSelectionListener { updateButtons() }
        addPropertyChangeListener("enabled") { updateButtons() }
      }
    }

    override fun updateButtons() {
      val (available, download) = getLangsForPopup()
      actionsPanel.setEnabled(Buttons.ADD, list.isEnabled && (available.isNotEmpty() || download.isNotEmpty()))
      actionsPanel.setEnabled(Buttons.REMOVE, !list.isSelectionEmpty)
      updateExtraElementActions(!list.isSelectionEmpty)
    }

    override fun setVisibleRowCount(rowCount: Int): MyToolbarDecorator {
      list.visibleRowCount = rowCount
      return this
    }

    override fun getComponent() = list

    override fun installDnDSupport() = RowsDnDSupport.install(list, list.model as EditableModel)

    override fun isModelEditable() = true
  }

  override val component: JComponent
    get() = this

  override fun isModified(state: GrazieConfig.State): Boolean {
    return myListModel.elements().toSet() != state.enabledLanguages
  }

  override fun reset(state: GrazieConfig.State) {
    myListModel.clear()
    GrazieConfig.get().enabledLanguages.sortedBy { it.nativeName }.forEach {
      myListModel.addElement(it)
    }
  }

  override fun apply(state: GrazieConfig.State): GrazieConfig.State {
    return state.copy(enabledLanguages = myListModel.elements().toSet())
  }
}
