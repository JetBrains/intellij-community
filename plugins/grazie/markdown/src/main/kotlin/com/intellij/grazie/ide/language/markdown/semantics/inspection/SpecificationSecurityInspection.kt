package com.intellij.grazie.ide.language.markdown.semantics.inspection

import ai.grazie.rules.promptAnalysis.LlmAnalyzer
import ai.grazie.rules.promptAnalysis.SecurityAnalyzer
import ai.grazie.rules.promptAnalysis.SecurityAnalyzer.SecurityVulnerability
import com.intellij.psi.PsiFile

internal class SpecificationSecurityInspection : SpecificationBaseInspection<SecurityVulnerability>() {
  override fun getAnalyzer(file: PsiFile): LlmAnalyzer<SecurityVulnerability> = SecurityAnalyzer()
}
