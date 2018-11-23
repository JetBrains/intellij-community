// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.*
import com.intellij.util.lazyPub
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.groovy.extensions.GroovyApplicabilityProvider.checkProviders
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod
import org.jetbrains.plugins.groovy.lang.resolve.api.Applicability
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.api.ErasedArgument
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCandidate
import org.jetbrains.plugins.groovy.lang.resolve.impl.GdkMethodCandidate
import org.jetbrains.plugins.groovy.lang.resolve.impl.MethodCandidateImpl
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSessionBuilder
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.buildQualifier
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.buildTopLevelSession
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.putAll
import kotlin.reflect.jvm.isAccessible

class MethodResolveResult(
  method: PsiMethod,
  place: PsiElement,
  state: ResolveState,
  arguments: Arguments?,
  typeArguments: Array<out PsiType> = PsiType.EMPTY_ARRAY
) : BaseGroovyResolveResult<PsiMethod>(method, place, state), GroovyMethodResult {

  override fun getContextSubstitutor(): PsiSubstitutor = super.getSubstitutor()

  override fun getApplicability(): Applicability = myApplicability

  private val myApplicability by lazyPub {
    arguments?.let { checkProviders(arguments.map(::ErasedArgument), method) }
    ?: myCandidate.argumentMapping?.applicability
    ?: Applicability.canBeApplicable
  }

  private val myCandidate: GroovyMethodCandidate by lazyPub {
    MethodCandidateImpl(method, contextSubstitutor, arguments, place)
  }

  override fun getCandidate(): GroovyMethodCandidate? = myRealCandidate

  private val myRealCandidate by lazyPub {
    val mapping = myCandidate.argumentMapping
    if (mapping != null && method is GrGdkMethod && place is GrReferenceExpression) {
      GdkMethodCandidate(method.staticMethod, buildQualifier(place, state), mapping)
    }
    else {
      myCandidate
    }
  }

  private val siteSubstitutor by lazyPub {
    contextSubstitutor.putAll(method.typeParameters, typeArguments)
  }

  private val myPartialSubstitutor by lazyPub {
    if (typeArguments.isNotEmpty()) {
      siteSubstitutor
    }
    else {
      GroovyInferenceSessionBuilder(place, myCandidate, contextSubstitutor).build().inferSubst()
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

  override fun getPartialSubstitutor(): PsiSubstitutor = myPartialSubstitutor

  override fun getSubstitutor(): PsiSubstitutor = fullSubstitutor

  override fun isApplicable(): Boolean = applicability != Applicability.inapplicable

  val fullSubstitutorDelegate: Lazy<*>
    @TestOnly get() = ::fullSubstitutor.apply { isAccessible = true }.getDelegate() as Lazy<*>
}
