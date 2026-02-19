// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.GroovyPostfixTemplateUtils
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.GroovyPostfixTemplateUtils.shouldBeParenthesized
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression

class GrNegateBooleanPostfixTemplate(sequence: String, provider: PostfixTemplateProvider) :
  GrPostfixTemplateBase(sequence, "!expr", GroovyPostfixTemplateUtils.getBooleanExpressionSelector(), provider) {

  override fun getGroovyTemplateString(element: PsiElement): String {
    element as GrExpression
    val elementText = element.text.let { if (shouldBeParenthesized(element)) "($it)" else it }
    return "!$elementText"
  }

}