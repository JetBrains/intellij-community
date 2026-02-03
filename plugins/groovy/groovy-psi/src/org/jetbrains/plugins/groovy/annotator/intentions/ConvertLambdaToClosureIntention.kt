// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.plugins.groovy.GroovyBundle.message
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.GrBlockLambdaBody
import org.jetbrains.plugins.groovy.lang.psi.api.GrLambdaExpression

class ConvertLambdaToClosureIntention(lambda: GrLambdaExpression) : PsiUpdateModCommandAction<GrLambdaExpression>(lambda) {
  override fun getFamilyName(): String = message("action.convert.lambda.to.closure")

  override fun invoke(context: ActionContext, lambda: GrLambdaExpression, updater: ModPsiUpdater) {
    val closureText = closureText(lambda) ?: return
    val closure = GroovyPsiElementFactory.getInstance(context.project).createClosureFromText(closureText)
    lambda.replaceWithExpression(closure, false)
  }

  private fun closureText(lambda: GrLambdaExpression): String? {
    val parameterList = lambda.parameterList
    val body = lambda.body ?: return null

    val closureText = StringBuilder()
    closureText.append("{")
    if (parameterList.parametersCount != 0) {
      appendTextBetween(closureText, parameterList.text, parameterList.lParen, parameterList.rParen)
    }
    appendElements(closureText, parameterList, body)
    if (body is GrBlockLambdaBody) {
      appendTextBetween(closureText, body.text, body.lBrace, body.rBrace)
    }
    else {
      closureText.append(body.text)
    }
    closureText.append("}")
    return closureText.toString()
  }
}
