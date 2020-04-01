package com.intellij.grazie.ide.ui.grammar.tabs.scope

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.config.CheckingContext
import com.intellij.grazie.ide.ui.components.GrazieUIComponent
import com.intellij.grazie.ide.ui.components.dsl.border
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.ide.ui.components.dsl.panel
import com.intellij.grazie.ide.ui.grammar.tabs.scope.component.GrazieStrategiesComponent
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.migLayout.*
import com.intellij.util.ui.JBUI
import net.miginfocom.layout.CC
import net.miginfocom.swing.MigLayout

class GrazieScopeTab : GrazieUIComponent {
  private val cbLiterals = JBCheckBox(msg("grazie.settings.grammar.scope.places-to-check.literals"))
  private val cbComments = JBCheckBox(msg("grazie.settings.grammar.scope.places-to-check.comments"))
  private val cbDocumentation = JBCheckBox(msg("grazie.settings.grammar.scope.places-to-check.documentation"))
  private val cbCommits = JBCheckBox(msg("grazie.settings.grammar.scope.places-to-check.commits"))

  private val strategies = GrazieStrategiesComponent()

  override fun isModified(state: GrazieConfig.State) =
    strategies.isModified(state) ||
    with(state.checkingContext) {
      cbLiterals.isSelected != isCheckInStringLiteralsEnabled ||
      cbComments.isSelected != isCheckInCommentsEnabled ||
      cbDocumentation.isSelected != isCheckInDocumentationEnabled ||
      cbCommits.isSelected != isCheckInCommitMessagesEnabled
    }

  override fun reset(state: GrazieConfig.State) {
    strategies.reset(state)

    with(state.checkingContext) {
      cbLiterals.isSelected = isCheckInStringLiteralsEnabled
      cbComments.isSelected = isCheckInCommentsEnabled
      cbDocumentation.isSelected = isCheckInDocumentationEnabled
      cbCommits.isSelected = isCheckInCommitMessagesEnabled
    }
  }

  override fun apply(state: GrazieConfig.State): GrazieConfig.State = strategies.apply(state).copy(
    checkingContext = CheckingContext(
      isCheckInStringLiteralsEnabled = cbLiterals.isSelected,
      isCheckInCommentsEnabled = cbComments.isSelected,
      isCheckInDocumentationEnabled = cbDocumentation.isSelected,
      isCheckInCommitMessagesEnabled = cbCommits.isSelected
    )
  )

  override val component = panel(MigLayout(createLayoutConstraints())) {
    border = JBUI.Borders.empty()

    add(
      panel {
        border = border(msg("grazie.settings.grammar.scope.file-types.text"), false, JBUI.emptyInsets(), false)
        add(strategies.component)
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
