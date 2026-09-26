// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.openapi.components.Service
import com.intellij.psi.PsiElement
import org.jetbrains.idea.devkit.inspections.getProjectLevelFQN
import org.jetbrains.idea.devkit.inspections.quickfix.AddServiceAnnotationProvider
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.idea.util.addAnnotation
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtClass

internal class KotlinAddServiceAnnotationProvider : AddServiceAnnotationProvider {
  override fun addServiceAnnotation(aClass: PsiElement, level: Service.Level) {
    val ktClass = when (aClass) {
      is KtClass -> aClass
      is KtLightClass -> aClass.kotlinOrigin
      else -> null
    } ?: return
    val annotationInnerText = when (level) {
      Service.Level.APP -> null
      Service.Level.PROJECT -> getProjectLevelFQN()
    }
    ktClass.addAnnotation(
      annotationClassId = ClassId.fromString(Service::class.java.canonicalName),
      annotationInnerText = annotationInnerText,
      searchForExistingEntry = false,
    )
  }
}