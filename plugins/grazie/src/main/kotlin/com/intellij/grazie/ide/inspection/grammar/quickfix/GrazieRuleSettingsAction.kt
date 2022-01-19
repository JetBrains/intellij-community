package com.intellij.grazie.ide.inspection.grammar.quickfix

import com.intellij.codeInsight.intention.CustomizableIntentionAction
import com.intellij.codeInspection.IntentionAndQuickFixAction
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.ide.fus.GrazieFUSCounter
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.ide.ui.grammar.GrazieConfigurable
import com.intellij.grazie.text.Rule
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import javax.swing.Icon

internal class GrazieRuleSettingsAction(private val ruleName: String, private val rule: Rule)
  : IntentionAndQuickFixAction(), Iconable, CustomizableIntentionAction
{
  override fun isShowSubmenu(): Boolean = false

  override fun isSelectable(): Boolean = true

  override fun isShowIcon(): Boolean = true

  override fun getIcon(flags: Int): Icon = AllIcons.Actions.Edit

  override fun getName() = msg("grazie.grammar.quickfix.open.rule.text", ruleName)

  override fun getFamilyName(): String = msg("grazie.grammar.quickfix.open.rule.family")

  override fun startInWriteAction() = false

  override fun applyFix(project: Project, file: PsiFile?, editor: Editor?) {
    val state1 = GrazieConfig.get()

    val ok: Boolean
    val navigatable = rule.editSettings()
    if (navigatable != null && navigatable.canNavigate()) {
      navigatable.navigate(true)
      ok = true
    } else {
      val configurable = GrazieConfigurable()
      configurable.selectRule(rule.globalId)
      ok = ShowSettingsUtil.getInstance().editConfigurable(project, configurable)
    }

    val result = if (!ok) "canceled" else analyzeStateChange(state1, GrazieConfig.get())
    GrazieFUSCounter.quickFixInvoked(rule, project, "rule.settings:$result")
  }

  private fun analyzeStateChange(state1: GrazieConfig.State, state2: GrazieConfig.State): String {
    if (state1 == state2) return "unmodified"

    val changes = arrayListOf<String>()
    if (state1.checkingContext.languagesDiffer(state2.checkingContext)) {
      changes.add("languages")
    }
    if (state1.checkingContext.domainsDiffer(state2.checkingContext)) {
      changes.add("domains")
    }
    if (state1.userDisabledRules != state2.userDisabledRules || state1.userEnabledRules != state2.userEnabledRules) {
      changes.add("rules")
    }
    if (changes.isEmpty()) {
      changes.add("unclassified")
    }

    return "changes:" + changes.joinToString(",")
  }
}