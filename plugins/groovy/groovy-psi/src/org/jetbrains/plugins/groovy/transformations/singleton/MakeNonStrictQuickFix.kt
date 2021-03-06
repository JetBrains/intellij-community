// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.singleton

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory

internal class MakeNonStrictQuickFix : LocalQuickFix {
  @Nls
  override fun getFamilyName() = GroovyBundle.message("singleton.constructor.makeNonStrict")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val annotation = getAnnotation(descriptor.psiElement) ?: return
    val existingValue = AnnotationUtil.findDeclaredAttribute(annotation, "strict")
    val newValue = GroovyPsiElementFactory.getInstance(project)
      .createAnnotationFromText("@A(strict=false)")
      .parameterList
      .attributes[0]
    if (existingValue == null) {
      annotation.parameterList.add(newValue)
    }
    else {
      existingValue.replace(newValue)
    }
  }
}
