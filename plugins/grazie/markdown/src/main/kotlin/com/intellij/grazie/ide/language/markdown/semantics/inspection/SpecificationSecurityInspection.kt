package com.intellij.grazie.ide.language.markdown.semantics.inspection

import ai.grazie.rules.promptAnalysis.LlmAnalyzer
import ai.grazie.rules.promptAnalysis.SecurityAnalyzer
import ai.grazie.rules.promptAnalysis.SecurityAnalyzer.SecurityVulnerability
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.grazie.GrazieBundle
import com.intellij.psi.PsiFile

internal class SpecificationSecurityInspection : SpecificationBaseInspection<SecurityVulnerability>() {

  override fun reportProblem(holder: ProblemsHolder, file: PsiFile, problem: SecurityVulnerability) {
    val text = file.text
    findAllOccurrences(problem.text, text).forEach { range ->
      holder.registerProblem(createProblemDescriptor(
        file, range,
        GrazieBundle.message("specification.inspection.security.problem")
      ))
    }
  }

  override fun getAnalyzer(file: PsiFile): LlmAnalyzer<SecurityVulnerability> = SecurityAnalyzer()
}
