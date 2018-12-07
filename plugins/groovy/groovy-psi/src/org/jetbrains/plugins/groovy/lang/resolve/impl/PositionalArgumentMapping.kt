// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.*
import org.jetbrains.plugins.groovy.lang.psi.util.isOptional
import org.jetbrains.plugins.groovy.lang.resolve.api.Applicability
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument
import org.jetbrains.plugins.groovy.lang.resolve.api.ArgumentMapping
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments

class PositionalArgumentMapping(
  method: PsiMethod,
  override val arguments: Arguments,
  private val context: PsiElement
) : ArgumentMapping {

  private val parameterToArgument: Map<PsiParameter, Argument?>? by lazy {
    mapByPosition(arguments, method.parameterList.parameters.toList(), PsiParameter::isOptional, false)
  }

  private val argumentToParameter: Map<Argument, PsiParameter>? by lazy {
    parameterToArgument?.mapNotNull { (parameter, argument) ->
      if (argument == null) null else Pair(argument, parameter)
    }?.toMap()
  }

  override fun targetParameter(argument: Argument): PsiParameter? = argumentToParameter?.get(argument)

  override fun expectedType(argument: Argument): PsiType? = targetParameter(argument)?.type

  override val expectedTypes: Iterable<Pair<PsiType, Argument>>
    get() = argumentToParameter?.asSequence()
              ?.mapNotNull { (argument, parameter) -> Pair(parameter.type, argument) }
              ?.asIterable()
            ?: emptyList()

  override fun applicability(substitutor: PsiSubstitutor, erase: Boolean): Applicability {
    val map = argumentToParameter ?: return Applicability.inapplicable
    return mapApplicability(map, substitutor, erase, context)
  }
}

// foo(a?, b, c?, d?, e)
// foo(1, 2)          => 1:b, 2:e
// foo(1, 2, 3)       => 1:a, 2:b, 3:e
// foo(1, 2, 3, 4)    => 1:a, 2:b, 3:c, 4:e
// foo(1, 2, 3, 4, 5) => 1:a, 2:b, 3:c, 4:d, 5:e
private fun <Arg, Param> mapByPosition(arguments: List<Arg>,
                                       parameters: List<Param>,
                                       isOptional: (Param) -> Boolean,
                                       @Suppress("SameParameterValue") partial: Boolean): Map<Param, Arg?>? {
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

  return result.toMap(LinkedHashMap())
}
