// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.ResolveState
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.*
import kotlin.reflect.jvm.isAccessible

class MethodResolveResult(
  method: PsiMethod,
  ref: GrReferenceExpression,
  state: ResolveState
) : BaseGroovyResolveResult<PsiMethod>(method, ref, state), GroovyMethodResult {

  private val siteSubstitutor by lazy(LazyThreadSafetyMode.PUBLICATION) {
    super.getSubstitutor().putAll(method.typeParameters, ref.typeArguments)
  }

  private val methodCandidate by lazy(LazyThreadSafetyMode.PUBLICATION) {
    val argumentConstraints = buildArguments(ref)
    if (method is GrGdkMethod) {
      val arguments = mutableListOf<Argument>().apply {
        add(0, buildQualifier(ref, state))
        addAll(argumentConstraints)
      }
      MethodCandidate(method.staticMethod, siteSubstitutor, arguments, ref)
    }
    else {
      MethodCandidate(method, siteSubstitutor, argumentConstraints, ref)
    }
  }

  private val applicabilitySubstitutor by lazy(LazyThreadSafetyMode.PUBLICATION) {
    if (ref.typeArguments.isNotEmpty()) {
      siteSubstitutor
    }
    else {
      GroovyInferenceSessionBuilder(ref, methodCandidate).build().inferSubst()
    }
  }

  private val fullSubstitutor by lazy(LazyThreadSafetyMode.PUBLICATION) {
    if (ref.typeArguments.isNotEmpty()) {
      siteSubstitutor
    }
    else {
      GroovyInferenceSessionBuilder(ref, methodCandidate)
        .addReturnConstraint()
        .resolveMode(false)
        .startFromTop(true)
        .build().inferSubst(ref)
    }
  }

  override fun getCandidate(): MethodCandidate? = methodCandidate

  override fun getPartialSubstitutor(): PsiSubstitutor = applicabilitySubstitutor

  override fun getSubstitutor(): PsiSubstitutor = fullSubstitutor

  private val applicability by lazy {
    methodCandidate.isApplicable(applicabilitySubstitutor)
  }

  override fun isApplicable(): Boolean = applicability

  val applicabilityDelegate: Lazy<*>
    @TestOnly get() = ::applicability.apply { isAccessible = true }.getDelegate() as Lazy<*>

  val fullSubstitutorDelegate: Lazy<*>
    @TestOnly get() = ::fullSubstitutor.apply { isAccessible = true }.getDelegate() as Lazy<*>
}
