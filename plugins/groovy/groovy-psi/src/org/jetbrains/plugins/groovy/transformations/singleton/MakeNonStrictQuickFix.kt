// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.singleton

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory

internal class MakeNonStrictQuickFix : PsiUpdateModCommandQuickFix() {
  @Nls
  override fun getFamilyName() = GroovyBundle.message("singleton.constructor.makeNonStrict")

  override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
    val annotation = getAnnotation(element) ?: return
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
