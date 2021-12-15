// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.template.postfix

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.impl.MacroCallNode
import com.intellij.codeInsight.template.impl.TextExpression
import com.intellij.codeInsight.template.macro.SuggestVariableNameMacro
import com.intellij.codeInsight.template.postfix.templates.editable.EditablePostfixTemplateWithMultipleExpressions
import com.intellij.codeInsight.template.postfix.templates.editable.PostfixTemplateExpressionCondition
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentsOfType
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.conditions.GrIterablePostfixTemplateExpressionCondition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression

class GrForeachPostfixTemplate(provider: GroovyPostfixTemplateProvider) :
  EditablePostfixTemplateWithMultipleExpressions<PostfixTemplateExpressionCondition<out PsiElement>>(
    "groovy.postfix.template.for",
    "for",
    createTemplate("for (final def \$NAME$ in \$EXPR$) {\n    \$END$\n}"),
    "for (final e in expr)",
    setOf(GrIterablePostfixTemplateExpressionCondition()),
    true,
    provider) {

  override fun getExpressions(context: PsiElement, document: Document, offset: Int): List<PsiElement> {
    if (DumbService.getInstance(context.project).isDumb) return emptyList()
    if (myUseTopmostExpression) {
      return listOfNotNull(context.parentsOfType<GrExpression>().last())
    } else {
      return GroovyPostfixTemplateUtils.EXPRESSION_SELECTOR.getExpressions(context, document, offset)
    }
  }

  override tailrec fun getTopmostExpression(element: PsiElement): PsiElement {
    while (element.parent is GrExpression) {
      return getTopmostExpression(element.parent)
    }
    return element
  }

  override fun addTemplateVariables(element: PsiElement, template: Template) {
    val name = MacroCallNode(SuggestVariableNameMacro())
    template.addVariable("NAME", name, name, true)
    template.addVariable("EXPR", TextExpression(element.text), TextExpression(element.text), false, true)
  }
}