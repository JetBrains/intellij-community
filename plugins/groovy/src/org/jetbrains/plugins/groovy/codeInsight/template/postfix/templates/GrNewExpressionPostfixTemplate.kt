// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.GroovyPostfixTemplateUtils
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression

class GrNewExpressionPostfixTemplate(provider: PostfixTemplateProvider) :
  GrPostfixTemplateBase("new", "new Expr()", GroovyPostfixTemplateUtils.CONSTRUCTOR_SELECTOR, provider) {

  override fun getGroovyTemplateString(element: PsiElement): String = if (element is GrCallExpression) {
    "new __expr____END__"
  }
  else {
    "new __expr__(__END__)"
  }

}