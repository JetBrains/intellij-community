// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.impl.MacroCallNode
import com.intellij.codeInsight.template.macro.SuggestVariableNameMacro
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.GroovyPostfixTemplateUtils

class GrArgPostfixTemplate(provider: PostfixTemplateProvider) :
  GrPostfixTemplateBase("arg", "functionCall(expr)", GroovyPostfixTemplateUtils.getExpressionSelector(), provider) {

  override fun getGroovyTemplateString(element: PsiElement): String = "__call__(__expr__)__END__"

  override fun setVariables(template: Template, element: PsiElement) {
    val name = MacroCallNode(SuggestVariableNameMacro())
    template.addVariable("call", name, name, true)
  }
}