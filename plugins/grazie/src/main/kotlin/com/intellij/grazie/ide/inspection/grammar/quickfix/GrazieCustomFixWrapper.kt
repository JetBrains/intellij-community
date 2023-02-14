package com.intellij.grazie.ide.inspection.grammar.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.choice.ChoiceTitleIntentionAction
import com.intellij.codeInsight.intention.choice.ChoiceVariantIntentionAction
import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.LocalQuickFixAsIntentionAdapter
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.grazie.text.TextProblem

internal class GrazieCustomFixWrapper(problem: TextProblem,
                                      delegate: LocalQuickFix,
                                      descriptor: ProblemDescriptor,
                                      private val index: Int) :
  IntentionWrapper(if (delegate is IntentionAction) delegate else LocalQuickFixAsIntentionAdapter(delegate, descriptor)),
  Comparable<IntentionAction>
{
  private val problemFamily: String = GrazieReplaceTypoQuickFix.familyName(problem)

  override fun compareTo(other: IntentionAction): Int {
    if ((other is ChoiceVariantIntentionAction || other is ChoiceTitleIntentionAction) && other.familyName == problemFamily) return 1
    if (other is GrazieAddExceptionQuickFix || other is GrazieRuleSettingsAction) return -1
    if (other is GrazieCustomFixWrapper && this.problemFamily == other.problemFamily) return index.compareTo(other.index)
    return familyName.compareTo(other.familyName)
  }
}
