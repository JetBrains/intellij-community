package com.intellij.grazie.ide.ui.grammar.tabs.exceptions.component

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.config.SuppressingContext
import com.intellij.grazie.ide.ui.components.GrazieUIComponent
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.ide.ui.components.dsl.padding
import com.intellij.grazie.ide.ui.components.dsl.setEmptyTextPlaceholder
import com.intellij.grazie.ide.ui.components.utils.ConfigurableListCellRenderer
import com.intellij.grazie.ide.ui.components.utils.configure
import com.intellij.grazie.utils.toSet
import com.intellij.grazie.utils.trimToNull
import com.intellij.openapi.ui.Messages
import com.intellij.ui.AddDeleteListPanel
import com.intellij.ui.CommonActionsPanel
import com.intellij.ui.ListSpeedSearch
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.ListCellRenderer

class GrazieExceptionsListComponent(exceptions: List<String>) : GrazieUIComponent, AddDeleteListPanel<String>(null, exceptions.sorted()) {
  init {
    ListSpeedSearch(myList) { it }

    emptyText.setEmptyTextPlaceholder(
      mainText = msg("grazie.settings.grammar.exceptions.empty.text"),
      shortcutText = msg("grazie.settings.grammar.exceptions.empty.action"),
      shortcutButton = CommonActionsPanel.Buttons.ADD,
      shortcutAction = { addElement(findItemToAdd()) }
    )
  }

  override fun findItemToAdd() = Messages.showInputDialog(
    msg("grazie.settings.grammar.exceptions.add.message"), msg("grazie.settings.grammar.exceptions.add.title"), null
  )?.trimToNull()

  override fun getListCellRenderer(): ListCellRenderer<*> = ConfigurableListCellRenderer<String> { component, value ->
    component.configure {
      border = padding(JBUI.insets(5))
      text = value
    }
  }

  override val component: JComponent = this

  override fun isModified(state: GrazieConfig.State) = state.suppressingContext.suppressed != myListModel.elements().toSet()

  override fun reset(state: GrazieConfig.State) {
    myListModel.clear()
    state.suppressingContext.suppressed.sorted().forEach { myListModel.addElement(it) }
  }

  override fun apply(state: GrazieConfig.State) = state.copy(suppressingContext = SuppressingContext(myListModel.elements().toSet()))
}
