// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.annotation.JvmAnnotationArrayValue
import com.intellij.lang.jvm.annotation.JvmAnnotationAttributeValue
import com.intellij.lang.jvm.annotation.JvmAnnotationEnumFieldValue
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.util.processExtensionsByClassName
import java.util.concurrent.atomic.AtomicBoolean


private const val APPLICATION_SERVICE_FQN = "com.intellij.applicationService"

class ApplicationServiceAsStaticFinalFieldInspection : AbstractBaseJavaLocalInspectionTool() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (!DevKitInspectionUtil.isAllowed(holder.file)) return PsiElementVisitor.EMPTY_VISITOR

    return object : JavaElementVisitor() {

      override fun visitField(field: PsiField) {
        if (!(field.hasModifier(JvmModifier.STATIC) && field.hasModifier(JvmModifier.FINAL))) return

        val fieldTypeClass = PsiTypesUtil.getPsiClass(field.type) ?: return
        val classFqn = fieldTypeClass.qualifiedName ?: return

        if (isLightApplicationService(fieldTypeClass) || isRegisteredApplicationService(holder.project, classFqn)) {
          val typeElement = field.typeElement ?: return
          holder.registerProblem(typeElement, DevKitBundle.message("inspections.application.service.as.static.final.field.message"))
        }
      }

    }
  }

  private fun isLightApplicationService(psiClass: PsiClass): Boolean {
    fun JvmAnnotationAttributeValue.asSet(): Set<JvmAnnotationEnumFieldValue> {
      return when (this) {
        is JvmAnnotationEnumFieldValue -> setOf(this)
        is JvmAnnotationArrayValue -> this.values.mapNotNull { it as? JvmAnnotationEnumFieldValue }.toSet()
        else -> emptySet()
      }
    }

    val serviceAnnotation = psiClass.getAnnotation(Service::class.java.canonicalName) ?: return false
    val annotationAttributeValues = serviceAnnotation
                                      .findAttribute(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME)
                                      ?.attributeValue
                                      ?.asSet() ?: emptySet()

    // If there is only Service.Level.PROJECT in attributes, then it's a project service
    // Any other case (empty attributes, only Service.Level.APP,
    // both Service.Level.PROJECT and Service.Level.APP) -- it's an application service
    val isProjectLevel = annotationAttributeValues.size == 1 && Service.Level.PROJECT.name in annotationAttributeValues.map { it.fieldName }
    return !isProjectLevel
  }


  private fun isRegisteredApplicationService(project: Project, className: String): Boolean {
    val foundApplicationService = AtomicBoolean(false)
    processExtensionsByClassName(project, className) { _, ep ->
      val hasServiceFqn = ep.effectiveQualifiedName == APPLICATION_SERVICE_FQN
      foundApplicationService.set(hasServiceFqn)
      !hasServiceFqn
    }
    return foundApplicationService.get()
  }

}
