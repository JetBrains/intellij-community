// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.conversions.strings

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil

class ConvertToDollarSlashRegexIntention : GrPsiUpdateIntention() {

  override fun processIntention(element: PsiElement, context: ActionContext, updater: ModPsiUpdater) {
    if (element !is GrLiteral) return
    val buffer = StringBuilder(GrStringUtil.DOLLAR_SLASH)

    if (element is GrLiteralImpl) {
      val value = element.value as? String
                  ?: GrStringUtil.unescapeString(GrStringUtil.removeQuotes(element.text))
      GrStringUtil.escapeSymbolsForDollarSlashyStrings(buffer, value)
    }
    else if (element is GrString) {
      val slashy = GrStringUtil.isSlashyString(element)
      element.allContentParts.forEach { part ->
        if (part is GrStringInjection) buffer.append(part.text)
        else {
          val unescaped = if (slashy) GrStringUtil.unescapeSlashyString(part.text)
                          else GrStringUtil.unescapeString(part.text)
          GrStringUtil.escapeSymbolsForDollarSlashyStrings(buffer, unescaped)
        }
      }
    }

    buffer.append(GrStringUtil.SLASH_DOLLAR)
    val factory = GroovyPsiElementFactory.getInstance(context.project)
    element.replace(factory.createExpressionFromText(buffer.toString()))
  }

  override fun getElementPredicate(): PsiElementPredicate = IntentionPredicate

  private object IntentionPredicate : PsiElementPredicate {

    override fun satisfiedBy(element: PsiElement): Boolean {
      return element is GrLiteral &&
             GrStringUtil.isStringLiteral(element) &&
             !GrStringUtil.isDollarSlashyString(element)
    }
  }
}
