// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.conversions.strings

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil

class ConvertToDollarSlashRegexIntention : GrPsiUpdateIntention() {

  override fun processIntention(element: PsiElement, context: ActionContext, updater: ModPsiUpdater) {
    if (element !is GrLiteral) return
    val value = element.value as? String ?: return
    val factory = GroovyPsiElementFactory.getInstance(context.project)
    val newEmptyLiteral = factory.createExpressionFromText("$//$") as? GrLiteral ?: return
    val newLiteral = ElementManipulators.handleContentChange(newEmptyLiteral, value) ?: return
    element.replace(newLiteral)
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
