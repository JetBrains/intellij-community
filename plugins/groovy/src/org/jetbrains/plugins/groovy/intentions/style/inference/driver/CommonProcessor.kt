// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver

import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.intentions.style.inference.NameGenerator
import org.jetbrains.plugins.groovy.intentions.style.inference.createProperTypeParameter
import org.jetbrains.plugins.groovy.intentions.style.inference.isClosureType
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.ExpressionConstraint
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type

class CommonProcessor constructor(private val targetParameters: Set<GrParameter>,
                                  private val varargParameter: GrParameter?) : ParametersProcessor {
  private val method = targetParameters.firstOrNull()?.parentOfType<GrMethod>()

  companion object {

    internal fun createDirectlyFromMethod(method: GrMethod): CommonProcessor {
      return CommonProcessor(method.parameters.toSet(), null)
    }

    fun createFromMethod(method: GrMethod, virtualMethod: GrMethod, generator: NameGenerator): CommonProcessor {
      val elementFactory = GroovyPsiElementFactory.getInstance(virtualMethod.project)
      val targetParameters = setUpParameterMapping(method, virtualMethod)
        .filter { it.key.typeElement == null }
        .map { it.value }
        .toSet()
      for (parameter in targetParameters) {
        val newTypeParameter = elementFactory.createProperTypeParameter(generator.name, PsiClassType.EMPTY_ARRAY)
        virtualMethod.typeParameterList!!.add(newTypeParameter)
        parameter.setType(newTypeParameter.type())
      }
      val varargParameter = targetParameters.find { it.isVarArgs }
      varargParameter?.ellipsisDots?.delete()
      return CommonProcessor(targetParameters, varargParameter)
    }
  }

  override fun createParameterizedProcessor(manager: ParameterizationManager,
                                            parameterizedMethod: GrMethod,
                                            substitutor: PsiSubstitutor): CommonProcessor {
    if (method == null) {
      return CommonProcessor(emptySet(), null)
    }
    if (varargParameter != null) {
      parameterizedMethod.parameters.last().ellipsisDots?.delete()
    }
    val parameterMapping = setUpParameterMapping(method, parameterizedMethod)
    for (parameter in targetParameters) {
      val newParameter = parameterMapping.getValue(parameter)
      val isStrictInstance = parameter == varargParameter || substitutor.substitute(parameter.type).isClosureType()
      val newType = manager.createDeeplyParameterizedType(substitutor.substitute(parameter.type), isStrictInstance)
      newType.typeParameters.forEach { parameterizedMethod.typeParameterList!!.add(it) }
      if (parameter == varargParameter) {
        newParameter.setType(newType.type.createArrayType())
      }
      else {
        newParameter.setType(newType.type)
      }
    }
    return CommonProcessor(targetParameters.map { parameterMapping.getValue(it) }.toSet(),
                           parameterMapping[varargParameter])
  }

  override fun collectOuterConstraints(method: GrMethod): Collection<ConstraintFormula> {
    if (this.method == null) {
      return emptyList()
    }
    val constraintCollector = mutableListOf<ConstraintFormula>()
    for (parameter in targetParameters) {
      constraintCollector.add(ExpressionConstraint(parameter.type, parameter.initializerGroovy ?: continue))
    }
    for (call in ReferencesSearch.search(method).findAll().mapNotNull { it.element.parent as? GrExpression }) {
      constraintCollector.add(ExpressionConstraint(null, call))
    }
    return constraintCollector
  }

  override fun collectInnerConstraints(): TypeUsageInformation {
    if (method == null) {
      return TypeUsageInformation(emptySet(), emptyMap(), emptyList())
    }
    val analyzer = RecursiveMethodAnalyzer(method)
    method.accept(analyzer)
    return analyzer.buildUsageInformation()
  }

  override fun instantiate(resultMethod: GrMethod, resultSubstitutor: PsiSubstitutor) {
    if (method == null) {
      return
    }
    val parameterMapping = setUpParameterMapping(method, resultMethod)
    parameterMapping.forEach { (param, actualParameter) ->
      actualParameter.setType(resultSubstitutor.substitute(param.type))
      if (param.type is PsiArrayType) {
        actualParameter.setType(
          resultSubstitutor.substitute((param.type as PsiArrayType).componentType).createArrayType())
      }
    }
  }

  override fun acceptReducingVisitor(visitor: PsiTypeVisitor<*>, resultMethod: GrMethod) {
    resultMethod.parameters.forEach { it.type.accept(visitor) }
  }

  override fun forbiddingTypes(): List<PsiType> {
    return listOf(varargParameter?.type ?: return emptyList())
  }
}