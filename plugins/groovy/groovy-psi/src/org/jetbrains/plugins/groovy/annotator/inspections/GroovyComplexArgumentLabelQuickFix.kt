// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.inspections

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.utils.ParenthesesUtils.parenthesize

class GroovyComplexArgumentLabelQuickFix(element: GrExpression) : PsiUpdateModCommandAction<GrExpression>(element) {
  override fun getFamilyName(): String = GroovyBundle.message("groovy.complex.argument.label.quick.fix.message")

  override fun invoke(context: ActionContext, element: GrExpression, updater: ModPsiUpdater) {
    element.replace(parenthesize(element))
  }
}