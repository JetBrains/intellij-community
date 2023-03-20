// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory

class GrReplaceMethodCallQuickFix(val oldName: String, val newName: String) : LocalQuickFix {

  override fun getName(): String = GroovyBundle.message("intention.name.replace", oldName, newName)

  override fun getFamilyName(): String = GroovyBundle.message("intention.family.name.replace.keywords")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val referenceElement = descriptor.psiElement.parentOfType<GrReferenceElement<*>>()?.referenceNameElement ?: return
    val newRefElement = GroovyPsiElementFactory.getInstance(project).createReferenceExpressionFromText(newName).referenceNameElement ?: return
    referenceElement.replace(newRefElement)
  }
}