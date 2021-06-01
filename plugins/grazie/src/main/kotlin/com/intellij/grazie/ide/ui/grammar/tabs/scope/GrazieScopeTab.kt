package com.intellij.grazie.ide.ui.grammar.tabs.scope

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.config.CheckingContext
import com.intellij.grazie.ide.ui.components.GrazieUIComponent
import com.intellij.grazie.ide.ui.components.dsl.border
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.ide.ui.components.dsl.panel
import com.intellij.grazie.text.TextExtractor
import com.intellij.lang.Language
import com.intellij.ui.CheckBoxList
import com.intellij.ui.CheckBoxListListener
import com.intellij.ui.ListSpeedSearch
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.migLayout.*
import com.intellij.util.ui.JBUI
import net.miginfocom.layout.CC
import net.miginfocom.swing.MigLayout

class GrazieScopeTab : GrazieUIComponent {
  private val myDisabledLanguageIds = hashSetOf<String>()

  private val cbLiterals = JBCheckBox(msg("grazie.settings.grammar.scope.places-to-check.literals"))
  private val cbComments = JBCheckBox(msg("grazie.settings.grammar.scope.places-to-check.comments"))
  private val cbDocumentation = JBCheckBox(msg("grazie.settings.grammar.scope.places-to-check.documentation"))
  private val cbCommits = JBCheckBox(msg("grazie.settings.grammar.scope.places-to-check.commits"))
  private val languageList = CheckBoxList<Language>()

  init {
    languageList.setCheckBoxListListener(CheckBoxListListener { index, selected ->
      val language = languageList.getItemAt(index) ?: return@CheckBoxListListener

      if (selected) {
        myDisabledLanguageIds.remove(language.id)
      }
      else {
        myDisabledLanguageIds.add(language.id)
      }
    })
    ListSpeedSearch(languageList) { it.text }
  }

  override fun isModified(state: GrazieConfig.State) =
    with(state.checkingContext) {
      cbLiterals.isSelected != isCheckInStringLiteralsEnabled ||
      cbComments.isSelected != isCheckInCommentsEnabled ||
      cbDocumentation.isSelected != isCheckInDocumentationEnabled ||
      cbCommits.isSelected != isCheckInCommitMessagesEnabled ||
      myDisabledLanguageIds != disabledLanguages
    }

  override fun reset(state: GrazieConfig.State) {
    with(state.checkingContext) {
      myDisabledLanguageIds.clear()
      myDisabledLanguageIds.addAll(disabledLanguages)
      cbLiterals.isSelected = isCheckInStringLiteralsEnabled
      cbComments.isSelected = isCheckInCommentsEnabled
      cbDocumentation.isSelected = isCheckInDocumentationEnabled
      cbCommits.isSelected = isCheckInCommitMessagesEnabled
    }

    languageList.clear()
    for (language in TextExtractor.getSupportedLanguages().sortedBy { it.displayName }) {
      languageList.addItem(language, language.displayName, language.id !in myDisabledLanguageIds)
    }
  }

  override fun apply(state: GrazieConfig.State): GrazieConfig.State = state.copy(
    checkingContext = CheckingContext(
      isCheckInStringLiteralsEnabled = cbLiterals.isSelected,
      isCheckInCommentsEnabled = cbComments.isSelected,
      isCheckInDocumentationEnabled = cbDocumentation.isSelected,
      isCheckInCommitMessagesEnabled = cbCommits.isSelected,
      disabledLanguages = myDisabledLanguageIds
    )
  )

  override val component = panel(MigLayout(createLayoutConstraints())) {
    border = JBUI.Borders.empty()

    add(
      panel {
        border = border(msg("grazie.settings.grammar.scope.file-types.text"), false, JBUI.emptyInsets(), false)
        add(ScrollPaneFactory.createScrollPane(languageList))
      }, CC().grow().width("180px").height("50%").alignX("left")
    )

    add(
      panel(MigLayout(createLayoutConstraints().insets("1", "0", "0", "0"))) {
        border = border(msg("grazie.settings.grammar.scope.places-to-check.text"), true, JBUI.insetsLeft(50), false)
        add(cbLiterals, CC().wrap())
        add(cbComments, CC().wrap())
        add(cbDocumentation, CC().wrap())
        add(cbCommits, CC().wrap())
      }, CC().width("218px").alignY("top")
    )
  }
}
