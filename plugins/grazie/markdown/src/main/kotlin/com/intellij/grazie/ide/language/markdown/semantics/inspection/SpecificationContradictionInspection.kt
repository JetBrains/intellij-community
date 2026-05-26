package com.intellij.grazie.ide.language.markdown.semantics.inspection

import ai.grazie.rules.promptAnalysis.ContradictionAnalyzer
import ai.grazie.rules.promptAnalysis.ContradictionAnalyzer.Contradiction
import ai.grazie.rules.promptAnalysis.LlmAnalyzer
import ai.grazie.rules.promptAnalysis.LlmAnalyzer.LlmIssue
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.grazie.GrazieBundle
import com.intellij.psi.PsiFile

internal class SpecificationContradictionInspection : SpecificationBaseInspection<Contradiction>() {

  override fun reportProblem(holder: ProblemsHolder, file: PsiFile, issue: LlmIssue<Contradiction>) {
    findAllOccurrences(issue, file).forEach { range ->
      holder.registerProblem(createProblemDescriptor(
        file, range,
        GrazieBundle.message ("specification.inspection.contradiction.problem", issue.issue.suggestion)
      ))
    }
  }

  override fun getAnalyzer(file: PsiFile): LlmAnalyzer<Contradiction> = ContradictionAnalyzer()
}
