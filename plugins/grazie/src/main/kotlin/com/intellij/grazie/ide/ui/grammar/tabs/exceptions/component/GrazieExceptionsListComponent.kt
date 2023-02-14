package com.intellij.grazie.ide.ui.grammar.tabs.exceptions.component

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.config.SuppressingContext
import com.intellij.grazie.ide.ui.components.GrazieUIComponent
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.ide.ui.components.dsl.padding
import com.intellij.grazie.ide.ui.components.utils.ConfigurableListCellRenderer
import com.intellij.grazie.ide.ui.components.utils.configure
import com.intellij.grazie.utils.toSet
import com.intellij.grazie.utils.trimToNull
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.ui.Messages
import com.intellij.ui.AddDeleteListPanel
import com.intellij.ui.ListSpeedSearch
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.ToolbarDecorator
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import javax.swing.JComponent
import javax.swing.ListCellRenderer

class GrazieExceptionsListComponent(
  exceptions: List<String>
): GrazieUIComponent, AddDeleteListPanel<@Nls String>(null, exceptions.sorted()) {
  init {
    ListSpeedSearch(myList) { it }

    emptyText.text = msg("grazie.settings.grammar.exceptions.empty.text")

    emptyText.appendSecondaryText(
      msg("grazie.settings.grammar.exceptions.empty.text.explanation", KeymapUtil.getShortcutText("ShowIntentionActions")),
      SimpleTextAttributes.GRAYED_ATTRIBUTES, null
    )
  }

  override fun findItemToAdd() = Messages.showInputDialog(
    msg("grazie.settings.grammar.exceptions.add.message"), msg("grazie.settings.grammar.exceptions.add.title"), null
  )?.trimToNull()

  override fun getListCellRenderer(): ListCellRenderer<*> = ConfigurableListCellRenderer<@Nls String> { component, value ->
    component.configure {
      border = padding(JBUI.insets(5))
      text = value
    }
  }

  override fun customizeDecorator(decorator: ToolbarDecorator?) {
    decorator?.disableAddAction()
  }

  override val component: JComponent = this

  override fun isModified(state: GrazieConfig.State) = state.suppressingContext.suppressed != myListModel.elements().toSet()

  override fun reset(state: GrazieConfig.State) {
    myListModel.clear()
    state.suppressingContext.suppressed.sorted().forEach {
      @Suppress("HardCodedStringLiteral")
      myListModel.addElement(it)
    }
  }

  override fun apply(state: GrazieConfig.State) = state.copy(suppressingContext = SuppressingContext(myListModel.elements().toSet()))
}
