package com.intellij.grazie.ide.language.markdown.semantics.inspection

import ai.grazie.rules.promptAnalysis.LlmAnalyzer
import ai.grazie.rules.promptAnalysis.SemanticCoverageAnalyzer
import ai.grazie.rules.promptAnalysis.SemanticCoverageAnalyzer.CoverageIssue
import ai.grazie.rules.promptAnalysis.SemanticCoverageAnalyzer.IssueType.COVERAGE_GAP
import ai.grazie.rules.promptAnalysis.SemanticCoverageAnalyzer.IssueType.MISSING_ERROR_HANDLING
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.grazie.GrazieBundle
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

internal class SpecificationSematicCoverageInspection : SpecificationBaseInspection<CoverageIssue>() {

  override fun reportProblem(holder: ProblemsHolder, file: PsiFile, problem: CoverageIssue) {
    val text = file.text
    val ranges = if (problem.startOffset != -1 && problem.endOffset != -1) {
      listOf(TextRange(problem.startOffset, problem.endOffset))
    } else {
      findAllOccurrences(problem.text, text)
    }
    ranges.forEach { range ->
      holder.registerProblem(createProblemDescriptor(file, range, getProblemDescription(problem)))
    }
  }

  override fun getAnalyzer(file: PsiFile): LlmAnalyzer<CoverageIssue> = SemanticCoverageAnalyzer()

  private fun getProblemDescription(problem: CoverageIssue): @InspectionMessage String {
    val key = when (problem.type) {
      COVERAGE_GAP -> "specification.inspection.semanticCoverage.problem.coverage.gap"
      MISSING_ERROR_HANDLING -> "specification.inspection.semanticCoverage.problem.missing.error.handling"
    }
    return GrazieBundle.message(key)
  }
}
