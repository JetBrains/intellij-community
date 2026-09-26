// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.cs

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_TRANSFORM_COMPILE_STATIC
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_TRANSFORM_STC_POJO
import org.jetbrains.plugins.groovy.lang.psi.util.getPOJO

class GrPOJOInspection : BaseInspection() {
  override fun buildVisitor() = object : BaseInspectionVisitor() {
    override fun visitTypeDefinition(typeDefinition: GrTypeDefinition) {
      val actualPojo = typeDefinition.getAnnotation(GROOVY_TRANSFORM_STC_POJO)?.takeIf { it !is LightElement }?: return
      val enabledPojo = getPOJO(typeDefinition)
      if (enabledPojo == null) {
        registerError(actualPojo, GENERIC_ERROR_OR_WARNING)
      }
    }
  }

  override fun buildFix(location: PsiElement): LocalQuickFix? {
    val containingClass = location.parentOfType<GrTypeDefinition>() ?: return null
    return object : LocalQuickFixOnPsiElement(containingClass) {
      override fun getFamilyName(): String = GroovyBundle.message("intention.family.name.replace.modifiers")

      override fun getText(): String = GroovyBundle.message("add.compilestatic")

      override fun invoke(project: Project, psiFile: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        val clazz = startElement as? GrTypeDefinition
        clazz?.modifierList?.addAnnotation(GROOVY_TRANSFORM_COMPILE_STATIC)
      }
    }
  }

  override fun buildErrorString(vararg args: Any?): String {
    return GroovyBundle.message("inspection.message.pojo.has.effect.only.with.compilestatic")
  }
}