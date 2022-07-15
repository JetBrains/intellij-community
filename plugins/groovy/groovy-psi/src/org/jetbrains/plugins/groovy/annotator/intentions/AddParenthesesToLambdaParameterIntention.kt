// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.intentions

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.createSmartPointer
import org.jetbrains.plugins.groovy.GroovyBundle.message
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.GrLambdaExpression

class AddParenthesesToLambdaParameterIntention(parameterList: GrLambdaExpression) : IntentionAction {

  private val myLambda: SmartPsiElementPointer<GrLambdaExpression> = parameterList.createSmartPointer()

  override fun getText(): String = familyName

  override fun getFamilyName(): String = message("add.parenthesis.to.lambda.parameter.list")

  override fun startInWriteAction(): Boolean = true

  override fun getFileModifierForPreview(target: PsiFile): FileModifier? {
    val originalParameterList = PsiTreeUtil.findSameElementInCopy(myLambda.element, target) ?: return null
    return AddParenthesesToLambdaParameterIntention(originalParameterList)
  }

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    val parameterList = myLambda.element?.parameterList ?: return false
    parameterList.lParen ?: return true
    return false
  }

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    val lambda = myLambda.element ?: return
    val closureText = closureText(lambda)
    val closure = GroovyPsiElementFactory.getInstance(project).createLambdaFromText(closureText)
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
