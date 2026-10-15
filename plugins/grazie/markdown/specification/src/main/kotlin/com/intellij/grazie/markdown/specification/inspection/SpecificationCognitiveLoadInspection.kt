package com.intellij.grazie.markdown.specification.inspection

import ai.grazie.rules.promptAnalysis.CognitiveLoadAnalyzer
import ai.grazie.rules.promptAnalysis.CognitiveLoadAnalyzer.ComplexityIssue
import ai.grazie.rules.promptAnalysis.LlmAnalyzer
import com.intellij.psi.PsiFile

internal class SpecificationCognitiveLoadInspection: SpecificationBaseInspection<ComplexityIssue>() {
  override fun getAnalyzer(file: PsiFile): LlmAnalyzer<ComplexityIssue> = CognitiveLoadAnalyzer()
}
