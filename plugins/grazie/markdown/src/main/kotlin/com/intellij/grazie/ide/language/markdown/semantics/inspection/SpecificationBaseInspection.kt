package com.intellij.grazie.ide.language.markdown.semantics.inspection

import ai.grazie.api.gateway.client.SuspendableAPIGatewayClient
import ai.grazie.rules.promptAnalysis.LlmAnalyzer
import ai.grazie.rules.promptAnalysis.LlmAnalyzer.LlmIssue
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemDescriptorBase
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.grazie.cloud.GrazieCloudConnector
import com.intellij.grazie.cloud.GrazieCloudConnector.Companion.hasAdditionalConnectors
import com.intellij.grazie.cloud.GrazieCloudConnector.Companion.hasQuota
import com.intellij.grazie.cloud.GrazieCloudConnector.Companion.seemsCloudConnected
import com.intellij.grazie.ide.language.markdown.semantics.analyzer.Analyzer
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.util.text.StringSearcher

internal abstract class SpecificationBaseInspection<T> : LocalInspectionTool() {

  open fun reportProblem(holder: ProblemsHolder, file: PsiFile, issue: LlmIssue<T>) {
    findAllOccurrences(issue, file).forEach { range ->
      @Suppress("HardCodedStringLiteral")
      holder.registerProblem(createProblemDescriptor(file, range, issue.message))
    }
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
        if (!isAgentMarkdownFile(file)) {
          thisLogger().info("${file.name} is not agent-like")
          return
        }
        val analyzer = getAnalyzer(file) ?: return
        Analyzer.analyze(analyzer, file, client)
          .forEach { problem -> reportProblem(holder, file, problem) }
      }
    }
  }

  protected fun findAllOccurrences(issue: LlmIssue<T>, file: PsiFile): List<TextRange> {
    if (issue.startOffset != -1 && issue.endOffset != -1) {
      return listOf(TextRange(issue.startOffset, issue.endOffset))
    }
    val pattern = issue.text
    val indexes = StringSearcher(pattern, false, true).findAllOccurrences(file.text)
    if (indexes.isEmpty()) {
      thisLogger().warn("No occurrences found by ${javaClass.name} in text")
    }
    return indexes.map { index -> TextRange(index, index + pattern.length) }
  }

  protected fun createProblemDescriptor(file: PsiFile, range: TextRange, @InspectionMessage description: String): ProblemDescriptorBase =
    ProblemDescriptorBase(
      file, file, description, emptyArray(),
      ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
      false, range, true, true
    )

  private fun isAgentMarkdownFile(file: PsiFile): Boolean = AGENT_MARKDOWN_FILE_NAME_PATTERN.matches(file.name)

  private fun validateAndGetClient(isOnTheFly: Boolean): SuspendableAPIGatewayClient? {
    if (!isOnTheFly) return null
    if (!Registry.`is`("grazie.specification.semantics.enabled")) {
      thisLogger().debug("Specification semantics inspection is disabled")
      return null
    }
    if (!hasAdditionalConnectors() || !seemsCloudConnected() || !hasQuota()) {
      thisLogger().warn("Additional connectors = ${hasAdditionalConnectors()}, seemsCloudConnected = ${seemsCloudConnected()}, hasQuota = ${hasQuota()}")
      return null
    }
    return GrazieCloudConnector.api()?.also { thisLogger().info("API client is not null") }
  }

  companion object {
    private val AGENT_MARKDOWN_FILE_NAME_PATTERN = Regex(
      "(agents|agent|ai|claude|copilot-instructions|prompt|skill|system[-_]prompt|spec|architecture)\\.md",
      RegexOption.IGNORE_CASE,
    )
  }
}