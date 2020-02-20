package com.intellij.grazie.ide.inspection.grammar.problem

import com.intellij.codeInspection.SuppressableProblemGroup
import com.intellij.grazie.grammar.Typo
import com.intellij.grazie.ide.inspection.grammar.problem.suppress.GrazieDisableCategoryIntention
import com.intellij.grazie.ide.inspection.grammar.problem.suppress.GrazieDisableContextIntention
import com.intellij.grazie.ide.inspection.grammar.problem.suppress.GrazieDisableRuleIntention
import com.intellij.psi.PsiElement

class GrazieProblemGroup(val id: String, val fix: Typo) : SuppressableProblemGroup {
  override fun getProblemName(): String = id

  override fun getSuppressActions(element: PsiElement?) = arrayOf(
    GrazieDisableCategoryIntention(fix),
    GrazieDisableRuleIntention(fix),
    GrazieDisableContextIntention(fix)
  )
}