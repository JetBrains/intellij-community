// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.SmartList
import com.intellij.util.containers.minimalElements
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.filterSameSignatureCandidates
import org.jetbrains.plugins.groovy.lang.resolve.api.Applicability
import org.jetbrains.plugins.groovy.lang.resolve.api.Applicability.canBeApplicable
import org.jetbrains.plugins.groovy.lang.resolve.api.Applicability.inapplicable
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyOverloadResolver

/**
 * Applicable results and a flag whether overload should be selected.
 *
 * There may be cases when we don't know types of arguments, so we cannot select a method 100% sure.
 * Consider the example:
 * ```
 * def foo(Integer a) {}
 * def foo(Number b) {}
 * foo(unknownArgument)
 * ```
 * If we were to choose an overload with most specific signature, then only `foo(Integer)` would be chosen.
 * In such case we assume both overloads as [potentially applicable][canBeApplicable]
 * and offer navigation to both of them, etc.
 */
typealias ApplicabilitiesResult<X> = Pair<List<X>, Boolean>

fun <X> List<X>.filterApplicable(applicability: (X) -> Applicability): ApplicabilitiesResult<X> {
  if (isEmpty()) {
    return ApplicabilitiesResult(emptyList(), true)
  }
  val results = SmartList<X>()
  var canSelectOverload = true
  for (thing in this) {
    val thingApplicability = applicability(thing)
    if (thingApplicability == inapplicable) {
      continue
    }
    if (thingApplicability == canBeApplicable) {
      canSelectOverload = false
    }
    results += thing
  }
  return ApplicabilitiesResult(results, canSelectOverload)
}

fun List<GroovyMethodResult>.correctStaticScope(): List<GroovyMethodResult> {
  if (isEmpty()) return emptyList()
  return filterTo(SmartList()) {
    it.isStaticsOK
  }
}

private val overloadResolverEp = ExtensionPointName.create<GroovyOverloadResolver>("org.intellij.groovy.overloadResolver")

private fun compare(left: GroovyMethodResult, right: GroovyMethodResult): Int {
  for (resolver in overloadResolverEp.extensions) {
    val result = resolver.compare(left, right)
    if (result != 0) {
      return result
    }
  }
  return 0
}

private val resultOverloadComparator: Comparator<GroovyMethodResult> = Comparator(::compare)

fun chooseOverloads(candidates: List<GroovyMethodResult>): List<GroovyMethodResult> {
  return SmartList(candidates.minimalElements(resultOverloadComparator))
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
