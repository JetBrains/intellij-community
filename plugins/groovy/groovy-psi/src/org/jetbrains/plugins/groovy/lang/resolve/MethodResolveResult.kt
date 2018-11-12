// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.*
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSessionBuilder
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.MethodCandidate
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.buildQualifier
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.putAll
import kotlin.reflect.jvm.isAccessible

class MethodResolveResult(
  method: PsiMethod,
  place: PsiElement,
  state: ResolveState,
  private val arguments: Arguments?,
  private val typeArguments: Array<out PsiType>
) : BaseGroovyResolveResult<PsiMethod>(method, place, state), GroovyMethodResult {

  override fun getContextSubstitutor(): PsiSubstitutor {
    return super.getSubstitutor()
  }

  private val siteSubstitutor by lazy(LazyThreadSafetyMode.PUBLICATION) {
    contextSubstitutor.putAll(method.typeParameters, typeArguments)
  }

  private val methodCandidate by lazy(LazyThreadSafetyMode.PUBLICATION) {
    if (arguments != null && method is GrGdkMethod && place is GrReferenceExpression) {
      val newArguments = listOf(buildQualifier(place, state)) + arguments
      MethodCandidate(method.staticMethod, siteSubstitutor, newArguments, place)
    }
    else {
      MethodCandidate(method, siteSubstitutor, arguments, place)
    }
  }

  private val applicabilitySubstitutor by lazy(LazyThreadSafetyMode.PUBLICATION) {
    if (typeArguments.isNotEmpty()) {
      siteSubstitutor
    }
    else {
      GroovyInferenceSessionBuilder(place, methodCandidate).build().inferSubst()
    }
  }

  private val fullSubstitutor by lazy(LazyThreadSafetyMode.PUBLICATION) {
    if (typeArguments.isNotEmpty()) {
      siteSubstitutor
    }
    else {
      GroovyInferenceSessionBuilder(place, methodCandidate)
        .addReturnConstraint()
        .resolveMode(false)
        .startFromTop(true)
        .build().inferSubst(this)
    }
  }

  override fun getCandidate(): MethodCandidate? = methodCandidate

  override fun getPartialSubstitutor(): PsiSubstitutor = applicabilitySubstitutor

  override fun getSubstitutor(): PsiSubstitutor = fullSubstitutor

  private val applicability by lazy {
    methodCandidate.isApplicable(contextSubstitutor)
  }

  override fun isApplicable(): Boolean = applicability

  val applicabilityDelegate: Lazy<*>
    @TestOnly get() = ::applicability.apply { isAccessible = true }.getDelegate() as Lazy<*>

  val fullSubstitutorDelegate: Lazy<*>
    @TestOnly get() = ::fullSubstitutor.apply { isAccessible = true }.getDelegate() as Lazy<*>
}
