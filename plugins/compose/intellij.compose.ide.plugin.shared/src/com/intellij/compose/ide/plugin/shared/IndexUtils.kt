package com.intellij.compose.ide.plugin.shared

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
