// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.GroovyPostfixTemplateUtils

class GrSerrExpressionPostfixTemplate(provider: PostfixTemplateProvider) :
  GrPostfixTemplateBase("serr", "System.err.println(expr)", GroovyPostfixTemplateUtils.getTopExpressionSelector(), provider) {

  override fun getGroovyTemplateString(element: PsiElement): String = "System.err.println(__expr__)__END__"
}