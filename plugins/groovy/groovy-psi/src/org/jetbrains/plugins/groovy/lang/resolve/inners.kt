// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("LoopToCallChain", "UseExpressionBody", "LiftReturnOrAssignment")

package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.impl.PsiFileEx
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.util.SmartList
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.util.GrClassImplUtil
import org.jetbrains.plugins.groovy.lang.resolve.caches.getInnersHierarchyCache
import org.jetbrains.plugins.groovy.lang.resolve.caches.getInnersOutersCache

fun GrTypeDefinition.processInnerInHierarchy(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean {
  if (useCaches(place)) {
    return getInnersHierarchyCache().processDeclarations(processor, state, place)
  }
  else {
    return processInnersInHierarchyNoCache(processor, state, place)
  }
}

fun GrTypeDefinition.processInnersInOuters(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean {
  if (useCaches(place)) {
    return getInnersOutersCache().processDeclarations(processor, state, place)
  }
  else {
    return processInnersInOutersNoCache(processor, state, place)
  }
}

internal fun GrTypeDefinition.processInnersInHierarchyNoCache(processor: PsiScopeProcessor,
                                                              state: ResolveState,
                                                              place: PsiElement): Boolean {
  if (!doProcessInnersInClassAndInterfaces(processor, state, place)) return false

  var superClass: PsiClass? = GrClassImplUtil.getSuperClass(this, getExtendsListTypes(false))
  while (superClass != null) {
    if (!superClass.doProcessInnerClasses(processor, state)) return false
    if (!superClass.doProcessInterfaces(processor, state, place)) return false
    superClass = superClass.superClass
  }
  return true
}

private fun GrTypeDefinition.doProcessInnersInClassAndInterfaces(processor: PsiScopeProcessor,
                                                                 state: ResolveState,
                                                                 place: PsiElement): Boolean {
  return doProcessOwnInnerClasses(processor, state) &&
         doProcessOwnInterfaces(processor, state, place)
}

private fun GrTypeDefinition.doProcessOwnInterfaces(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean {
  for (anInterface in GrClassImplUtil.getInterfaces(this, false)) {
    if (!anInterface.processDeclarations(processor, state, null, place)) return false
  }
  return true
}

private fun GrTypeDefinition.doProcessOwnInnerClasses(processor: PsiScopeProcessor, state: ResolveState): Boolean {
  val name = processor.getName(state)
  if (name == null) {
    for (inner in codeInnerClasses) {
      if (!processor.execute(inner, state)) return false
    }
  }
  else {
    for (inner in codeInnerClasses) {
      if (inner.name != name) continue
      if (!processor.execute(inner, state)) return false
    }
  }
  return true
}

private fun PsiClass.doProcessInnerClasses(processor: PsiScopeProcessor, state: ResolveState): Boolean {
  val name = processor.getName(state)
  if (name == null) {
    for (inner in innerClasses) {
      if (!processor.execute(inner, state)) return false
    }
  }
  else {
    val inner = findInnerClassByName(name, false)
    if (inner != null) {
      if (!processor.execute(inner, state)) return false
    }
  }
  return true
}


private fun PsiClass.doProcessInterfaces(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean {
  for (anInterface in interfaces) {
    if (!anInterface.processDeclarations(processor, state, null, place)) return false
  }
  return true
}

internal fun GrTypeDefinition.processInnersInOutersNoCache(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean {
  val outers = collectOuterClasses().reversed()
  for (outer in outers) {
    if (!outer.doProcessInnersInClassAndInterfaces(processor, state, place)) return false
  }
  return true
}

private fun GrTypeDefinition.collectOuterClasses(): List<GrTypeDefinition> {
  val result = SmartList<GrTypeDefinition>()
  var current: GrTypeDefinition? = containingClass as? GrTypeDefinition
  while (current != null) {
    result += current
    current = current.containingClass as? GrTypeDefinition
  }
  return result
}

private fun useCaches(place: PsiElement): Boolean {
  if (place.parent is GrAnnotation) return false
  if (ApplicationManager.getApplication().isDispatchThread) return false
  val containingFile = place.containingFile
  if (containingFile.originalFile !== containingFile) return false
  return containingFile.getUserData(PsiFileEx.BATCH_REFERENCE_PROCESSING) == true
}
