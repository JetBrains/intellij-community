package com.intellij.grazie.ide.language.markdown.semantics.inspection

import ai.grazie.rules.promptAnalysis.LlmAnalyzer
import ai.grazie.rules.promptAnalysis.LlmAnalyzer.LlmIssue
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemDescriptorBase
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.grazie.cloud.GrazieCloudConnector
import com.intellij.grazie.ide.language.markdown.semantics.analyzer.SpecificationAnalyzer
import com.intellij.grazie.ide.language.markdown.semantics.inspection.quickfix.SpecificationReplacementQuickFix
import com.intellij.grazie.ide.language.markdown.semantics.utils.SpecificationUtils.isAnalysisEnabled
import com.intellij.grazie.ide.language.markdown.semantics.utils.SpecificationUtils.isSpecificationLikeFile
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.Experimental
abstract class SpecificationBaseInspection<T> : LocalInspectionTool() {

  open fun reportProblems(holder: ProblemsHolder, file: PsiFile, dependencies: Set<PsiFile>, issues: List<LlmIssue<T>>) {
    issues.forEach { issue -> reportProblem(holder, file, issue) }
  }

  private fun reportProblem(holder: ProblemsHolder, file: PsiFile, issue: LlmIssue<T>) {
    if (issue.startOffset() == -1 && issue.endOffset() == -1) {
      thisLogger().warn("No occurrences found by ${javaClass.name} in text")
      return
    }

    val range = TextRange(issue.startOffset(), issue.endOffset())
    val underline = SmartPointerManager.getInstance(file.project).createSmartPsiFileRangePointer(file, range)
    val fixes = SpecificationReplacementQuickFix(underline, issue.replacements).getAllAsFixes()
    val descriptor = ProblemDescriptorBase(
      file, file, issue.message, fixes.toTypedArray(),
      ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
      false, range, true, true
    )
    holder.registerProblem(descriptor)
  }

  /**
   * Returns the dependency set for a given file. The resulting set must always contain [root].
   */
  open fun getDependencies(root: PsiFile): Set<PsiFile> = setOf(root)

  abstract fun getAnalyzer(file: PsiFile): LlmAnalyzer<T>?

  final override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor {
    if (!isOnTheFly || !isAnalysisEnabled()) return PsiElementVisitor.EMPTY_VISITOR
    val client = GrazieCloudConnector.api() ?: return PsiElementVisitor.EMPTY_VISITOR

    return object : PsiElementVisitor() {
      override fun visitFile(file: PsiFile) {
        // Markdown file has two PSI trees: HTML and Markdown
        // We need to filter out HTML one otherwise it's going to be analyzed twice
        if (file !is MarkdownFile) return

        if (!isSpecificationLikeFile(file)) {
          thisLogger().info("${file.name} is not specification-like")
          return
        }
        val analyzer = getAnalyzer(file) ?: return
        val dependencies = getDependencies(file)
        val issues = SpecificationAnalyzer.analyze(analyzer, file, dependencies , client)
        reportProblems(holder, file, dependencies, issues)
      }
    }
  }
}