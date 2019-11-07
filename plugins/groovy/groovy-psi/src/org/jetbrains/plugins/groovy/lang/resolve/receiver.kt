// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.*
import com.intellij.psi.CommonClassNames.JAVA_LANG_CLASS
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTraitType
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.processClassDeclarations
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.processNonCodeMembers
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument
import org.jetbrains.plugins.groovy.lang.resolve.api.JustTypeArgument
import org.jetbrains.plugins.groovy.lang.resolve.impl.GroovyMapPropertyImpl
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint.STATIC_CONTEXT

fun Argument.processReceiver(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean {
  val receiverType: PsiType = topLevelType ?: TypesUtil.getJavaLangObject(place) ?: return true
  return receiverType.doProcessReceiverType0(processor, state.put(ClassHint.RECEIVER, this), place)
}

fun PsiType?.processReceiverType(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean {
  if (this == null) return true
  return doProcessReceiverType0(processor, state.put(ClassHint.RECEIVER, JustTypeArgument(this)), place)
}

private fun PsiType.doProcessReceiverType0(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean {
  if (!doProcessReceiverType(processor, state, place)) return false
  return !state.processNonCodeMembers() || processNonCodeMembers(this, processor, place, state)
}

private fun PsiType.doProcessReceiverType(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean {
  return when (this) {
    is PsiPrimitiveType -> getBoxedType(place)?.processReceiverType(processor, state, place) ?: true
    is PsiDisjunctionType -> leastUpperBound.processReceiverType(processor, state, place)
    is PsiIntersectionType -> conjuncts.all { it.processReceiverType(processor, state, place) }
    is PsiCapturedWildcardType -> upperBound.processReceiverType(processor, state, place)
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

  if (state[STATIC_CONTEXT] != true && clazz.qualifiedName == JAVA_LANG_CLASS) {
    // this is `Class<Something>`
    val type = parameters.singleOrNull() as? PsiClassType // `Something`
    if (!type.processReceiverType(processor, newState.put(STATIC_CONTEXT, true), place)) {
      return false
    }
  }

  if (!processMapType(processor, newState, place)) {
    return false
  }

  return processClassDeclarations(clazz, processor, newState, null, place)
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
