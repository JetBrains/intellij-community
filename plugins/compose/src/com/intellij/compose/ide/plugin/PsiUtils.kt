// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin

import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleConstructorCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtNamedFunction

internal fun PsiElement.isComposableFunction(): Boolean =
  (this as? KtNamedFunction)?.getAnnotationWithCaching(COMPOSABLE_FUNCTION_KEY) { it.isComposableAnnotation() } != null

private val COMPOSABLE_FUNCTION_KEY: Key<CachedValue<KtAnnotationEntry?>> =
  Key.create("com.intellij.compose.ide.plugin.isComposableFunction")

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
