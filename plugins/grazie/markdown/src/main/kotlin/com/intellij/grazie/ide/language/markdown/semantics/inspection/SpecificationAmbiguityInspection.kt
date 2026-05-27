package com.intellij.grazie.ide.language.markdown.semantics.inspection

import ai.grazie.rules.promptAnalysis.AmbiguityAnalyzer
import ai.grazie.rules.promptAnalysis.AmbiguityAnalyzer.Ambiguity
import ai.grazie.rules.promptAnalysis.LlmAnalyzer
import ai.grazie.rules.promptAnalysis.LlmAnalyzer.LlmIssue
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.grazie.GrazieBundle
import com.intellij.psi.PsiFile

internal class SpecificationAmbiguityInspection : SpecificationBaseInspection<Ambiguity>() {
  override fun reportProblem(holder: ProblemsHolder, file: PsiFile, issue: LlmIssue<Ambiguity>) {
    val problem = issue.issue
    findAllOccurrences(issue, file).forEach { range ->
      holder.registerProblem(createProblemDescriptor(
        file, range,
        GrazieBundle.message("specification.inspection.ambiguity.problem", problem.problem, problem.suggestion)
      ))
    }
  }

  override fun getAnalyzer(file: PsiFile): LlmAnalyzer<Ambiguity> = AmbiguityAnalyzer()
}
