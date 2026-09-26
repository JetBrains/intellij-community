// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.impl.MacroCallNode
import com.intellij.codeInsight.template.macro.SuggestVariableNameMacro
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.GroovyPostfixTemplateProvider
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.GroovyPostfixTemplateUtils

class GrForeachPostfixTemplate(sequence: String, provider: GroovyPostfixTemplateProvider) :
  GrPostfixTemplateBase(sequence, "for (final e in expr)", GroovyPostfixTemplateUtils.getIterableExpressionSelector(), provider) {

  override fun getGroovyTemplateString(element: PsiElement): String =
    """for (final def __NAME__ in __expr__) {
    __END__
}"""

  override fun setVariables(template: Template, element: PsiElement) {
    val name = MacroCallNode(SuggestVariableNameMacro())
    template.addVariable("NAME", name, name, true)
  }
}