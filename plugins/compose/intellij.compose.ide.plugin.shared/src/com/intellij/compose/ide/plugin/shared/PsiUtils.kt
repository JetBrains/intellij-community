/*
 * Copyright (C) 2019 The Android Open Source Project
 * Modified 2025 by JetBrains s.r.o.
 * Copyright (C) 2025 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
fun isComposeEnabledForElementModule(element: PsiElement): Boolean {
  val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return false
  return isComposeEnabledInModule(module)
}

internal fun PsiElement.isComposableFunction(): Boolean =
  this is KtNamedFunction && this.hasComposableAnnotation()

internal fun KtAnnotated.hasComposableAnnotation(): Boolean =
  this.getAnnotationWithCaching(COMPOSABLE_FUNCTION_KEY) { it.isComposableAnnotation() } != null

internal val PsiElement.module: Module?
  get() = ModuleUtilCore.findModuleForPsiElement(this)

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

internal fun KtAnnotationEntry.isComposableAnnotation(): Boolean = analyze(this) {
  classIdMatches(this@isComposableAnnotation, COMPOSABLE_ANNOTATION_CLASS_ID)
}

internal fun KtAnnotationEntry.isPreviewParameterAnnotation(): Boolean = analyze(this) {
  classIdMatches(this@isPreviewParameterAnnotation, MULTIPLATFORM_PREVIEW_PARAMETER_CLASS_ID) ||
  classIdMatches(this@isPreviewParameterAnnotation, JETPACK_PREVIEW_PARAMETER_CLASS_ID)
}

internal fun KaSession.classIdMatches(element: KtAnnotationEntry, classId: ClassId): Boolean {
  val shortName = element.shortName ?: return false
  if (classId.shortClassName != shortName) return false

  val elementClassId = element.resolveToCall()?.singleConstructorCallOrNull()?.symbol?.containingClassId ?: return false
  return classId == elementClassId
}

internal fun KtDeclaration.returnTypeFqName(): FqName? =
    if (this !is KtCallableDeclaration) null
    else analyze(this) { this@returnTypeFqName.returnType.expandedSymbol?.classId?.asSingleFqName() }

internal fun KtElement.callReturnTypeFqName(): FqName? =
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