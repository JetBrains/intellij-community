// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.*
import com.intellij.util.containers.ComparatorUtil.min
import org.jetbrains.plugins.groovy.lang.resolve.api.Applicability
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument
import org.jetbrains.plugins.groovy.lang.resolve.api.ArgumentMapping
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.util.init
import org.jetbrains.plugins.groovy.util.recursionAwareLazy

private typealias MapWithVarargs = Pair<Map<Argument, PsiParameter>, Set<Argument>>

class VarargArgumentMapping(
  method: PsiMethod,
  override val arguments: Arguments,
  private val context: PsiElement
) : ArgumentMapping {

  private val varargParameter: PsiParameter = method.parameterList.parameters.last()

  private val varargType: PsiType = (varargParameter.type as PsiArrayType).componentType

  private val mapping: MapWithVarargs? by recursionAwareLazy(fun(): MapWithVarargs? {
    val parameters = method.parameterList.parameters
    val regularParameters = parameters.init()
    val regularParametersCount = regularParameters.size
    if (arguments.size < regularParametersCount) {
      // not enough arguments
      return null
    }
    val map = arguments.zip(regularParameters).toMap()
    val varargs = arguments.drop(regularParametersCount)
    return Pair(map, LinkedHashSet(varargs))
  })

  override fun targetParameter(argument: Argument): PsiParameter? {
    val (positional, varargs) = mapping ?: return null
    if (argument in varargs) {
      return varargParameter
    }
    else {
      return positional[argument]
    }
  }

  override fun expectedType(argument: Argument): PsiType? {
    val (positional, varargs) = mapping ?: return null
    if (argument in varargs) {
      return varargType
    }
    else {
      return positional[argument]?.type
    }
  }

  override val expectedTypes: Iterable<Pair<PsiType, Argument>>
    get() {
      val (positional, varargs) = mapping ?: return emptyList()
      val positionalSequence = positional.asSequence().map { (argument, parameter) -> Pair(parameter.type, argument) }
      val varargsSequence = varargs.asSequence().map { Pair(varargType, it) }
      return (positionalSequence + varargsSequence).asIterable()
    }

  override fun applicability(substitutor: PsiSubstitutor, erase: Boolean): Applicability {
    val (positional, varargs) = mapping ?: return Applicability.inapplicable

    val mapApplicability = mapApplicability(positional, substitutor, erase, context)
    if (mapApplicability === Applicability.inapplicable) {
      return Applicability.inapplicable
    }

    val varargApplicability = varargApplicability(parameterType(varargType, substitutor, erase), varargs, context)
    if (varargApplicability === Applicability.inapplicable) {
      return Applicability.inapplicable
    }

    return min(mapApplicability, varargApplicability)
  }

  private fun varargApplicability(parameterType: PsiType?, varargs: Collection<Argument>, context: PsiElement): Applicability {
    for (vararg in varargs) {
      val argumentAssignability = argumentApplicability(parameterType, vararg, context)
      if (argumentAssignability != Applicability.applicable) {
        return argumentAssignability
      }
    }
    return Applicability.applicable
  }
}
