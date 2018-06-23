// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.openapi.util.Pair
import com.intellij.psi.*
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil.mapParametersToArguments
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil

class MethodCandidate(val method: PsiMethod,
                      val siteSubstitutor: PsiSubstitutor,
                      val qualifier: Argument?,
                      val arguments: List<Argument>,
                      val context: GroovyPsiElement) {

  private val typeComputer: (Argument) -> PsiType? = { it ->
    val type = if (it.expression != null) getTopLevelType(it.expression) else it.type

    type ?: TypesUtil.getJavaLangObject(context)
  }

  private val completionTypeComputer: (Argument) -> PsiType? = { it ->
    if (it.expression != null) {
      var type = getTopLevelType(it.expression)
      if (it.expression is GrNewExpression && com.intellij.psi.util.PsiUtil.resolveClassInType(type) == null) {
        type = null
      }
      type
    }
    else it.type
  }

  fun mapArguments(): Map<Argument, Pair<PsiParameter, PsiType?>> {
    return mapArguments(typeComputer)
  }

  fun completionMapArguments(): Map<Argument, Pair<PsiParameter, PsiType?>> {
    return mapArguments(completionTypeComputer)
  }

  private fun mapArguments(typeComputer:(Argument) -> PsiType?): Map<Argument, Pair<PsiParameter, PsiType?>> {
    val erasedSignature = GrClosureSignatureUtil.createSignature(method, siteSubstitutor, true) // check it

    val argInfos = mapParametersToArguments(erasedSignature, arguments.toTypedArray(), typeComputer, context, true) ?: return emptyMap()

    val params = method.parameterList.parameters

    val map = HashMap<Argument, Pair<PsiParameter, PsiType?>>()

    argInfos.forEachIndexed { index, argInfo ->
      argInfo ?: return@forEachIndexed
      val param = if (index < params.size)  params[index] else null ?: return@forEachIndexed
      var paramType = param.type
      if (argInfo.isMultiArg && paramType is PsiArrayType) paramType = paramType.componentType

      argInfo.args.forEach {
        map[it] = Pair(param, paramType)
      }
    }

    return map
  }

  fun getArgumentTypes(): Array<PsiType?> {
    return arguments.map(typeComputer).toTypedArray()
  }
}