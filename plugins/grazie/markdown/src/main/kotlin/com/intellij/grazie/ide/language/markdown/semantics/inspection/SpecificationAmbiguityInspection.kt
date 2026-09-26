package com.intellij.grazie.ide.language.markdown.semantics.inspection

import ai.grazie.rules.promptAnalysis.AmbiguityAnalyzer
import ai.grazie.rules.promptAnalysis.AmbiguityAnalyzer.Ambiguity
import ai.grazie.rules.promptAnalysis.LlmAnalyzer
import com.intellij.psi.PsiFile

internal class SpecificationAmbiguityInspection : SpecificationBaseInspection<Ambiguity>() {
  override fun getAnalyzer(file: PsiFile): LlmAnalyzer<Ambiguity> = AmbiguityAnalyzer()
}
