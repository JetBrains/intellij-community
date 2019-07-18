// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors

import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.JavaScopeProcessorEvent
import com.intellij.psi.scope.NameHint
import com.intellij.psi.scope.ProcessorWithHints
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.util.SmartList
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.util.elementInfo
import org.jetbrains.plugins.groovy.lang.resolve.getName
import org.jetbrains.plugins.groovy.lang.resolve.impl.*
import org.jetbrains.plugins.groovy.lang.resolve.log
import org.jetbrains.plugins.groovy.lang.resolve.sorryCannotKnowElementKind

abstract class BaseMethodProcessor(private val name: String) : ProcessorWithHints() {

  init {
    hint(NameHint.KEY, NameHint { name })
  }

  protected val myCandidates = SmartList<GroovyMethodResult>()
  private var myApplicable: ApplicabilitiesResult? = null
  val acceptMore: Boolean get() = myApplicable?.first.isNullOrEmpty()

  final override fun execute(element: PsiElement, state: ResolveState): Boolean {
    if (!acceptMore) {
      log.warn("Don't pass more methods if ${javaClass.name} doesn't want to accept them")
      return false
    }
    if (element !is PsiMethod) {
      if (state[sorryCannotKnowElementKind] != true && Registry.`is`("groovy.assert.element.kind.in.resolve")) {
        log.error(
          "Unexpected element. " + elementInfo(element) + "\n" +
          "See org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor docs."
        )
      }
      return true
    }
    if (name != getName(state, element)) return true
    val candidate = candidate(element, state)
    if (candidate != null) {
      myCandidates += candidate
      myApplicable = null
    }
    return true
  }

  protected abstract fun candidate(element: PsiMethod, state: ResolveState): GroovyMethodResult?

  override fun handleEvent(event: PsiScopeProcessor.Event, associated: Any?) {
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
      val filteredBySignature = filterBySignature(applicableCandidates)
      if (canChooseOverload) {
        return chooseOverloads(filteredBySignature)
      }
      else {
        return filteredBySignature
      }
    }

  val allCandidates: List<GroovyMethodResult> get() = myCandidates
}
