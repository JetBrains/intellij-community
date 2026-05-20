package com.intellij.grazie.ide.language.markdown.semantics.inspection

import ai.grazie.rules.promptAnalysis.ContradictionAnalyzer
import ai.grazie.rules.promptAnalysis.ContradictionAnalyzer.Contradiction
import ai.grazie.rules.promptAnalysis.LlmAnalyzer
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.grazie.GrazieBundle
import com.intellij.psi.PsiFile

internal class SpecificationContradictionInspection : SpecificationBaseInspection<Contradiction>() {

  override fun reportProblem(holder: ProblemsHolder, file: PsiFile, problem: Contradiction) {
    val text = file.text
    findAllOccurrences(problem.text, text).forEach { range ->
      holder.registerProblem(createProblemDescriptor(
        file, range,
        GrazieBundle.message ("specification.inspection.contradiction.problem", problem.suggestion)
      ))
    }
  }

  override fun getAnalyzer(file: PsiFile): LlmAnalyzer<Contradiction> = ContradictionAnalyzer()
}
