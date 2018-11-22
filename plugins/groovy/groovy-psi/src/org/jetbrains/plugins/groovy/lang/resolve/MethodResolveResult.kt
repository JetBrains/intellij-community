// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.*
import com.intellij.util.lazyPub
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.groovy.extensions.GroovyApplicabilityProvider
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod
import org.jetbrains.plugins.groovy.lang.resolve.api.Applicability
import org.jetbrains.plugins.groovy.lang.resolve.api.ArgumentMapping
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.api.ErasedArgument
import org.jetbrains.plugins.groovy.lang.resolve.impl.GdkArgumentMapping
import org.jetbrains.plugins.groovy.lang.resolve.impl.mapArgumentsToParameters
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.*
import kotlin.reflect.jvm.isAccessible

class MethodResolveResult(
  method: PsiMethod,
  place: PsiElement,
  state: ResolveState,
  private val arguments: Arguments?,
  private val typeArguments: Array<out PsiType> = PsiType.EMPTY_ARRAY
) : BaseGroovyResolveResult<PsiMethod>(method, place, state), GroovyMethodResult {

  override fun getContextSubstitutor(): PsiSubstitutor {
    return super.getSubstitutor()
  }

  private fun erasedArguments(arguments: Arguments) = arguments.map(::ErasedArgument)

  private val myArgumentMapping by lazyPub {
    arguments?.let {
      // no arguments => no mapping
      mapArgumentsToParameters(element, contextSubstitutor, arguments, place)
    }
  }

  private val myRealArgumentMapping by lazyPub {
    myArgumentMapping?.let {
      if (method is GrGdkMethod && place is GrReferenceExpression) {
        GdkArgumentMapping(method.staticMethod, buildQualifier(place, state), it)
      }
      else {
        it
      }
    }
  }

  override fun getArgumentMapping(): ArgumentMapping? = myRealArgumentMapping

  private val providersApplicability by lazyPub {
    arguments?.let {
      GroovyApplicabilityProvider.checkProviders(erasedArguments(arguments), method)
    }
  }

  override fun getApplicability(): Applicability {
    return providersApplicability
           ?: myArgumentMapping?.applicability
           ?: Applicability.canBeApplicable
  }

  private val siteSubstitutor by lazyPub {
    contextSubstitutor.putAll(method.typeParameters, typeArguments)
  }

  private val methodCandidate by lazyPub {
    if (arguments != null && method is GrGdkMethod && place is GrReferenceExpression) {
      val newArguments = listOf(buildQualifier(place, state)) + arguments
      MethodCandidate(method.staticMethod, contextSubstitutor, newArguments, place)
    }
    else {
      MethodCandidate(method, contextSubstitutor, arguments, place)
    }
  }

  private val myPartialSubstitutor by lazyPub {
    if (typeArguments.isNotEmpty()) {
      siteSubstitutor
    }
    else {
      GroovyInferenceSessionBuilder(place, methodCandidate, myArgumentMapping).build().inferSubst()
    }
  }

  private val fullSubstitutor by lazyPub {
    if (typeArguments.isNotEmpty()) {
      siteSubstitutor
    }
    else {
      buildTopLevelSession(place).inferSubst(this)
    }
  }

  override fun getCandidate(): MethodCandidate? = methodCandidate

  override fun getPartialSubstitutor(): PsiSubstitutor = myPartialSubstitutor

  override fun getSubstitutor(): PsiSubstitutor = fullSubstitutor

  override fun isApplicable(): Boolean = applicability != Applicability.inapplicable

  val fullSubstitutorDelegate: Lazy<*>
    @TestOnly get() = ::fullSubstitutor.apply { isAccessible = true }.getDelegate() as Lazy<*>
}
