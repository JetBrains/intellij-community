package com.intellij.grazie.ide.language.markdown.semantics.utils

import com.intellij.grazie.cloud.GrazieCloudConnector.Companion.hasQuota
import com.intellij.grazie.cloud.GrazieCloudConnector.Companion.seemsCloudConnected
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiFile

internal object SpecificationUtils {
  private val SPECIFICATION_LIKE_PATTERN = Regex(
    "(agents|agent|ai|claude|copilot-instructions|prompt|skill|system[-_]prompt|spec|architecture)\\.md",
    RegexOption.IGNORE_CASE,
  )

  internal fun isAnalysisEnabled(): Boolean =
    Registry.`is`("grazie.specification.semantics.enabled") && seemsCloudConnected() && hasQuota()

  internal fun isSpecificationLikeFile(file: PsiFile): Boolean {
    if (SPECIFICATION_LIKE_PATTERN.matches(file.name)) return true
    val pattern = Regex(Registry.stringValue("grazie.specification.semantics.specification.pattern"))
    return pattern.matches(file.virtualFile.path)
  }
}