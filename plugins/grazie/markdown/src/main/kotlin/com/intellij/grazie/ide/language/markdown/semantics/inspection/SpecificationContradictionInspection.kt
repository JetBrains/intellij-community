package com.intellij.grazie.ide.language.markdown.semantics.inspection

import ai.grazie.rules.promptAnalysis.ContradictionAnalyzer
import ai.grazie.rules.promptAnalysis.ContradictionAnalyzer.Contradiction
import ai.grazie.rules.promptAnalysis.LlmAnalyzer
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiFile

internal class SpecificationContradictionInspection : SpecificationBaseInspection<Contradiction>() {
  override fun getAnalyzer(file: PsiFile): LlmAnalyzer<Contradiction> =
    ContradictionAnalyzer(Registry.`is`("grazie.specification.verification.enabled"))
}
