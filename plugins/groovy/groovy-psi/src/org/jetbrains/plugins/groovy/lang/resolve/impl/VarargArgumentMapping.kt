// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.*
import com.intellij.psi.util.TypeConversionUtil
import com.intellij.util.containers.ComparatorUtil.min
import com.intellij.util.lazyPub
import org.jetbrains.plugins.groovy.lang.resolve.api.Applicability
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument
import org.jetbrains.plugins.groovy.lang.resolve.api.ArgumentMapping
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.util.init

private typealias MapWithVarargs = Pair<Map<PsiParameter, Argument>, Arguments>

class VarargArgumentMapping(
  method: PsiMethod,
  erasureSubstitutor: PsiSubstitutor,
  arguments: Arguments,
  context: PsiElement
) : ArgumentMapping {

  init {
    val parameters = method.parameterList.parameters
    require(parameters.isNotEmpty())
    require(parameters.last().type is PsiArrayType)
  }

  private val varargType: PsiType by lazyPub {
    val parameterType = method.parameterList.parameters.last().type as PsiArrayType
    parameterType.componentType
  }

  private val varargTypeErased: PsiType by lazyPub {
    TypeConversionUtil.erasure(varargType, erasureSubstitutor)
  }

  private val mapping: MapWithVarargs? by lazyPub(fun(): MapWithVarargs? {
    val parameters = method.parameterList.parameters
    val regularParameters = parameters.init()
    val regularParametersCount = regularParameters.size
    if (arguments.size < regularParametersCount) {
      // not enough arguments
      return null
    }
    val map = regularParameters.zip(arguments).toMap()
    val varargs = arguments.drop(regularParametersCount)
    return Pair(map, varargs)
  })

  override val applicability: Applicability by lazyPub(fun(): Applicability {
    val (positional, varargs) = mapping ?: return Applicability.inapplicable

    val mapApplicability = mapApplicability(positional, erasureSubstitutor, context)
    if (mapApplicability === Applicability.inapplicable) {
      return Applicability.inapplicable
    }

    val varargApplicability = varargApplicability(varargs, context)
    if (varargApplicability === Applicability.inapplicable) {
      return Applicability.inapplicable
    }

    return min(mapApplicability, varargApplicability)
  })

  private fun varargApplicability(varargs: Arguments, context: PsiElement): Applicability {
    for (vararg in varargs) {
      val argumentAssignability = argumentApplicability(vararg, context) {
        varargTypeErased
      }
      if (argumentAssignability != Applicability.applicable) {
        return argumentAssignability
      }
    }
    return Applicability.applicable
  }

  override val expectedTypes: Iterable<Pair<PsiType, Argument>>
    get() {
      val (positional, varargs) = mapping ?: return emptyList()
      val positionalSequence = positional.asSequence().map { (parameter, argument) -> Pair(parameter.type, argument) }
      val varargsSequence = varargs.asSequence().map { Pair(varargType, it) }
      return (positionalSequence + varargsSequence).asIterable()
    }
}
