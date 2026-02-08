// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.inspections

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.stubindex.KotlinClassShortNameIndex
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import java.util.LinkedList
import java.util.function.Predicate

private val workspaceModelClasses: List<String> = listOfNotNull(
  WorkspaceEntity::class.qualifiedName,
  EntitySource::class.qualifiedName,
  SymbolicEntityId::class.qualifiedName,
  WorkspaceEntity.Builder::class.qualifiedName,
  WorkspaceEntityBase::class.qualifiedName
)

/**
 * Finds which [workspaceModelClasses] this KtClassOrObject inherits and caches the result.
 *
 * @return set of parent classes' fully qualified names
 */
internal fun KtClassOrObject.getWorkspaceSupers(): Set<String> {
  return CachedValuesManager.getCachedValue(this) {
    val workspaceSupersSequence = getMatchingSuperTypes { ktClass ->
      workspaceModelClasses.contains(ktClass.fqName?.asString())
    }
    val workspaceSupers = workspaceSupersSequence.mapNotNull { it.fqName?.asString() }.toSet()
    CachedValueProvider.Result(workspaceSupers, PsiModificationTracker.MODIFICATION_COUNT)
  }
}

internal fun KtTypeReference.resolveToKtClass(): KtClass? {
  val resolvedReference = (typeElement as? KtUserType)?.referenceExpression?.mainReference?.resolve()
  return when (resolvedReference) {
    is KtClass -> resolvedReference
    is KtConstructor<*> -> resolvedReference.containingClass()
    else -> null
  }
}

internal fun KtClassOrObject.getMatchingSuperTypes(predicate: Predicate<KtClass>): Sequence<KtClass> = sequence {
  val visited = mutableSetOf<KtClass>()
  val superTypeList = LinkedList<KtSuperTypeListEntry>()
  superTypeList.addAll(superTypeListEntries)
  while (!superTypeList.isEmpty()) {
    val superType = superTypeList.pop()
    val resolvedKtClass = superType.typeReference?.resolveToKtClass() ?: continue
    if (!visited.add(resolvedKtClass)) continue
    if (predicate.test(resolvedKtClass)) yield(resolvedKtClass)
    resolvedKtClass.superTypeListEntries.forEach { superTypeList.push(it) }
  }
}

internal fun findWorkspaceEntityImplementation(entityClass: KtClass, searchScope: GlobalSearchScope): KtClass? {
  val thisFqName = entityClass.fqName?.asString() ?: return null
  val foundImplClasses = KotlinClassShortNameIndex["${entityClass.name}Impl", entityClass.project, searchScope]
  val foundEntityImpls = foundImplClasses.filter { it is KtClass && it.isWorkspaceEntityImplementation() }
  val foundImpl = foundEntityImpls.find { someImpl -> someImpl.getMatchingSuperTypes { it.fqName?.asString() == thisFqName }.any() }
  return foundImpl as? KtClass
}

internal fun KtClass.isWorkspaceEntity(): Boolean {
  return getWorkspaceSupers().contains(WorkspaceEntity::class.qualifiedName)
}

/**
 * Check that a class is an **interface** that extends WorkspaceEntity, but not WorkspaceEntity.Builder, meaning that it is a declaration of
 * an entity.
 */
internal fun KtClass.isWorkspaceEntityDeclaration(): Boolean {
  if (!isInterface()) return false
  val workspaceSupers = getWorkspaceSupers()
  return workspaceSupers.contains(WorkspaceEntity::class.qualifiedName) &&
         !workspaceSupers.contains(WorkspaceEntity.Builder::class.qualifiedName)
}

internal fun KtClass.isWorkspaceEntityImplementation(): Boolean {
  val workspaceSupers = getWorkspaceSupers()
  return workspaceSupers.contains(WorkspaceEntity::class.qualifiedName) && workspaceSupers.contains(WorkspaceEntityBase::class.qualifiedName)
}

internal fun KtClassOrObject.isWorkspaceEntitySource(): Boolean {
  return getWorkspaceSupers().contains(EntitySource::class.qualifiedName)
}

internal fun KtClass.findAnnotation(annotationFqName: String): KtAnnotationEntry? {
  for (annotationEntry in annotationEntries) {
    val annotationClass = annotationEntry.typeReference?.resolveToKtClass() ?: continue
    if (annotationClass.fqName?.asString() == annotationFqName) return annotationEntry
  }
  return null
}

internal fun KtClass.isAbstractEntity(): Boolean {
  val annotationFqName = Abstract::class.qualifiedName!!
  return findAnnotation(annotationFqName) != null
}

internal fun KtClassOrObject.getPsiElementForHighlighting(): PsiElement? = nameIdentifier ?: firstChild