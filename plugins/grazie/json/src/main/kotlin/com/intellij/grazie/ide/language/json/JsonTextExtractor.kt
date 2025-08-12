// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language.json

import com.intellij.grazie.text.*
import com.intellij.grazie.utils.replaceBackslashEscapes
import com.intellij.json.JsonSpellcheckerStrategy.JsonSchemaSpellcheckerClientForJson
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement

class JsonTextExtractor : TextExtractor() {
  override fun buildTextContent(element: PsiElement, allowedDomains: MutableSet<TextContent.TextDomain>): TextContent? {
    if (element is JsonStringLiteral && JsonSchemaSpellcheckerClientForJson(element).matchesNameFromSchema()) return null
    if (element is PsiComment || element is JsonStringLiteral) {
      val domain = if (element is PsiComment) TextContent.TextDomain.COMMENTS else TextContent.TextDomain.LITERALS
      val content = TextContentBuilder.FromPsi.build(element, domain) ?: return null
      return content.replaceBackslashEscapes()
    }
    return null
  }
}

class JsonProblemFilter : ProblemFilter() {
  override fun shouldIgnore(problem: TextProblem): Boolean = problem.fitsGroup(RuleGroup.CASING)
}
