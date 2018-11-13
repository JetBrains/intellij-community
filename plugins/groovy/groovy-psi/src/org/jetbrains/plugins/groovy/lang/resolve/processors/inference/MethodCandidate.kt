// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.openapi.util.Pair
import com.intellij.psi.*
import com.intellij.psi.util.TypeConversionUtil
import org.jetbrains.plugins.groovy.extensions.GroovyApplicabilityProvider.checkProviders
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil.mapParametersToArguments
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil
import org.jetbrains.plugins.groovy.lang.resolve.api.Applicability.applicable
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import java.util.*

class MethodCandidate(val method: PsiMethod,
                      val siteSubstitutor: PsiSubstitutor,
                      private val arguments: Arguments?,
                      private val context: PsiElement) {

  val argumentMapping: Map<Argument, Pair<PsiParameter, PsiType?>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
    mapArguments(typeComputer)
  }

  private val erasedArguments: Array<PsiType?>? by lazy(LazyThreadSafetyMode.PUBLICATION) {
    arguments?.map(typeComputer)?.map(TypeConversionUtil::erasure)?.toTypedArray()
  }

  fun isApplicable(substitutor: PsiSubstitutor): Boolean {
    if (substitutor.substitutionMap.isEmpty() && argumentMapping.size == method.parameters.size) { //fast pass
      if (argumentMapping.size == arguments?.size) return true
      val erasedArguments = erasedArguments ?: return true
      return checkProviders(erasedArguments, method) == applicable
    }

    return PsiUtil.isApplicable(erasedArguments, method, substitutor, context, true)
  }

  private val typeComputer: (Argument) -> PsiType? = ::computeType

  private fun computeType(argument: Argument): PsiType? {
    val type = argument.topLevelType
    return if (type is GrMapType) {
      TypesUtil.createTypeByFQClassName(CommonClassNames.JAVA_UTIL_MAP, context)
    }
    else {
      type ?: TypesUtil.getJavaLangObject(context)
    }
  }

  private val completionTypeComputer: (Argument) -> PsiType? = ::computeCompletionType

  private fun computeCompletionType(argument: Argument): PsiType? {
    return if (argument is ExpressionArgument) {
      var type = getTopLevelTypeCached(argument.expression)
      if (argument.expression is GrNewExpression && com.intellij.psi.util.PsiUtil.resolveClassInType(type) == null) {
        type = null
      }
      type
    }
    else argument.type
  }

  fun completionMapArguments(): Map<Argument, Pair<PsiParameter, PsiType?>> {
    return mapArguments(completionTypeComputer)
  }

  private fun mapArguments(typeComputer: (Argument) -> PsiType?): Map<Argument, Pair<PsiParameter, PsiType?>> {
    if (arguments == null) return emptyMap()
    val erasedSignature = GrClosureSignatureUtil.createSignature(method, siteSubstitutor, true)

    val argInfos = mapParametersToArguments(erasedSignature, arguments.toTypedArray(), typeComputer, context, true) ?: return emptyMap()

    val params = method.parameterList.parameters

    val map = HashMap<Argument, Pair<PsiParameter, PsiType?>>()

    argInfos.forEachIndexed { index, argInfo ->
      argInfo ?: return@forEachIndexed
      val param = if (index < params.size) params[index] else null ?: return@forEachIndexed
      var paramType = param.type
      if (argInfo.isMultiArg && paramType is PsiArrayType) paramType = paramType.componentType

      argInfo.args.forEach {
        map[it] = Pair(param, paramType)
      }
    }

    return map
  }
}