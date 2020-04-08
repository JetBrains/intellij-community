// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.resolve.api.*

class PositionalArgumentMapping<out P : CallParameter>(
  private val parameters: List<P>,
  override val arguments: Arguments,
  private val context: PsiElement
) : ArgumentMapping<P> {

  private val parameterToArgument: List<Pair<P, Argument?>>? by lazy {
    mapByPosition(parameters, arguments, CallParameter::isOptional, false)
  }

  private val argumentToParameter: Map<Argument, P>? by lazy {
    parameterToArgument?.mapNotNull { (parameter, argument) ->
      argument?.let {
        Pair(argument, parameter)
      }
    }?.toMap()
  }

  override fun targetParameter(argument: Argument): P? = argumentToParameter?.get(argument)

  override fun expectedType(argument: Argument): PsiType? = targetParameter(argument)?.type

  override val expectedTypes: Iterable<Pair<PsiType, Argument>>
    get() {
      return (parameterToArgument ?: return emptyList()).mapNotNull { (parameter, argument) ->
        val expectedType = parameter.type
        if (expectedType == null || argument == null) {
          null
        }
        else {
          Pair(expectedType, argument)
        }
      }
    }

  override fun applicability(): Applicability {
    val map = argumentToParameter ?: return Applicability.inapplicable
    return mapApplicability(map, context)
  }

  val distance: Long
    get() {
      val map = requireNotNull(argumentToParameter) {
        "#distance should not be accessed on inapplicable mapping"
      }
      return positionalParametersDistance(map, context)
    }

  override fun highlightingApplicabilities(substitutor: PsiSubstitutor): ApplicabilityResult {
    val map = argumentToParameter ?: return ApplicabilityResult.Inapplicable
    return ApplicabilityResultImpl(highlightApplicabilities(map, substitutor, context))
  }
}

// foo(a?, b, c?, d?, e)
// foo(1, 2)          => 1:b, 2:e
// foo(1, 2, 3)       => 1:a, 2:b, 3:e
// foo(1, 2, 3, 4)    => 1:a, 2:b, 3:c, 4:e
// foo(1, 2, 3, 4, 5) => 1:a, 2:b, 3:c, 4:d, 5:e
private fun <Arg, Param> mapByPosition(parameters: List<Param>,
                                       arguments: List<Arg>,
                                       isOptional: (Param) -> Boolean,
                                       @Suppress("SameParameterValue") partial: Boolean): List<Pair<Param, Arg?>>? {
  val argumentsCount = arguments.size
  val parameterCount = parameters.size
  val optionalParametersCount = parameters.count(isOptional)
  val requiredParametersCount = parameterCount - optionalParametersCount
  if (!partial && argumentsCount !in requiredParametersCount..parameterCount) {
    // too little or too many arguments
    return null
  }

  val result = ArrayList<Pair<Param, Arg?>>(parameterCount)
  val argumentIterator = arguments.iterator()
  var optionalArgsLeft = argumentsCount - requiredParametersCount
  for (parameter in parameters) {
    val optional = isOptional(parameter)
    val argument = if (argumentIterator.hasNext()) {
      if (!optional) {
        argumentIterator.next()
      }
      else if (optionalArgsLeft > 0) {
        optionalArgsLeft--
        argumentIterator.next()
      }
      else {
        // No argument passed for this parameter.
        // Don't call next() on argument iterator, so current argument will be used for next parameter.
        null
      }
    }
    else {
      require(optionalArgsLeft == 0)
      require(optional || partial) {
        "argumentsCount < requiredParameters. This should happen only in partial mode"
      }
      null
    }
    result += Pair(parameter, argument)
  }
  require(!argumentIterator.hasNext() || partial) {
    "argumentsCount > parametersCount. This should happen only in partial mode."
  }

  return result
}
