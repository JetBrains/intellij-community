// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.plugins.groovy.GroovyBundle.message
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.GrLambdaExpression

class AddParenthesesToLambdaParameterIntention(parameterList: GrLambdaExpression) : 
  PsiUpdateModCommandAction<GrLambdaExpression>(parameterList) {

  override fun getFamilyName(): String = message("add.parenthesis.to.lambda.parameter.list")

  override fun getPresentation(context: ActionContext, lambda: GrLambdaExpression): Presentation? {
    val parameterList = lambda.parameterList
    parameterList.lParen ?: return Presentation.of(familyName)
    return null
  }

  override fun invoke(context: ActionContext, lambda: GrLambdaExpression, updater: ModPsiUpdater) {
    val closureText = closureText(lambda)
    val closure = GroovyPsiElementFactory.getInstance(context.project).createLambdaFromText(closureText)
    lambda.replaceWithExpression(closure, false)
  }

  private fun closureText(lambda: GrLambdaExpression): String {
    val closureText = StringBuilder()
    closureText.append("(")
    val parameterList = lambda.parameterList
    appendTextBetween(closureText, parameterList.text, parameterList.lParen, parameterList.rParen)
    closureText.append(")")
    appendTextBetween(closureText, lambda.text, parameterList, null)

    return closureText.toString()
  }
}
