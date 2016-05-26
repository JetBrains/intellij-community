/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions

import com.intellij.psi.*
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTraitType
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.ClosureParameterEnhancer
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil
import org.jetbrains.plugins.groovy.lang.resolve.ClosureMissingMethodContributor
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint

fun resolveReferenceExpression(place: GrReferenceExpression, processor: PsiScopeProcessor): Boolean {
  val qualifier = place.qualifier
  if (qualifier == null) {
    if (!ResolveUtil.treeWalkUp(place, processor, true)) return false
    return place.context !is GrMethodCall || ClosureMissingMethodContributor.processMethodsFromClosures(place, processor)
  }
  else {
    if (place.dotTokenType === GroovyTokenTypes.mSPREAD_DOT) {
      val qType = qualifier.type
      val componentType = ClosureParameterEnhancer.findTypeForIteration(qType, place)
      if (componentType != null) {
        val state = ResolveState.initial().put(ClassHint.RESOLVE_CONTEXT, qualifier).put(SpreadState.SPREAD_STATE, SpreadState.create(qType, null))
        return processQualifierType(place, componentType, processor, state)
      }
    }
    else {
      if (ResolveUtil.isClassReference(place)) return false
      if (!processJavaLangClass(place, qualifier, processor)) return false
      return processQualifier(place, qualifier, processor)
    }
  }
  return true
}

fun processJavaLangClass(place: PsiElement, qualifier: GrExpression, processor: PsiScopeProcessor): Boolean {
  if (qualifier !is GrReferenceExpression) return true

  //optimization: only 'class' or 'this' in static context can be an alias of java.lang.Class
  if ("class" != qualifier.referenceName && !PsiUtil.isThisReference(qualifier) && qualifier.resolve() !is PsiClass) return true

  val classType = ResolveUtil.unwrapClassType(qualifier.getType())
  return classType?.let {
    val state = ResolveState.initial().put(ClassHint.RESOLVE_CONTEXT, qualifier)
    processQualifierType(place, classType, processor, state)
  } ?: true
}

fun processQualifier(place: PsiElement, qualifier: GrExpression, processor: PsiScopeProcessor): Boolean {
  val qualifierType = qualifier.type
  val state = ResolveState.initial().put(ClassHint.RESOLVE_CONTEXT, qualifier)
  if (qualifierType == null || PsiType.VOID == qualifierType) {
    if (qualifier is GrReferenceExpression) {
      val resolved = qualifier.resolve()
      if (resolved is PsiClass) {
        if (!ResolveUtil.processClassDeclarations((resolved as PsiClass?)!!, processor, state, null, place)) return false
      }
      else if (resolved != null && !resolved.processDeclarations(processor, state, null, place)) return false
      if (resolved !is PsiPackage) {
        val objectQualifier = TypesUtil.getJavaLangObject(place)
        if (!processQualifierType(place, objectQualifier, processor, state)) return false
      }
    }
  }
  else {
    if (!processQualifierType(place, qualifierType, processor, state)) return false
  }
  return true
}

fun processQualifierType(place: PsiElement, qualifierType: PsiType, processor: PsiScopeProcessor, state: ResolveState): Boolean {
  val type = if (qualifierType is PsiDisjunctionType) qualifierType.leastUpperBound else qualifierType
  return doProcessQualifierType(place, type, processor, state)
}

fun doProcessQualifierType(place: PsiElement, qualifierType: PsiType, processor: PsiScopeProcessor, state: ResolveState): Boolean {
  if (qualifierType is PsiIntersectionType) {
    return qualifierType.conjuncts.find { !processQualifierType(place, it, processor, state) } == null
  }

  if (qualifierType is PsiCapturedWildcardType) {
    val wildcard = qualifierType.wildcard
    if (wildcard.isExtends) {
      return processQualifierType(place, wildcard.extendsBound, processor, state)
    }
  }

  // Process trait type conjuncts in reversed order because last applied trait matters.
  if (qualifierType is GrTraitType) return qualifierType.conjuncts.findLast { !processQualifierType(place, it, processor, state) } == null

  if (qualifierType is PsiClassType) {
    val qualifierResult = qualifierType.resolveGenerics()
    qualifierResult.element?.let {
      val resolveState = state.put(PsiSubstitutor.KEY, qualifierResult.substitutor)
      if (!ResolveUtil.processClassDeclarations(it, processor, resolveState, null, place)) return false
    }
  }
  else if (qualifierType is PsiArrayType) {
    GroovyPsiManager.getInstance(place.project).getArrayClass(qualifierType.componentType)?.let {
      if (!ResolveUtil.processClassDeclarations(it, processor, state, null, place)) return false
    }
  }

  if (place.parent !is GrMethodCall && InheritanceUtil.isInheritor(qualifierType, CommonClassNames.JAVA_UTIL_COLLECTION)) {
    ClosureParameterEnhancer.findTypeForIteration(qualifierType, place)?.let {
      val spreadState = state.get(SpreadState.SPREAD_STATE)
      val resolveState = state.put(SpreadState.SPREAD_STATE, SpreadState.create(qualifierType, spreadState))
      if (!processQualifierType(place, it, processor, resolveState)) return false
    }
  }

  if (!ResolveUtil.processCategoryMembers(place, processor, state)) return false
  if (!ResolveUtil.processNonCodeMembers(qualifierType, processor, place, state)) return false
  return true
}