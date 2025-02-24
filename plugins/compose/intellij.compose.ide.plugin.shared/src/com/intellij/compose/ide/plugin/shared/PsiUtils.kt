// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.shared

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.roots.impl.ProjectFileIndexFacade
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.calls
import org.jetbrains.kotlin.analysis.api.resolution.singleConstructorCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList

/**
 * Determines if the given [PsiElement] element is part of a library source.
 *
 * @param element The [PsiElement] element to check.
 * @return true if the element is in a library source; false otherwise.
 */
@ApiStatus.Internal
@RequiresReadLock
fun isElementInLibrarySource(element: PsiElement): Boolean {
  val virtualFile = element.containingFile.virtualFile ?: return false

  return ProjectFileIndexFacade.getInstance(element.project)
    .isInLibrarySource(virtualFile)
}

/**
 * Checks if the Compose functionality is enabled within the module associated with the given [PsiElement].
 * Compose functionality is enabled if the Compose annotation is available in the evaluated module's classpath.
 *
 * @param element - the [PsiElement] for which the module should be evaluated.
 * @return true if the Compose annotation class is found in the module's classpath; false otherwise.
 */
@ApiStatus.Internal
fun isComposeEnabledInModule(element: PsiElement): Boolean {
  val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return false
  return isComposeEnabledInModule(module)
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

internal fun PsiElement.isComposableFunction(): Boolean =
  (this as? KtNamedFunction)?.getAnnotationWithCaching(COMPOSABLE_FUNCTION_KEY) { it.isComposableAnnotation() } != null

private val COMPOSABLE_FUNCTION_KEY: Key<CachedValue<KtAnnotationEntry?>> =
  Key.create("com.intellij.compose.ide.plugin.shared.isComposableFunction")

private fun KtAnnotated.getAnnotationWithCaching(
  key: Key<CachedValue<KtAnnotationEntry?>>,
  accept: (KtAnnotationEntry) -> Boolean
): KtAnnotationEntry? = CachedValuesManager.getCachedValue(this, key) {
  val annotationEntry = annotationEntries.firstOrNull { accept(it) }
  val containingKtFile = this.containingKtFile
  CachedValueProvider.Result.create(annotationEntry, containingKtFile, ProjectRootModificationTracker.getInstance(project))
}

private fun KtAnnotationEntry.isComposableAnnotation(): Boolean = analyze(this) {
  classIdMatches(this@isComposableAnnotation, COMPOSABLE_ANNOTATION_CLASS_ID)
}

private fun KaSession.classIdMatches(element: KtAnnotationEntry, classId: ClassId): Boolean {
  val shortName = element.shortName ?: return false
  if (classId.shortClassName != shortName) return false

  val elementClassId = element.resolveToCall()?.singleConstructorCallOrNull()?.symbol?.containingClassId ?: return false
  return classId == elementClassId
}

internal fun KtDeclaration.returnTypeFqName(): FqName? =
    if (this !is KtCallableDeclaration) null
    else analyze(this) { this@returnTypeFqName.returnType.expandedSymbol?.classId?.asSingleFqName() }

internal fun KtElement.callReturnTypeFqName() =
  analyze(this) {
    val call = resolveToCall()?.calls?.firstOrNull() as? KaCallableMemberCall<*, *>
    call?.let { it.symbol.returnType.expandedSymbol?.classId?.asSingleFqName() }
  }

internal fun KtValueArgument.matchingParamTypeFqName(callee: KtNamedFunction): FqName? {
  return if (isNamed()) {
    val argumentName = getArgumentName()!!.asName.asString()
    val matchingParam = callee.valueParameters.find { it.name == argumentName } ?: return null
    matchingParam.returnTypeFqName()
  } else {
    val argumentIndex = (parent as KtValueArgumentList).arguments.indexOf(this)
    val paramAtIndex = callee.valueParameters.getOrNull(argumentIndex) ?: return null
    paramAtIndex.returnTypeFqName()
  }
}