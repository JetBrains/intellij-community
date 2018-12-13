// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.references

import com.intellij.psi.*
import com.intellij.psi.CommonClassNames.JAVA_UTIL_MAP
import com.intellij.psi.util.InheritanceUtil.isInheritor
import com.intellij.util.SmartList
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.util.GrInnerClassConstructorUtil.enclosingClass
import org.jetbrains.plugins.groovy.lang.resolve.BaseMethodResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.DiamondResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.ElementResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.MethodResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.api.*
import org.jetbrains.plugins.groovy.lang.resolve.impl.DefaultMethodComparatorContext
import org.jetbrains.plugins.groovy.lang.resolve.impl.chooseOverloads
import org.jetbrains.plugins.groovy.lang.resolve.impl.getAllConstructors

abstract class GrConstructorReference<T : PsiElement>(element: T) : GroovyCachingReference<T>(element), GroovyCallReference {

  // TODO consider introducing GroovyConstructorCallReference and putting it there
  protected abstract fun resolveClass(): GroovyResolveResult?

  protected open val supportsMapInvocation: Boolean get() = true

  override fun doResolve(incomplete: Boolean): Collection<GroovyResolveResult> {
    val classCandidate = resolveClass() ?: return emptyList()
    val constructedClass = classCandidate.element as? PsiClass ?: return emptyList()
    val substitutor = classCandidate.contextSubstitutor
    val needsInference = classCandidate is DiamondResolveResult

    val allConstructors = getAllConstructors(constructedClass, element)
    val userArguments = arguments
    if (incomplete || userArguments == null) {
      return allConstructors.map(::ElementResolveResult)
    }

    val state = ResolveState.initial().put(PsiSubstitutor.KEY, substitutor)

    val enclosingClassArgument = enclosingClassArgument(element, constructedClass)?.let(::listOf) ?: emptyList()
    val realArguments = enclosingClassArgument + userArguments
    val (realApplicable, realResults) = tryArguments(allConstructors, state, needsInference, realArguments)
    if (realApplicable || !supportsMapInvocation) {
      return realResults
    }

    val singleArgument = userArguments.singleOrNull()
    if (singleArgument == null || !isInheritor(singleArgument.runtimeType, JAVA_UTIL_MAP)) {
      // not a map constructor call,
      // no real applicable results =>
      // result all results
      return realResults
    }

    return tryArguments(allConstructors, state, needsInference, enclosingClassArgument).second
  }

  private fun enclosingClassArgument(place: PsiElement, constructedClass: PsiClass): Argument? {
    val enclosingClass = enclosingClass(element, constructedClass) ?: return null
    val type = JavaPsiFacade.getElementFactory(place.project).createType(enclosingClass)
    return JustTypeArgument(type)
  }

  private fun tryArguments(constructors: List<PsiMethod>,
                           state: ResolveState,
                           diamond: Boolean,
                           arguments: Arguments): Pair<Boolean, List<GroovyMethodResult>> {
    val allResults = constructors.map {
      if (diamond || it.typeParameters.isNotEmpty()) {
        MethodResolveResult(it, element, state, arguments)
      }
      else {
        BaseMethodResolveResult(it, element, state, arguments)
      }
    }
    val applicable = chooseConstructors(allResults, arguments)
    return if (applicable != null) {
      Pair(true, applicable)
    }
    else {
      Pair(false, allResults)
    }
  }

  private fun chooseConstructors(candidates: List<GroovyMethodResult>, arguments: Arguments): List<GroovyMethodResult>? {
    val applicable = candidates.filterTo(SmartList()) {
      it.isApplicable
    }
    if (applicable.isNotEmpty()) {
      return chooseOverloads(applicable, DefaultMethodComparatorContext(element, arguments, true))
    }
    else {
      return null
    }
  }
}
