// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.GroovyPostfixTemplateUtils

class GrIfPostfixTemplate(provider: PostfixTemplateProvider) :
  GrPostfixTemplateBase("if", "if (expr)", GroovyPostfixTemplateUtils.getTopBooleanExpressionSelector(), provider) {

  override fun getGroovyTemplateString(element: PsiElement): String
  = """if (__expr__) {
    __END__
}"""

}