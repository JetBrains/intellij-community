// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.ResolveState
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
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint.THIS_TYPE
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.buildQualifier
import org.jetbrains.plugins.groovy.util.recursionAwareLazy

open class BaseMethodResolveResult(
  method: PsiMethod,
  place: PsiElement,
  state: ResolveState,
  arguments: Arguments?
) : BaseGroovyResolveResult<PsiMethod>(method, place, state), GroovyMethodResult {

  final override fun isApplicable(): Boolean = applicability != Applicability.inapplicable

  final override fun getApplicability(): Applicability = myApplicability

  private val myApplicability by recursionAwareLazy {
    arguments?.let { checkProviders(arguments.map(::ErasedArgument), method) }
    ?: myCandidate.argumentMapping?.applicability(contextSubstitutor, true)
    ?: Applicability.canBeApplicable
  }

  protected val myCandidate: GroovyMethodCandidate by recursionAwareLazy {
    MethodCandidateImpl(state[THIS_TYPE], method, contextSubstitutor, arguments, place)
  }

  final override fun getCandidate(): GroovyMethodCandidate? = myRealCandidate

  private val myRealCandidate by recursionAwareLazy {
    val mapping = myCandidate.argumentMapping
    if (mapping != null && method is GrGdkMethod && place is GrReferenceExpression) {
      GdkMethodCandidate(method.staticMethod, buildQualifier(place, state), mapping)
    }
    else {
      myCandidate
    }
  }
}
