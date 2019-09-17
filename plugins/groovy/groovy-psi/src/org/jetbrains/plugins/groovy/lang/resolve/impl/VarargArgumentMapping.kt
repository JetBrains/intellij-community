// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.*
import com.intellij.util.containers.ComparatorUtil.min
import org.jetbrains.plugins.groovy.lang.resolve.api.*
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

  override fun isVararg(parameter: PsiParameter): Boolean = varargParameter === parameter

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
      val argumentAssignability = argumentApplicability(parameterType, vararg.runtimeType, context)
      if (argumentAssignability != Applicability.applicable) {
        return argumentAssignability
      }
    }
    return Applicability.applicable
  }

  override fun highlightingApplicabilities(substitutor: PsiSubstitutor): Applicabilities {
    val (positional, varargs) = mapping ?: return emptyMap()

    val positionalApplicabilities = highlightApplicabilities(positional, substitutor,  context)
    val parameterType = parameterType(varargType, substitutor, false)
    val varargApplicabilities = varargs.associate {
      it to ApplicabilityData(parameterType, argumentApplicability(parameterType, it.type, context))
    }

    return positionalApplicabilities + varargApplicabilities
  }

  fun compare(right: VarargArgumentMapping): Int {
    val sizeDiff = varargs.size - right.varargs.size
    if (sizeDiff != 0) return sizeDiff
    val leftDistance = distance
    val rightDistance = right.distance
    return when {
      distance == 0L -> -1
      right.distance == 0L -> 1
      else -> leftDistance.compareTo(rightDistance)
    }
  }

  /**
   * Used only in comparing with VarargArgumentMapping witch contains same count of varargs
   *
   * @see org.codehaus.groovy.runtime.MetaClassHelper.calculateParameterDistance
   */
  private val distance: Long
    get() {
      val map = requireNotNull(mapping) {
        "#distance should not be accessed on inapplicable mapping"
      }
      val (positional, varargs) = map
      var distance = positionalParametersDistance(positional, context)

      for (vararg in varargs) {
        val argumentType = vararg.runtimeType ?: continue
        distance += parameterDistance(argumentType, varargType, context)
      }

      return distance
    }

  val varargs: Collection<Argument>
    get() = requireNotNull(mapping) {
      "#varargs should not be accessed on inapplicable mapping"
    }.second
}
