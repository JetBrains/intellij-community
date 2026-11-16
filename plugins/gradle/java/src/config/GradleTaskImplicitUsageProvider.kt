// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.config

import com.intellij.codeInsight.daemon.ImplicitUsageProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierList
import com.intellij.psi.util.PropertyUtilBase.findPropertyGetter
import com.intellij.psi.util.PropertyUtilBase.getPropertyName
import com.intellij.psi.util.PropertyUtilBase.isSimplePropertyGetter
import com.intellij.psi.util.PropertyUtilBase.isSimplePropertySetter
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_TASKS_ACTION

private val taskAnnotations = listOf("org.gradle.api.tasks.Input",
                                     "org.gradle.api.tasks.InputFile",
                                     "org.gradle.api.tasks.InputFiles",
                                     "org.gradle.api.tasks.InputDirectory",
                                     "org.gradle.api.tasks.OutputDirectory",
                                     "org.gradle.api.tasks.OutputDirectories",
                                     "org.gradle.api.tasks.OutputFile",
                                     "org.gradle.api.tasks.LocalState",
                                     "org.gradle.api.tasks.Destroys",
                                     "org.gradle.api.tasks.Classpath",
                                     "org.gradle.api.tasks.Console")

@ApiStatus.Internal
fun hasGradleTaskAnnotation(modifierList: PsiModifierList?): Boolean {
  return modifierList != null && taskAnnotations.any(modifierList::hasAnnotation)
}

internal class GradleTaskImplicitUsageProvider : ImplicitUsageProvider {
  override fun isImplicitUsage(element: PsiElement): Boolean {
    if (element !is PsiMethod) return false
    if (element.modifierList.hasAnnotation(GRADLE_API_TASKS_ACTION)) return true
    val getter = when {
      isSimplePropertyGetter(element) -> element
      isSimplePropertySetter(element) -> getPropertyName(element)?.let { findPropertyGetter(element.containingClass, it, false, false) }
      else -> null
    }
    return hasGradleTaskAnnotation(getter?.modifierList)
  }

  override fun isImplicitRead(element: PsiElement): Boolean = false

  override fun isImplicitWrite(element: PsiElement): Boolean = false
}
