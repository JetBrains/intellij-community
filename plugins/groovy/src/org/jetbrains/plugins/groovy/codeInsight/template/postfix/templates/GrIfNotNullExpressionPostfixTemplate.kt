// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.GroovyPostfixTemplateUtils

class GrIfNotNullExpressionPostfixTemplate(provider: PostfixTemplateProvider, name : String) :
  StringBasedPostfixTemplate(name, "if (expr != null)", GroovyPostfixTemplateUtils.TOP_EXPRESSION_SELECTOR, provider) {

  override fun getId(): String = "groovy.postfix.template.notnull"

  override fun getTemplateString(element: PsiElement): String {
    return "if (\$expr$ != null) {\n    \$END$\n}"
  }

  override fun getElementToRemove(expr: PsiElement): PsiElement = expr
}