// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.PsiTypeParameterList
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.ExpressionConstraint
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSession
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type


data class InferenceMetaData(val parameterIndex: Map<GrParameter, PsiTypeParameter>,
                             val varargParameters: Set<GrParameter>,
                             val closureParameters: Map<GrParameter, ParameterizedClosure>,
                             val method: GrMethod,
                             val virtualMethod: GrMethod,
                             val extractedExpressions: List<Pair<GrParameter, List<GrExpression>>>,
                             val defaultTypeParameterList: PsiTypeParameterList)


fun setUpNewTypeParameters(method: GrMethod, virtualMethod: GrMethod): InferenceMetaData {
  val elementFactory = GroovyPsiElementFactory.getInstance(virtualMethod.project)
  if (!virtualMethod.hasTypeParameters()) {
    virtualMethod.addAfter(elementFactory.createTypeParameterList(), virtualMethod.firstChild)
  }
  val defaultTypeParameterList = virtualMethod.typeParameterList!!.copy() as PsiTypeParameterList
  val parameterIndex = mutableMapOf<GrParameter, PsiTypeParameter>()
  val closureParameters = mutableMapOf<GrParameter, ParameterizedClosure>()
  val generator = NameGenerator(virtualMethod.typeParameters.mapNotNull { it.name })
  val extractedExpressions = run {
    val allAcceptedExpressions = extractAcceptedExpressions(method, method.parameters.filter { it.typeElement == null }, mutableSetOf())
    val proxyMapping = method.parameters.zip(virtualMethod.parameters).toMap()
    allAcceptedExpressions.map { (parameter, expressions) -> proxyMapping.getValue(parameter) to expressions }
  }

  for (parameter in virtualMethod.parameters.filter { it.typeElement == null }) {
    val newTypeParameter = elementFactory.createProperTypeParameter(generator.name, PsiClassType.EMPTY_ARRAY)
    virtualMethod.typeParameterList!!.add(newTypeParameter)
    parameterIndex[parameter] = newTypeParameter
    parameter.setType(newTypeParameter.type())
  }
  extractedExpressions
    .filter { (_, acceptedTypes) -> acceptedTypes.all { it is GrClosableBlock } && acceptedTypes.isNotEmpty() }
    .forEach { (parameter, calls) ->
      // todo: default-valued parameters
      val parameterizedClosure = ParameterizedClosure(parameter)
      closureParameters[parameter] = parameterizedClosure
      repeat((calls.first() as GrClosableBlock).allParameters.size) {
        val newTypeParameter = elementFactory.createProperTypeParameter(generator.name, PsiClassType.EMPTY_ARRAY)
        virtualMethod.typeParameterList!!.add(newTypeParameter)
        parameterizedClosure.typeParameters.add(newTypeParameter)
      }
    }
  val varargParameters = virtualMethod.parameters.mapNotNull {
    if (it.isVarArgs) {
      it.ellipsisDots!!.delete()
      it
    } else {
      null
    }
  }.toSet()
  return InferenceMetaData(parameterIndex, varargParameters, closureParameters, method, virtualMethod, extractedExpressions, defaultTypeParameterList)
}

private fun extractAcceptedExpressions(method: GrMethod,
                                       targetParameters: Collection<GrParameter>,
                                       visitedMethods: MutableSet<GrMethod>): Map<GrParameter, List<GrExpression>> {
  if (targetParameters.isEmpty()) {
    return emptyMap()
  }
  visitedMethods.add(method)
  val referencesStorage = mutableMapOf<GrParameter, MutableList<GrExpression>>()
  targetParameters.forEach { referencesStorage[it] = mutableListOf() }
  for (call in ReferencesSearch.search(method).findAll().mapNotNull { it.element.parent as? GrCall }) {
    val argumentList = call.expressionArguments + call.closureArguments
    val targetExpressions = argumentList.zip(method.parameters).filter { it.second in targetParameters }
    val insufficientExpressions = targetExpressions.filter { it.first.type?.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) ?: true }
    (targetExpressions - insufficientExpressions).forEach { referencesStorage[it.second]!!.add(it.first) }
    val enclosingMethodParameterMapping = insufficientExpressions.mapNotNull { (expression, targetParameter) ->
      val resolved = expression.reference?.resolve() as? GrParameter
      resolved?.run {
        Pair(this, targetParameter)
      }
    }.toMap()
    val enclosingMethod = call.parentOfType(GrMethod::class)
    if (enclosingMethod != null && !visitedMethods.contains(enclosingMethod)) {
      val acceptedExpressionsForEnclosingParameters = extractAcceptedExpressions(enclosingMethod, enclosingMethodParameterMapping.keys,
                                                                                 visitedMethods)
      acceptedExpressionsForEnclosingParameters.forEach { referencesStorage[enclosingMethodParameterMapping[it.key]]!!.addAll(it.value) }
    }
  }
  return referencesStorage
}


fun collectOuterCalls(virtualMethod: GrMethod,
                      closureParameters: Map<GrParameter, ParameterizedClosure>,
                      method: GrMethod,
                      session: GroovyInferenceSession) {
  val elementFactory = GroovyPsiElementFactory.getInstance(virtualMethod.project)
  val restoreTypeMapping = mutableMapOf<GrParameter, PsiTypeParameter>()
  for (parameter in closureParameters.keys) {
    // allows to resolve Closure#call
    restoreTypeMapping[parameter] = parameter.type.typeParameter()!!
    parameter.setType(elementFactory.createTypeByFQClassName(GroovyCommonClassNames.GROOVY_LANG_CLOSURE))
  }
  val innerUsages = virtualMethod.block
    ?.controlFlow
    ?.filterIsInstance<ReadWriteVariableInstruction>()
    ?.groupBy { it.element?.reference?.resolve() }
  if (innerUsages != null) {
    closureParameters.keys.forEach {
      if (innerUsages.containsKey(it)) {
        collectClosureParametersConstraints(session, closureParameters.getValue(it), innerUsages.getValue(it))
      }
      it.setType(restoreTypeMapping[it]!!.type())
    }
  }
  for (parameter in virtualMethod.parameters) {
    session.addConstraint(ExpressionConstraint(parameter.type, parameter.initializerGroovy ?: continue))
  }
  for (call in ReferencesSearch.search(method).findAll().mapNotNull { it.element.parent as? GrExpression }) {
    session.addConstraint(ExpressionConstraint(null, call))
  }
}