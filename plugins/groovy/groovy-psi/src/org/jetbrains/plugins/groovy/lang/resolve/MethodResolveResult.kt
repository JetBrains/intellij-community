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

  private val methodCandidate by lazy {
    val siteSubstitutor = state[PsiSubstitutor.KEY].putAll(method.typeParameters, ref.typeArguments)
    val qualifierConstraint = buildQualifier(ref, state)
    val argumentConstraints = buildArguments(ref)

    if (method is GrGdkMethod) {
      val arguments = mutableListOf<Argument>().apply {
        add(0, qualifierConstraint)
        addAll(argumentConstraints)
      }
      MethodCandidate(method.staticMethod, siteSubstitutor, null, arguments, ref)
    }
    else {
      MethodCandidate(method, siteSubstitutor, qualifierConstraint, argumentConstraints, ref)
    }
  }

  private val applicabilitySubstitutor by lazy {
    GroovyInferenceSessionBuilder(ref, methodCandidate).build().inferSubst()
  }

  private val fullSubstitutor by lazy {
    GroovyInferenceSessionBuilder(ref, methodCandidate)
      .addReturnConstraint()
      .resolveMode(false)
      .startFromTop(true)
      .build()
      .inferSubst(ref)
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
