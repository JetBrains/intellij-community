// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.references

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.util.GrInnerClassConstructorUtil.enclosingClass
import org.jetbrains.plugins.groovy.lang.resolve.DiamondResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.api.*
import org.jetbrains.plugins.groovy.lang.resolve.impl.*

abstract class GrConstructorReference<T : PsiElement>(element: T) : GroovyCachingReference<T>(element),
                                                                    GroovyConstructorReference {

  final override fun resolveClass(): GroovyResolveResult? = myConstructedClassReference.resolve(false).singleOrNull()

  private val myConstructedClassReference = object : GroovyCachingReference<T>(element) {
    override fun doResolve(incomplete: Boolean): Collection<GroovyResolveResult> {
      return doResolveClass()?.let(::listOf) ?: emptyList()
    }
  }

  protected abstract fun doResolveClass(): GroovyResolveResult?

  protected open val supportsMapInvocation: Boolean get() = true

  protected open val supportsEnclosingInstance: Boolean get() = true

  final override fun doResolve(incomplete: Boolean): Collection<GroovyResolveResult> {
    val classCandidate: GroovyResolveResult = resolveClass() ?: return emptyList()
    val clazz: PsiClass = classCandidate.element as? PsiClass ?: return emptyList()
    val place: T = element
    val arguments: Arguments? = arguments

    val constructors: List<PsiMethod> = getAllConstructors(clazz, place)
    if (incomplete || arguments == null) {
      return constructors.toResolveResults()
    }

    val withArguments: WithArguments = withArguments(place, classCandidate.contextSubstitutor, classCandidate is DiamondResolveResult)
    val withEnclosingClassArguments: WithArguments = withEnclosingClassArguments(clazz, withArguments)
    return chooseConstructors(constructors, arguments, supportsMapInvocation, withEnclosingClassArguments)
  }

  private fun withEnclosingClassArguments(clazz: PsiClass, withArguments: WithArguments): WithArguments {
    if (!supportsEnclosingInstance) {
      return withArguments
    }
    val enclosingClassArgument: Argument? = enclosingClassArgument(element, clazz)
    if (enclosingClassArgument == null) {
      return withArguments
    }
    else {
      val enclosingClassArguments: Arguments = listOf(enclosingClassArgument)
      return { arguments: Arguments, mapConstructor: Boolean ->
        withArguments(enclosingClassArguments + arguments, mapConstructor)
      }
    }
  }

  private fun enclosingClassArgument(place: PsiElement, constructedClass: PsiClass): Argument? {
    val enclosingClass = enclosingClass(element, constructedClass) ?: return null
    val type = JavaPsiFacade.getElementFactory(place.project).createType(enclosingClass)
    return JustTypeArgument(type)
  }
}
