package com.intellij.grazie.ide.language.properties

import com.intellij.grazie.text.ProblemFilter
import com.intellij.grazie.text.RuleGroup
import com.intellij.grazie.text.TextProblem

class PropertyProblemFilter : ProblemFilter() {
  override fun shouldIgnore(problem: TextProblem): Boolean = problem.fitsGroup(RuleGroup.UNDECORATED_SINGLE_SENTENCE)
}