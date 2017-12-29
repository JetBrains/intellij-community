// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.*
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parents
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArrayInitializer
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTraitType
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.ClosureParameterEnhancer
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil
import org.jetbrains.plugins.groovy.lang.psi.util.treeWalkUpAndGetArray
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.canResolveToMethod
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.isDefinitelyKeyOfMap
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint
import org.jetbrains.plugins.groovy.lang.resolve.processors.GroovyResolverProcessorBuilder
import org.jetbrains.plugins.groovy.lang.resolve.processors.LocalVariableProcessor

private class GrReferenceResolveRunner(val place: GrReferenceExpression, val processor: PsiScopeProcessor) {

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
      val state = initialState.put(ClassHint.RESOLVE_CONTEXT, qualifier)
      if (place.dotTokenType === GroovyTokenTypes.mSPREAD_DOT) {
        return processSpread(qualifier.type, state)
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
      processQualifierType(classType, initialState)
    } ?: true
  }

  private fun processQualifier(qualifier: GrExpression, state: ResolveState): Boolean {
    val qualifierType = qualifier.type
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
    else if (qualifierType is PsiPrimitiveType) {
      val boxedType = qualifierType.getBoxedType(place) ?: return true
      return processQualifierType(boxedType, state)
    }
    else if (qualifierType is PsiArrayType) {
      GroovyPsiManager.getInstance(place.project).getArrayClass(qualifierType.componentType)?.let {
        if (!ResolveUtil.processClassDeclarations(it, processor, state, null, place)) return false
      }
    }

    if (place.parent !is GrMethodCall && InheritanceUtil.isInheritor(qualifierType, CommonClassNames.JAVA_UTIL_COLLECTION)) {
      if (!processSpread(qualifierType, state)) return false
    }

    if (state.processNonCodeMembers()) {
      if (!ResolveUtil.processCategoryMembers(place, processor, state)) return false
      if (!ResolveUtil.processNonCodeMembers(qualifierType, processor, place, state)) return false
    }
    return true
  }

  private fun processSpread(qualifierType: PsiType?, state: ResolveState): Boolean {
    val componentType = ClosureParameterEnhancer.findTypeForIteration(qualifierType, place) ?: return true
    val spreadState = SpreadState.create(qualifierType, state.get(SpreadState.SPREAD_STATE))
    return processQualifierType(componentType, state.put(SpreadState.SPREAD_STATE, spreadState))
  }
}

fun GrReferenceExpression.getCallVariants(upToArgument: GrExpression?): Array<out GroovyResolveResult> {
  val processor = GroovyResolverProcessorBuilder.builder()
    .setAllVariants(true)
    .setUpToArgument(upToArgument)
    .build(this)
  GrReferenceResolveRunner(this, processor).resolveReferenceExpression()
  return processor.candidatesArray
}

fun GrReferenceExpression.resolveReferenceExpression(forceRValue: Boolean, incomplete: Boolean): Array<out GroovyResolveResult> {
  resolvePackageOrClass()?.let { return arrayOf(it) }

  val localVariableResults = resolveLocalVariable()
  if (localVariableResults.isNotEmpty()) return localVariableResults

  if (!canResolveToMethod(this) && isDefinitelyKeyOfMap(this)) return GroovyResolveResult.EMPTY_ARRAY
  val processor = GroovyResolverProcessorBuilder.builder()
    .setForceRValue(forceRValue)
    .setIncomplete(incomplete)
    .build(this)
  GrReferenceResolveRunner(this, processor).resolveReferenceExpression()
  return processor.candidatesArray
}

private fun GrReferenceExpression.resolvePackageOrClass(): GroovyResolveResult? {
  return doResolvePackageOrClass()?.let { GroovyResolveResultImpl(it, true) }
}

private fun GrReferenceExpression.doResolvePackageOrClass(): PsiElement? {
  val facade = JavaPsiFacade.getInstance(project)
  val scope = resolveScope

  fun GrReferenceExpression.resolveClass(): PsiClass? {
    if (parent is GrMethodCall) return null
    val name = referenceName ?: return null
    if (name.isEmpty() || !name.first().isUpperCase()) return null
    val qname = qualifiedReferenceName ?: return null
    return facade.findClass(qname, scope)
  }

  if (isQualified) {
    resolveClass()?.let { return it }
  }

  for (parent in parents().drop(1)) {
    if (parent !is GrReferenceExpression) return null
    if (parent.resolveClass() == null) continue
    val qname = qualifiedReferenceName!!
    return facade.findPackage(qname)
  }

  return null
}

private fun GrReferenceExpression.resolveLocalVariable(): Array<out GroovyResolveResult> {
  if (isQualified) return GroovyResolveResult.EMPTY_ARRAY
  val name = referenceName ?: return GroovyResolveResult.EMPTY_ARRAY
  return treeWalkUpAndGetArray(LocalVariableProcessor(name))
}
