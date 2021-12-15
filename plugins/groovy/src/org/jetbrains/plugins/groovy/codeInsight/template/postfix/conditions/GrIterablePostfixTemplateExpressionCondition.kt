// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.template.postfix.conditions

import com.intellij.codeInsight.template.postfix.templates.editable.PostfixTemplateExpressionCondition
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiElement
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.GrLiteralClassType

class GrIterablePostfixTemplateExpressionCondition : PostfixTemplateExpressionCondition<PsiElement> {
  override fun getPresentableName(): String = GroovyBundle.message("postfix.template.iterable")

  override fun getId(): String = "groovy.expression.condition.iterable"

  override fun value(t: PsiElement): Boolean {
    if (t !is GrExpression) return false
    val type = t.type
    return type == null || // unknown type may be actually iterable in runtime
           type is GrLiteralClassType ||
           type is PsiArrayType ||
           InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_ITERABLE)
  }
}