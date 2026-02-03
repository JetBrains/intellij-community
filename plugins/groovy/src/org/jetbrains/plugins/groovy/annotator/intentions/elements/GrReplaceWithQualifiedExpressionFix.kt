// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.intentions.elements

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall

class GrReplaceWithQualifiedExpressionFix : PsiUpdateModCommandQuickFix() {
  override fun getFamilyName(): String {
    return GroovyBundle.message("intention.family.name.rename")
  }

  override fun getName(): String {
    return GroovyBundle.message("intention.name.replace.with.qualified.expression")
  }

  override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
    if (element !is GrMethodCall) return
    val container = element.resolveMethod()?.containingClass ?: return
    val factory = GroovyPsiElementFactory.getInstance(project)
    val newInvoked = factory.createExpressionFromText("${container.name}.${element.invokedExpression.text}", element)
    element.invokedExpression.replace(newInvoked)
  }
}