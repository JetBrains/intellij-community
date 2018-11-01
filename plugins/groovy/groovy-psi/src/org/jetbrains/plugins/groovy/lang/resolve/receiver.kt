// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.*
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTraitType
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.processClassDeclarations
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.processNonCodeMembers
import org.jetbrains.plugins.groovy.lang.resolve.impl.GroovyMapPropertyImpl
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint.STATIC_CONTEXT

fun PsiType?.processReceiverType(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean {
  if (this == null) return true
  if (!doProcessReceiverType(processor, state, place)) return false
  return !state.processNonCodeMembers() || processNonCodeMembers(this, processor, place, state)
}

private fun PsiType.doProcessReceiverType(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean {
  return when (this) {
    is PsiPrimitiveType -> getBoxedType(place)?.processReceiverType(processor, state, place) ?: true
    is PsiDisjunctionType -> leastUpperBound.processReceiverType(processor, state, place)
    is PsiIntersectionType -> conjuncts.all { it.processReceiverType(processor, state, place) }
    is PsiCapturedWildcardType -> wildcard.processReceiverType(processor, state, place)
    is PsiWildcardType -> !isExtends || extendsBound.processReceiverType(processor, state, place)
    is GrTraitType -> conjuncts.reversed().all {
      // Process trait type conjuncts in reversed order because last applied trait matters
      it.processReceiverType(processor, state, place)
    }
    is PsiArrayType -> {
      val arrayClass = GroovyPsiManager.getInstance(place.project).getArrayClass(componentType) ?: return true
      processClassDeclarations(arrayClass, processor, state, null, place)
    }
    is PsiClassType -> processClassType(processor, state, place)
    else -> return true
  }
}

private fun PsiClassType.processClassType(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean {
  val result = resolveGenerics()
  val clazz = result.element ?: return true
  val newState = state.put(PsiSubstitutor.KEY, state[PsiSubstitutor.KEY].putAll(result.substitutor))
  return processMapType(processor, newState, place) &&
         processClassDeclarations(clazz, processor, newState, null, place)
}

private fun PsiClassType.processMapType(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean {
  if (state[STATIC_CONTEXT] == true) {
    return true
  }
  if (!processor.shouldProcessProperties()) {
    return true
  }
  if (this !is GrMapType && !InheritanceUtil.isInheritor(this, CommonClassNames.JAVA_UTIL_MAP)) {
    return true
  }
  val name = processor.getName(state)
  if (name != null) {
    val property = GroovyMapPropertyImpl(this, name, place)
    if (!processor.execute(property, state)) {
      return false
    }
  }
  if (!processor.shouldProcessMethods()) {
    return false
  }
  return true
}
