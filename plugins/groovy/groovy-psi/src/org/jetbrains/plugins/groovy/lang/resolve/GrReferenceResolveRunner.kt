/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.*
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArrayInitializer
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTraitType
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.ClosureParameterEnhancer
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint

class GrReferenceResolveRunner(val place: GrReferenceExpression, val processor: PsiScopeProcessor) {

  fun resolveReferenceExpression(): Boolean {
    val processNonCode = PsiTreeUtil.skipParentsOfType(
      place, GrReferenceExpression::class.java, GrAnnotationArrayInitializer::class.java
    ) !is GrAnnotationNameValuePair
    val initialState = initialState(processNonCode)
    val qualifier = place.qualifier
    if (qualifier == null) {
      if (!treeWalkUp(place, processor, initialState)) return false
      if (!processNonCode) return true
      if (place.context is GrMethodCall && !ClosureMissingMethodContributor.processMethodsFromClosures(place, processor)) return false
    }
    else {
      if (place.dotTokenType === GroovyTokenTypes.mSPREAD_DOT) {
        val qType = qualifier.type
        val componentType = ClosureParameterEnhancer.findTypeForIteration(qType, place)
        if (componentType != null) {
          val state = initialState.put(ClassHint.RESOLVE_CONTEXT, qualifier).put(SpreadState.SPREAD_STATE, SpreadState.create(qType, null))
          return processQualifierType(componentType, state)
        }
      }
      else {
        if (ResolveUtil.isClassReference(place)) return false
        if (!processJavaLangClass(qualifier, initialState)) return false
        if (!processQualifier(qualifier, initialState)) return false
      }
    }
    return true
  }

  private fun processJavaLangClass(qualifier: GrExpression, initialState: ResolveState): Boolean {
    if (qualifier !is GrReferenceExpression) return true

    //optimization: only 'class' or 'this' in static context can be an alias of java.lang.Class
    if ("class" != qualifier.referenceName && !PsiUtil.isThisReference(qualifier) && qualifier.resolve() !is PsiClass) return true

    val classType = ResolveUtil.unwrapClassType(qualifier.getType())
    return classType?.let {
      val state = initialState.put(ClassHint.RESOLVE_CONTEXT, qualifier)
      processQualifierType(classType, state)
    } ?: true
  }

  private fun processQualifier(qualifier: GrExpression, initialState: ResolveState): Boolean {
    val qualifierType = qualifier.type
    val state = initialState.put(ClassHint.RESOLVE_CONTEXT, qualifier)
    if (qualifierType == null || PsiType.VOID == qualifierType) {
      if (qualifier is GrReferenceExpression) {
        val resolved = qualifier.resolve()
        if (resolved is PsiClass) {
          if (!ResolveUtil.processClassDeclarations((resolved as PsiClass?)!!, processor, state, null, place)) return false
        }
        else if (resolved != null && !resolved.processDeclarations(processor, state, null, place)) return false
        if (resolved !is PsiPackage) {
          val objectQualifier = TypesUtil.getJavaLangObject(place)
          if (!processQualifierType(objectQualifier, state)) return false
        }
      }
    }
    else {
      if (!processQualifierType(qualifierType, state)) return false
    }
    return true
  }

  private fun processQualifierType(qualifierType: PsiType, state: ResolveState): Boolean {
    val type = (qualifierType as? PsiDisjunctionType)?.leastUpperBound ?: qualifierType
    return doProcessQualifierType(type, state)
  }

  private fun doProcessQualifierType(qualifierType: PsiType, state: ResolveState): Boolean {
    if (qualifierType is PsiIntersectionType) {
      return qualifierType.conjuncts.find { !processQualifierType(it, state) } == null
    }

    if (qualifierType is PsiCapturedWildcardType) {
      val wildcard = qualifierType.wildcard
      if (wildcard.isExtends) {
        return processQualifierType(wildcard.extendsBound, state)
      }
    }
    if (qualifierType is PsiWildcardType) {
      if (qualifierType.isExtends) {
        return processQualifierType(qualifierType.extendsBound, state)
      }
    }

    // Process trait type conjuncts in reversed order because last applied trait matters.
    if (qualifierType is GrTraitType) return qualifierType.conjuncts.findLast { !processQualifierType(it, state) } == null

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
        if (!processQualifierType(it, resolveState)) return false
      }
    }

    if (state.processNonCodeMembers()) {
      if (!ResolveUtil.processCategoryMembers(place, processor, state)) return false
      if (!ResolveUtil.processNonCodeMembers(qualifierType, processor, place, state)) return false
    }
    return true
  }
}
