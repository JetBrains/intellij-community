package com.intellij.grazie.ide.language.markdown.semantics.inspection

import ai.grazie.rules.promptAnalysis.LlmAnalyzer
import ai.grazie.rules.promptAnalysis.SemanticCoverageAnalyzer
import ai.grazie.rules.promptAnalysis.SemanticCoverageAnalyzer.CoverageIssue
import com.intellij.psi.PsiFile

internal class SpecificationSematicCoverageInspection : SpecificationBaseInspection<CoverageIssue>() {
  override fun getAnalyzer(file: PsiFile): LlmAnalyzer<CoverageIssue> = SemanticCoverageAnalyzer()
}
