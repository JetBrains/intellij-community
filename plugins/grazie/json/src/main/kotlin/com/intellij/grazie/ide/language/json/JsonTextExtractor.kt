// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language.json

import com.intellij.grazie.text.ProblemFilter
import com.intellij.grazie.text.RuleGroup
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextContent.TextDomain
import com.intellij.grazie.text.TextContentBuilder
import com.intellij.grazie.text.TextExtractor
import com.intellij.grazie.text.TextProblem
import com.intellij.grazie.utils.replaceBackslashEscapes
import com.intellij.json.JsonSchemaNameMatcher
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement

internal class JsonTextExtractor : TextExtractor() {
  override fun buildTextContent(element: PsiElement, allowedDomains: MutableSet<TextDomain>): TextContent? {
    if (element is PsiComment || element is JsonStringLiteral) {
      val domain = if (element is PsiComment) TextDomain.COMMENTS else TextDomain.LITERALS
      if (domain !in allowedDomains) return null
      if (element is JsonStringLiteral && JsonSchemaNameMatcher.matchesNameFromSchema(element)) return null
      val content = TextContentBuilder.FromPsi.build(element, domain) ?: return null
      return content.replaceBackslashEscapes()
    }
    return null
  }
}

internal class JsonProblemFilter : ProblemFilter() {
  override fun shouldIgnore(problem: TextProblem): Boolean = problem.fitsGroup(RuleGroup.CASING)
}
