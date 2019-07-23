// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver

import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.intentions.style.inference.CollectingGroovyInferenceSession
import org.jetbrains.plugins.groovy.intentions.style.inference.NameGenerator
import org.jetbrains.plugins.groovy.intentions.style.inference.createProperTypeParameter
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.closure.ClosureDriver
import org.jetbrains.plugins.groovy.intentions.style.inference.isClosureType
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.ExpressionConstraint
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type

class CommonDriver constructor(private val targetParameters: Set<GrParameter>,
                               private val varargParameter: GrParameter?,
                               private val closureDriver: InferenceDriver,
                               private val originalMethod: GrMethod,
                               private val typeParameters: Collection<PsiTypeParameter>) : InferenceDriver {
  private val method = targetParameters.firstOrNull()?.parentOfType<GrMethod>()

  companion object {

    internal fun createDirectlyFromMethod(method: GrMethod): CommonDriver {
      return CommonDriver(method.parameters.toSet(), null, EmptyDriver, method, method.typeParameters.asList())
    }

    fun createFromMethod(method: GrMethod, virtualMethod: GrMethod, generator: NameGenerator): CommonDriver {
      val elementFactory = GroovyPsiElementFactory.getInstance(virtualMethod.project)
      val targetParameters = setUpParameterMapping(method, virtualMethod)
        .filter { it.key.typeElement == null }
        .map { it.value }
        .toSet()
      val typeParameters = mutableListOf<PsiTypeParameter>()
      for (parameter in targetParameters) {
        val newTypeParameter = elementFactory.createProperTypeParameter(generator.name, PsiClassType.EMPTY_ARRAY)
        typeParameters.add(newTypeParameter)
        virtualMethod.typeParameterList!!.add(newTypeParameter)
        parameter.setType(newTypeParameter.type())
      }
      val varargParameter = targetParameters.find { it.isVarArgs }
      varargParameter?.ellipsisDots?.delete()
      return CommonDriver(targetParameters, varargParameter, EmptyDriver, method, typeParameters)
    }
  }

  override fun typeParameters(): Collection<PsiTypeParameter> {
    return typeParameters
  }


  override fun collectSignatureSubstitutor(): PsiSubstitutor {
    val mapping = setUpParameterMapping(originalMethod, method!!).map { it.key.name to it.value }.toMap()
    val inferenceSession = CollectingGroovyInferenceSession(typeParameters().toTypedArray(), proxyMethodMapping = mapping)
    val constraints = collectOuterConstraints()
    constraints.forEach { inferenceSession.addConstraint(it) }
    return inferenceSession.inferSubst()
  }

  override fun createParameterizedDriver(manager: ParameterizationManager,
                                         parameterizedMethod: GrMethod,
                                         substitutor: PsiSubstitutor): InferenceDriver {
    if (method == null) {
      return EmptyDriver
    }
    if (varargParameter != null) {
      parameterizedMethod.parameters.last().ellipsisDots?.delete()
    }
    val parameterMapping = setUpParameterMapping(method, parameterizedMethod)
    val typeParameters = mutableListOf<PsiTypeParameter>()
    for (parameter in targetParameters) {
      val newParameter = parameterMapping.getValue(parameter)
      val isStrictInstance = parameter == varargParameter || substitutor.substitute(parameter.type).isClosureType()
      val newType = manager.createDeeplyParameterizedType(substitutor.substitute(parameter.type), isStrictInstance)
      newType.typeParameters.forEach { parameterizedMethod.typeParameterList!!.add(it) }
      typeParameters.addAll(newType.typeParameters)
      if (parameter == varargParameter) {
        newParameter.setType(newType.type.createArrayType())
      }
      else {
        newParameter.setType(newType.type)
      }
    }
    val closureDriver = ClosureDriver.createFromMethod(originalMethod, parameterizedMethod, manager.nameGenerator)
    val subst = closureDriver.collectSignatureSubstitutor()
    val newClosureDriver = closureDriver.createParameterizedDriver(manager, parameterizedMethod, subst)
    return CommonDriver(targetParameters.map { parameterMapping.getValue(it) }.toSet(),
                        parameterMapping[varargParameter],
                        newClosureDriver,
                        originalMethod, typeParameters)
  }

  override fun collectOuterConstraints(): Collection<ConstraintFormula> {
    if (this.method == null) {
      return emptyList()
    }
    val constraintCollector = mutableListOf<ConstraintFormula>()
    for (parameter in targetParameters) {
      constraintCollector.add(ExpressionConstraint(parameter.type, parameter.initializerGroovy ?: continue))
    }
    for (call in ReferencesSearch.search(originalMethod).findAll().mapNotNull { it.element.parent as? GrExpression }) {
      constraintCollector.add(ExpressionConstraint(null, call))
    }
    return constraintCollector
  }

  override fun collectInnerConstraints(): TypeUsageInformation {
    if (method == null) {
      return TypeUsageInformation(emptySet(), emptyMap(), emptyList())
    }
    val typeUsageInformation = closureDriver.collectInnerConstraints()
    val analyzer = RecursiveMethodAnalyzer(method)
    method.accept(analyzer)
    return analyzer.buildUsageInformation() + typeUsageInformation
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
    closureDriver.instantiate(resultMethod, resultSubstitutor)
  }

  override fun acceptReducingVisitor(visitor: PsiTypeVisitor<*>, resultMethod: GrMethod) {
    resultMethod.parameters.forEach { it.type.accept(visitor) }
    closureDriver.acceptReducingVisitor(visitor, resultMethod)
  }

  override fun forbiddingTypes(): List<PsiType> {
    return listOf(varargParameter?.type ?: return emptyList())
  }
}