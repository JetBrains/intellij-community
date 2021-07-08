package com.intellij.grazie.ide.inspection.grammar.quickfix

import com.intellij.codeInspection.IntentionAndQuickFixAction
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

internal class GrazieRuleSettingsAction(private val ruleName: String, private val rule: Rule) : IntentionAndQuickFixAction(), Iconable {

  override fun getIcon(flags: Int): Icon = AllIcons.Actions.Edit

  override fun getName() = msg("grazie.grammar.quickfix.open.rule.text", ruleName)

  override fun getFamilyName(): String = msg("grazie.grammar.quickfix.open.rule.family")

  override fun startInWriteAction() = false

  override fun applyFix(project: Project, file: PsiFile?, editor: Editor?) {
    val configurable = GrazieConfigurable()
    configurable.selectRule(rule.globalId)
    ShowSettingsUtil.getInstance().editConfigurable(project, configurable)
  }
}