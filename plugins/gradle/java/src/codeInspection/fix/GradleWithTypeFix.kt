// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection.fix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall

class GradleWithTypeFix : LocalQuickFix {
  override fun getFamilyName(): String {
    return GroovyBundle.message("intention.family.name.replace.keywords")
  }

  override fun getName(): String {
    return GradleInspectionBundle.message("intention.name.add.configure.each")
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val methodCall: GrMethodCall = descriptor.psiElement.parentOfType() ?: return
    val template = if (!methodCall.isReturnTypeValueUsed()) {
      "%s.configureEach%s"
    } else {
      "%s.tap { configureEach%s }"
    }
    val closure = deleteSecondArgument(methodCall) ?: return
    val newParameterList = getNewParameterList(closure)
    val representation = template.format(methodCall.text, newParameterList)
    val newCall = GroovyPsiElementFactory.getInstance(methodCall.project).createExpressionFromText(representation) as GrMethodCall
    methodCall.replace(newCall)
  }

  private fun getNewParameterList(closure: PsiElement): String {
    if (closure is GrClosableBlock) {
      return " ${closure.text}"
    } else {
      return "(${closure.text})"
    }
  }

}