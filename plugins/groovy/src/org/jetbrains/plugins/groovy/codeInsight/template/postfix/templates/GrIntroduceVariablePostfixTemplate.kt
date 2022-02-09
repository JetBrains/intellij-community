// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateWithExpressionSelector
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.GroovyPostfixTemplateUtils
import org.jetbrains.plugins.groovy.refactoring.introduce.variable.GrIntroduceVariableHandler

class GrIntroduceVariablePostfixTemplate(sequence: String, provider: PostfixTemplateProvider)
  : PostfixTemplateWithExpressionSelector(
  "groovy.postfix.template.$sequence",
  sequence,
  "def name = expr",
  GroovyPostfixTemplateUtils.getExpressionSelector(),
  provider) {


  override fun expandForChooseExpression(expression: PsiElement, editor: Editor) {
    val handler = GrIntroduceVariableHandler()
    handler.invoke(expression.project, editor, expression.containingFile, null)
  }
}