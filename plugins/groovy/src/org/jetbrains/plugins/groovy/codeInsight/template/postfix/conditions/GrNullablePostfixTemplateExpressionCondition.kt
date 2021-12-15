// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.template.postfix.conditions

import com.intellij.codeInsight.template.postfix.templates.editable.PostfixTemplateExpressionCondition
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPrimitiveType
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression

class GrNullablePostfixTemplateExpressionCondition : PostfixTemplateExpressionCondition<PsiElement> {
  override fun getPresentableName(): String = GroovyBundle.message("postfix.template.nullable.expression")

  override fun getId(): String = "groovy.expression.condition.nullable"

  override fun value(t: PsiElement): Boolean = t is GrExpression && t.type !is PsiPrimitiveType
}