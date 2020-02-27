// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui.components.langlist

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.ide.ui.components.dsl.padding
import com.intellij.grazie.jlanguage.Lang
import com.intellij.openapi.actionSystem.ActionToolbarPosition
import com.intellij.ui.*
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.util.ui.EditableModel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.*

class GrazieAddDeleteListPanel(private val download: (Lang) -> Boolean, private val onLanguageAdded: (lang: Lang) -> Unit,
                               private val onLanguageRemoved: (lang: Lang) -> Unit) :
  AddDeleteListPanel<Lang>(null, GrazieConfig.get().enabledLanguages.sortedWith(Comparator.comparing(Lang::displayName))) {
  private val decorator: ToolbarDecorator =
    @Suppress("UNCHECKED_CAST")
    GrazieListToolbarDecorator(myList as JList<Any>)
      .setAddAction { addElement(findItemToAdd()) }
      .setToolbarPosition(ActionToolbarPosition.BOTTOM)
      .setRemoveAction {
        myList.selectedValuesList.forEach(onLanguageRemoved)
        ListUtil.removeSelectedItems(myList as JList<Lang>)
      }

  init {
    emptyText.text = msg("grazie.ui.settings.language.empty.text")
    layout = BorderLayout()
    add(decorator.createPanel(), BorderLayout.CENTER)
  }

  override fun initPanel() {}

  override fun getListCellRenderer(): ListCellRenderer<*> = object : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(list: JList<*>?,
                                              value: Any?,
                                              index: Int,
                                              isSelected: Boolean,
                                              cellHasFocus: Boolean): Component {
      val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JComponent
      component.border = padding(JBUI.insets(5))
      return component
    }
  }

  override fun addElement(itemToAdd: Lang?) {
    if (itemToAdd != null) {
      val position = -(myListModel.elements().toList().binarySearch(itemToAdd, Comparator.comparing(Lang::displayName)) + 1)
      myListModel.add(position, itemToAdd)
      onLanguageAdded(itemToAdd)
      myList.clearSelection()
      myList.setSelectedValue(itemToAdd, true)
    }
  }

  override fun findItemToAdd(): Lang? {
    // remove already enabled languages and their dialects
    val langsInList = listItems.map { (it as Lang).iso }.toSet()
    val (downloadedLangs, otherLangs) = Lang.sortedValues().filter { it.iso !in langsInList }.partition { it.jLanguage != null }

    val step = GrazieListPopupStep(msg("grazie.ui.settings.language.popup.title"), downloadedLangs, otherLangs, download, ::addElement)
    val menu = object : ListPopupImpl(null, step) {
      override fun getListElementRenderer() = GraziePopupListElementRenderer(this)
    }

    decorator.actionsPanel?.getAnActionButton(CommonActionsPanel.Buttons.ADD)?.preferredPopupPoint?.let(menu::show)
    return null
  }

  fun reset(langs: Iterable<Lang>) {
    val model = myList.model as DefaultListModel<Lang>
    model.elements().asSequence().forEach(onLanguageRemoved)
    model.clear()
    langs.forEach(::addElement)
  }

  private class GrazieListToolbarDecorator(val list: JList<Any>) : ToolbarDecorator() {
    init {
      myRemoveActionEnabled = true
      myAddActionEnabled = true

      list.addListSelectionListener { updateButtons() }
      list.addPropertyChangeListener("enabled") { updateButtons() }
    }

    public override fun updateButtons() {
      actionsPanel?.let {
        it.setEnabled(CommonActionsPanel.Buttons.ADD, list.isEnabled && list.model.size < Lang.values().size)
        it.setEnabled(CommonActionsPanel.Buttons.REMOVE, !list.isSelectionEmpty)
        updateExtraElementActions(!list.isSelectionEmpty)
      }
    }

    override fun setVisibleRowCount(rowCount: Int) = this.also { list.visibleRowCount = rowCount }

    override fun getComponent() = list

    override fun installDnDSupport() = RowsDnDSupport.install(list, list.model as EditableModel)

    override fun isModelEditable() = true
  }
}
