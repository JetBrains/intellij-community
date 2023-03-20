// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.*
import com.intellij.psi.GenericsUtil.isTypeArgumentsApplicable
import com.intellij.util.recursionSafeLazy
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.groovy.lang.resolve.api.*
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSessionBuilder
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.buildTopLevelSession
import org.jetbrains.plugins.groovy.util.recursionAwareLazy
import kotlin.reflect.jvm.isAccessible

open class MethodResolveResult(
  method: PsiMethod,
  place: PsiElement,
  state: ResolveState,
  arguments: Arguments?
) : BaseMethodResolveResult(method, place, state, arguments) {

  override fun getPartialSubstitutor(): PsiSubstitutor = myPartialSubstitutor

  private val myPartialSubstitutor by recursionAwareLazy {
    GroovyInferenceSessionBuilder(place, myCandidate, contextSubstitutor).build().inferSubst()
  }

  override fun getSubstitutor(): PsiSubstitutor = fullSubstitutor ?: run {
    log.warn("Recursion prevented")
    PsiSubstitutor.EMPTY
  }

  private val fullSubstitutor by recursionSafeLazy {
    buildTopLevelSession(place, isOperatorPutAt = method.name == "putAt").inferSubst(this)
  }

  override fun createMethodCandidate(method: PsiMethod, place: PsiElement, state: ResolveState): GroovyMethodCandidate {
    val originalCandidate = super.createMethodCandidate(method, place, state)
    return object : GroovyMethodCandidate {
      override val receiverType: PsiType? get() = originalCandidate.receiverType
      override val method: PsiMethod get() = originalCandidate.method
      override val argumentMapping: ArgumentMapping<PsiCallParameter>? by recursionAwareLazy {
        originalCandidate.argumentMapping?.let {
          GenericsArgumentMapping(method, place, it)
        }
      }
    }
  }

  private class GenericsArgumentMapping(
    private val method: PsiMethod,
    private val place: PsiElement,
    delegate: ArgumentMapping<PsiCallParameter>
  ) : DelegateArgumentMapping<PsiCallParameter>(delegate) {
    override fun highlightingApplicabilities(substitutor: PsiSubstitutor): ApplicabilityResult {
      val applicabilityResult = super.highlightingApplicabilities(substitutor)
      return when {
        applicabilityResult.applicability != Applicability.applicable -> {
          applicabilityResult
        }
        isTypeArgumentsApplicable(method.typeParameters, substitutor, place) -> {
          ApplicabilityResult.Applicable
        }
        else -> {
          ApplicabilityResult.Inapplicable
        }
      }
    }
  }

  val fullSubstitutorDelegate: Lazy<*>
    @TestOnly get() = ::fullSubstitutor.apply { isAccessible = true }.getDelegate() as Lazy<*>
}
