// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.intentions.elements

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.util.asSafely
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall

class GrReplaceWithQualifiedExpressionFix : GroovyFix() {
  override fun getFamilyName(): String {
    return GroovyBundle.message("intention.family.name.rename")
  }

  override fun getName(): String {
    return GroovyBundle.message("intention.name.replace.with.qualified.expression")
  }

  override fun doFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement?.asSafely<GrMethodCall>() ?: return
    val container = element.resolveMethod()?.containingClass ?: return
    val factory = GroovyPsiElementFactory.getInstance(project)
    val newInvoked = factory.createExpressionFromText("${container.name}.${element.invokedExpression.text}", element)
    element.invokedExpression.replace(newInvoked)
  }
}