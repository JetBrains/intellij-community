// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.conversions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.GrArrayInitializer
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression

class ConvertJavaStyleArrayIntention : GrPsiUpdateIntention() {

  companion object : PsiElementPredicate {
    override fun satisfiedBy(element: PsiElement): Boolean {
      return element is GrNewExpression &&
             element.arrayInitializer.let { it != null && it.rBrace != null }
    }
  }

  override fun getElementPredicate(): PsiElementPredicate = ConvertJavaStyleArrayIntention

  override fun processIntention(element: PsiElement, context: ActionContext, updater: ModPsiUpdater) {
    val expression = element as? GrNewExpression ?: return
    val initializer = expression.arrayInitializer ?: return
    val newText = convertArrayInitializers(initializer)
    val newExpression = GroovyPsiElementFactory.getInstance(context.project).createExpressionFromText(newText)
    expression.replaceWithExpression(newExpression, true)
  }

  private fun convertArrayInitializers(initializer: GrExpression): String {
    if (initializer !is GrArrayInitializer) return initializer.text
    val expressionTexts = initializer.expressions.map { convertArrayInitializers(it) }
    val postfix = if(endsWithTrailingComma(initializer)) ",]" else "]"
    return expressionTexts.joinToString(", ", "[", postfix)
  }

  private fun endsWithTrailingComma(initializer: GrArrayInitializer): Boolean {
    val rBrace = initializer.rBrace ?: return false
    val candidate = PsiTreeUtil.skipWhitespacesBackward(rBrace)
    return candidate != null && candidate.elementType == GroovyTokenTypes.mCOMMA
  }
}
