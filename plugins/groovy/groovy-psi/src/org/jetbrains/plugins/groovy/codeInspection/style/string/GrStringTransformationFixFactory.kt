// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.style.string

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringContent
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil
import org.jetbrains.plugins.groovy.lang.psi.util.StringKind


private val FIXES = StringKind.values().map { it to createStringTransformationFix(it) }.toMap()

fun getStringTransformationFix(targetKind: StringKind): GroovyFix = FIXES[targetKind]!!

private fun getCurrentKind(quote: String): StringKind? = when (quote) {
  GrStringUtil.DOLLAR_SLASH -> StringKind.DOLLAR_SLASHY
  GrStringUtil.SLASH -> StringKind.SLASHY
  GrStringUtil.DOUBLE_QUOTES -> StringKind.DOUBLE_QUOTED
  GrStringUtil.TRIPLE_QUOTES -> StringKind.TRIPLE_SINGLE_QUOTED
  GrStringUtil.TRIPLE_DOUBLE_QUOTES -> StringKind.TRIPLE_DOUBLE_QUOTED
  GrStringUtil.QUOTE -> StringKind.SINGLE_QUOTED
  else -> null
}

private fun getQuoteByKind(kind: StringKind): String = when (kind) {
  StringKind.SINGLE_QUOTED -> GrStringUtil.QUOTE
  StringKind.TRIPLE_SINGLE_QUOTED -> GrStringUtil.TRIPLE_QUOTES
  StringKind.DOUBLE_QUOTED -> GrStringUtil.DOUBLE_QUOTES
  StringKind.TRIPLE_DOUBLE_QUOTED -> GrStringUtil.TRIPLE_DOUBLE_QUOTES
  StringKind.SLASHY -> GrStringUtil.SLASH
  StringKind.DOLLAR_SLASHY -> GrStringUtil.DOLLAR_SLASH
}

private fun createStringTransformationFix(targetKind: StringKind): GroovyFix = object : GroovyFix() {
  override fun getFamilyName(): String {
    return GroovyBundle.message("intention.family.name.fix.quotation")
  }

  override fun getName(): String = when (targetKind) {
    StringKind.SINGLE_QUOTED -> GroovyBundle.message("intention.name.convert.to.single.quoted.string")
    StringKind.TRIPLE_SINGLE_QUOTED -> GroovyBundle.message("intention.name.change.quotes.to.triple.single.quotes")
    StringKind.DOUBLE_QUOTED -> GroovyBundle.message("intention.name.convert.to.double.quoted.string")
    StringKind.TRIPLE_DOUBLE_QUOTED -> GroovyBundle.message("intention.name.change.quotes.to.triple.double.quotes")
    StringKind.SLASHY -> GroovyBundle.message("intention.name.convert.to.slashy.string")
    StringKind.DOLLAR_SLASHY -> GroovyBundle.message("intention.name.convert.to.dollar.slashy.string")
  }

  private fun escapeTextPart(innerKind: StringKind, text: String): String =
    targetKind.escape(innerKind.unescape(text))

  private fun getNewText(literal: GrLiteral): String {
    val literalText = literal.text
    val quote = GrStringUtil.getStartQuote(literalText)
    val kind = getCurrentKind(quote) ?: return literalText
    return if (literal is GrString) {
      literal.allContentParts.joinToString("") { if (it is GrStringContent) escapeTextPart(kind, it.text) else it.text }
    }
    else {
      escapeTextPart(kind, GrStringUtil.removeQuotes(literalText))
    }
  }

  override fun doFix(project: Project, descriptor: ProblemDescriptor) {
    val literal = descriptor.psiElement.parentOfType<GrLiteral>(true) ?: return
    val newText = getNewText(literal)
    val newQuote = getQuoteByKind(targetKind)
    val endQuote = if (newQuote == GrStringUtil.DOLLAR_SLASH) GrStringUtil.SLASH_DOLLAR else newQuote
    val newExpression = GroovyPsiElementFactory.getInstance(project).createExpressionFromText("$newQuote$newText$endQuote")
    literal.replaceWithExpression(newExpression, true)
  }
}
