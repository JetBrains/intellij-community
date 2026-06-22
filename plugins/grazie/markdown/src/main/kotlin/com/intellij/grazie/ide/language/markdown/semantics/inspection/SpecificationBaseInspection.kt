package com.intellij.grazie.ide.language.markdown.semantics.inspection

import ai.grazie.api.gateway.client.SuspendableAPIGatewayClient
import ai.grazie.rules.promptAnalysis.LlmAnalyzer
import ai.grazie.rules.promptAnalysis.LlmAnalyzer.LlmIssue
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemDescriptorBase
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.grazie.cloud.GrazieCloudConnector
import com.intellij.grazie.cloud.GrazieCloudConnector.Companion.hasQuota
import com.intellij.grazie.cloud.GrazieCloudConnector.Companion.seemsCloudConnected
import com.intellij.grazie.ide.language.markdown.semantics.analyzer.SpecificationAnalyzer
import com.intellij.grazie.ide.language.markdown.semantics.inspection.quickfix.ReplacementQuickFix
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.Experimental
abstract class SpecificationBaseInspection<T> : LocalInspectionTool() {

  open fun reportProblem(holder: ProblemsHolder, file: PsiFile, issue: LlmIssue<T>) {
    if (issue.startOffset() == -1 && issue.endOffset() == -1) {
      thisLogger().warn("No occurrences found by ${javaClass.name} in text")
      return
    }

    val range = TextRange(issue.startOffset(), issue.endOffset())
    val underline = SmartPointerManager.getInstance(file.project).createSmartPsiFileRangePointer(file, range)
    val replacements = issue.replacements
    val fixes = if (replacements.isNotEmpty()) ReplacementQuickFix(underline, replacements).getAllAsFixes().toTypedArray() else emptyArray()
    val descriptor = ProblemDescriptorBase(
      file, file, issue.message, fixes,
      ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
      false, range, true, true
    )
    holder.registerProblem(descriptor)
  }

  abstract fun getAnalyzer(file: PsiFile): LlmAnalyzer<T>?

  final override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor {
    val client = validateAndGetClient(isOnTheFly) ?: return PsiElementVisitor.EMPTY_VISITOR

    return object : PsiElementVisitor() {
      override fun visitFile(file: PsiFile) {
        // Markdown file has two PSI trees: HTML and Markdown
        // We need to filter out HTML one otherwise it's going to be analyzed twice
        if (file !is MarkdownFile) return

        if (!isAgentMarkdownFile(file)) {
          thisLogger().info("${file.name} is not agent-like")
          return
        }
        val analyzer = getAnalyzer(file) ?: return
        SpecificationAnalyzer.analyze(analyzer, file, client)
          .forEach { problem -> reportProblem(holder, file, problem) }
      }
    }
  }

  private fun isAgentMarkdownFile(file: PsiFile): Boolean = AGENT_MARKDOWN_FILE_NAME_PATTERN.matches(file.name)

  private fun validateAndGetClient(isOnTheFly: Boolean): SuspendableAPIGatewayClient? {
    if (!isOnTheFly) return null
    if (!Registry.`is`("grazie.specification.semantics.enabled") || !seemsCloudConnected() || !hasQuota()) return null
    return GrazieCloudConnector.api()
  }

  companion object {
    private val AGENT_MARKDOWN_FILE_NAME_PATTERN = Regex(
      "(agents|agent|ai|claude|copilot-instructions|prompt|skill|system[-_]prompt|spec|architecture)\\.md",
      RegexOption.IGNORE_CASE,
    )
  }
}