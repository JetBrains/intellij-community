// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.util.SmartList
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil.ApplicabilityResult.*
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isApplicableConcrete
import org.jetbrains.plugins.groovy.lang.resolve.GrMethodComparator
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.filterSameSignatureCandidates
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments

typealias ApplicabilitiesResult = Pair<List<GroovyMethodResult>, Boolean>

fun List<GroovyMethodResult>.applicable(argumentTypes: Array<out PsiType?>?, place: PsiElement): ApplicabilitiesResult {
  if (isEmpty()) return ApplicabilitiesResult(emptyList(), true)
  val results = SmartList<GroovyMethodResult>()
  var canSelectOverload = true
  for (result in this) {
    val applicability = isApplicableConcrete(argumentTypes, result.element, result.contextSubstitutor, place, true)
    if (applicability == inapplicable || applicability == ambiguous) {
      continue
    }
    if (applicability == canBeApplicable) {
      canSelectOverload = false
    }
    results += result
  }
  return ApplicabilitiesResult(results, canSelectOverload)
}

fun List<GroovyMethodResult>.correctStaticScope(): List<GroovyMethodResult> {
  if (isEmpty()) return emptyList()
  return filterTo(SmartList()) {
    it.isStaticsOK
  }
}

fun chooseOverloads(candidates: List<GroovyMethodResult>, context: GrMethodComparator.Context): List<GroovyMethodResult> {
  if (candidates.size <= 1) return candidates

  val results = SmartList<GroovyMethodResult>()
  val itr = candidates.iterator()

  results.add(itr.next())

  outer@
  while (itr.hasNext()) {
    val resolveResult = itr.next()
    val iterator = results.iterator()
    while (iterator.hasNext()) {
      val otherResolveResult = iterator.next()
      val res = GrMethodComparator.compareMethods(resolveResult, otherResolveResult, context)
      if (res > 0) {
        continue@outer
      }
      else if (res < 0) {
        iterator.remove()
      }
    }
    results.add(resolveResult)
  }

  return results
}

/**
 * @return results that have the same numbers of parameters as passed arguments,
 * or original results if it's not possible to compute arguments number or if there are no matching results
 */
fun filterByArgumentsCount(results: List<GroovyMethodResult>, arguments: Arguments?): List<GroovyMethodResult> {
  val argumentsCount = arguments?.size ?: return results
  val filtered = results.filterTo(SmartList()) {
    it.element.parameterList.parametersCount == argumentsCount
  }
  return if (filtered.isEmpty()) results else filtered
}

fun filterBySignature(results: List<GroovyMethodResult>): List<GroovyMethodResult> {
  if (results.size < 2) return results
  @Suppress("UNCHECKED_CAST")
  return filterSameSignatureCandidates(results).toList() as List<GroovyMethodResult>
}
