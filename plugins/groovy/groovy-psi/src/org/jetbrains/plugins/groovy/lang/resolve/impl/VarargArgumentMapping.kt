// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.util.TypeConversionUtil
import com.intellij.util.containers.ComparatorUtil.min
import org.jetbrains.plugins.groovy.lang.resolve.api.*
import org.jetbrains.plugins.groovy.lang.resolve.api.ApplicabilityResult.ArgumentApplicability
import org.jetbrains.plugins.groovy.util.recursionAwareLazy

class VarargArgumentMapping<out P : CallParameter>(
  parameters: List<P>,
  override val arguments: Arguments,
  private val context: PsiElement
) : ArgumentMapping<P> {

  override val varargParameter: P = parameters.last()

  private val varargType: PsiType = (varargParameter.type as PsiArrayType).componentType

  private val mapping: Pair<Map<Argument, P>, Set<Argument>>? by recursionAwareLazy {
    val regularParameters = parameters.dropLast(1)
    val regularParametersCount = regularParameters.size
    if (arguments.size < regularParametersCount) {
      // not enough arguments
      return@recursionAwareLazy null
    }
    val map = arguments.zip(regularParameters).toMap()
    val varargs = arguments.drop(regularParametersCount)
    Pair(map, LinkedHashSet(varargs))
  }

  override fun targetParameter(argument: Argument): P? {
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
      val result = ArrayList<Pair<PsiType, Argument>>()
      positional.mapNotNullTo(result) { (argument, parameter) ->
        parameter.type?.let { expectedType ->
          Pair(expectedType, argument)
        }
      }
      varargs.mapTo(result) {
        Pair(varargType, it)
      }
      return result
    }

  override fun applicability(): Applicability {
    val (positional, varargs) = mapping ?: return Applicability.inapplicable

    val mapApplicability = mapApplicability(positional, context)
    if (mapApplicability === Applicability.inapplicable) {
      return Applicability.inapplicable
    }

    val varargApplicability = varargApplicability(varargs, context)
    if (varargApplicability === Applicability.inapplicable) {
      return Applicability.inapplicable
    }

    return min(mapApplicability, varargApplicability)
  }

  private fun varargApplicability(varargs: Collection<Argument>, context: PsiElement): Applicability {
    val parameterType = TypeConversionUtil.erasure(varargType)
    for (vararg in varargs) {
      val argumentAssignability = argumentApplicability(parameterType, vararg.runtimeType, context)
      if (argumentAssignability != Applicability.applicable) {
        return argumentAssignability
      }
    }
    return Applicability.applicable
  }

  override fun highlightingApplicabilities(substitutor: PsiSubstitutor): ApplicabilityResult {
    val (positional, varargs) = mapping ?: return ApplicabilityResult.Inapplicable

    val positionalApplicabilities = highlightApplicabilities(positional, substitutor, context)
    val parameterType = parameterType(varargType, substitutor, false)
    val varargApplicabilities = varargs.associateWith {
      ArgumentApplicability(parameterType, argumentApplicability(parameterType, it.type, context))
    }

    return ApplicabilityResultImpl(positionalApplicabilities + varargApplicabilities)
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
        distance += parameterDistance(argumentType, null, varargType, context)
      }

      return distance
    }

  val varargs: Collection<Argument>
    get() = requireNotNull(mapping) {
      "#varargs should not be accessed on inapplicable mapping"
    }.second

  companion object {
    fun <X : CallParameter> compare(left: VarargArgumentMapping<X>, right: VarargArgumentMapping<X>): Int {
      val sizeDiff = left.varargs.size - right.varargs.size
      if (sizeDiff != 0) {
        return sizeDiff
      }
      val leftDistance = left.distance
      val rightDistance = right.distance
      return when {
        leftDistance == 0L -> -1
        right.distance == 0L -> 1
        else -> leftDistance.compareTo(rightDistance)
      }
    }
  }
}
