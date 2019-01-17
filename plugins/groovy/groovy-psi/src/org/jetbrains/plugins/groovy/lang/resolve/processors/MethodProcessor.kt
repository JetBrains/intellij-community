// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors

import com.intellij.psi.*
import com.intellij.psi.scope.ElementClassHint
import com.intellij.psi.scope.ElementClassHint.DeclarationKind
import com.intellij.psi.scope.JavaScopeProcessorEvent
import com.intellij.psi.scope.NameHint
import com.intellij.psi.scope.ProcessorWithHints
import com.intellij.psi.scope.PsiScopeProcessor.Event
import com.intellij.util.SmartList
import com.intellij.util.containers.isNullOrEmpty
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.util.elementInfo
import org.jetbrains.plugins.groovy.lang.resolve.*
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.impl.*
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.putAll

class MethodProcessor(
  private val name: String,
  private val place: PsiElement,
  private val arguments: Arguments?,
  private val typeArguments: Array<out PsiType>
) : ProcessorWithHints(),
    NameHint,
    GroovyResolveKind.Hint,
    ElementClassHint,
    DynamicMembersHint {

  init {
    hint(NameHint.KEY, this)
    hint(GroovyResolveKind.HINT_KEY, this)
    hint(ElementClassHint.KEY, this)
    hint(DynamicMembersHint.KEY, this)
  }

  override fun getName(state: ResolveState): String? = name

  override fun shouldProcess(kind: GroovyResolveKind): Boolean = kind == GroovyResolveKind.METHOD && acceptMore

  override fun shouldProcess(kind: DeclarationKind): Boolean = kind == DeclarationKind.METHOD && acceptMore

  override fun shouldProcessMethods(): Boolean = myCandidates.isEmpty()

  private val myCandidates = SmartList<GroovyMethodResult>()
  private var myApplicable: ApplicabilitiesResult? = null
  private val acceptMore: Boolean get() = myApplicable?.first.isNullOrEmpty()

  override fun execute(element: PsiElement, state: ResolveState): Boolean {
    if (!acceptMore) {
      log.warn("Don't pass more methods if processor doesn't want to accept them")
      return false
    }
    if (element !is PsiMethod) {
      if (state[sorryCannotKnowElementKind] != true) {
        log.error("Unexpected element. ${elementInfo(element)}")
      }
      return true
    }
    if (name != getName(state, element)) return true

    myCandidates += when {
      !element.hasTypeParameters() -> {
        // ignore explicit type arguments if there are no type parameters => no inference needed
        BaseMethodResolveResult(element, place, state, arguments)
      }
      typeArguments.isEmpty() -> {
        // generic method call without explicit type arguments => needs inference
        MethodResolveResult(element, place, state, arguments)
      }
      else -> {
        // generic method call with explicit type arguments => inference happens right here
        val substitutor = state[PsiSubstitutor.KEY].putAll(element.typeParameters, typeArguments)
        BaseMethodResolveResult(element, place, state.put(PsiSubstitutor.KEY, substitutor), arguments)
      }
    }
    myApplicable = null
    return true
  }

  override fun handleEvent(event: Event, associated: Any?) {
    if (JavaScopeProcessorEvent.CHANGE_LEVEL === event && myApplicable == null) {
      myApplicable = computeApplicableCandidates()
    }
  }

  private fun computeApplicableCandidates(): Pair<List<GroovyMethodResult>, Boolean> {
    return myCandidates
      .correctStaticScope()
      .findApplicable()
  }

  val applicableCandidates: List<GroovyMethodResult>?
    get() {
      val (applicableCandidates, canChooseOverload) = myApplicable ?: computeApplicableCandidates()
      if (applicableCandidates.isEmpty()) return null
      if (canChooseOverload) {
        return chooseOverloads(applicableCandidates, DefaultMethodComparatorContext(place, arguments))
      }
      else {
        return filterBySignature(applicableCandidates)
      }
    }

  val allCandidates: List<GroovyMethodResult> get() = myCandidates
}
