// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.shared

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.name.ClassId

/**
 * Checks if the given Kotlin class is accessible from the specified call site.
 *
 * @param callSite The PSI file representing the call site.
 * @param classId The identifier of the class to check accessibility for.
 * @return `true` if the class is accessible, `false` otherwise.
 */
internal fun isKotlinClassAvailable(callSite: PsiFile, classId: ClassId): Boolean {
  val module = ModuleUtilCore.findModuleForPsiElement(callSite) ?: return false
  val moduleScope = module.getModuleWithDependenciesAndLibrariesScope(/*includeTests = */true)
  val foundClasses = KotlinFullClassNameIndex[classId.asFqNameString(), module.project, moduleScope]
  return foundClasses.isNotEmpty()
}

/**
 * Checks if the Compose functionality is enabled in the module.
 * Compose functionality is enabled if the Compose annotation is available in the evaluated module's classpath.
 *
 * @param module - the [Module] which should be evaluated.
 * @return true if the Compose annotation class is found in the module's classpath; false otherwise.
 */
internal fun isComposeEnabledInModule(module: Module): Boolean {
  val moduleScope = module.getModuleWithDependenciesAndLibrariesScope(/*includeTests = */true)
  val foundClasses = KotlinFullClassNameIndex[COMPOSABLE_ANNOTATION_CLASS_ID.asFqNameString(), module.project, moduleScope]
  return foundClasses.isNotEmpty()
}

internal fun isModifierEnabledInModule(module: Module): Boolean {
  val moduleScope = module.getModuleWithDependenciesAndLibrariesScope(/*includeTests = */true)
  val foundClasses = KotlinFullClassNameIndex[COMPOSE_MODIFIER_CLASS_ID.asFqNameString(), module.project, moduleScope]
  return foundClasses.isNotEmpty()
}
