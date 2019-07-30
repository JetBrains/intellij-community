// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver

import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.intentions.style.inference.*
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.closure.ClosureDriver
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult.OK
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter.ApplicableTo.METHOD_PARAMETER
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.ExpressionConstraint
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.MethodCallConstraint
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.TypeConstraint
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type
import org.jetbrains.plugins.groovy.lang.sam.findSingleAbstractMethod

class CommonDriver internal constructor(private val targetParameters: Set<GrParameter>,
                                        private val varargParameter: GrParameter?,
                                        private val closureDriver: InferenceDriver,
                                        private val originalMethod: GrMethod,
                                        private val typeParameters: Collection<PsiTypeParameter>) : InferenceDriver {
  private val method = targetParameters.first().parentOfType<GrMethod>()!!

  companion object {

    internal fun createDirectlyFromMethod(method: GrMethod): InferenceDriver {
      if (method.parameters.isEmpty()) {
        return EmptyDriver
      }
      else {
        return CommonDriver(method.parameters.toSet(), null, EmptyDriver, method, method.typeParameters.asList())
      }
    }

    fun createFromMethod(method: GrMethod, virtualMethod: GrMethod, generator: NameGenerator): InferenceDriver {
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
      if (targetParameters.isEmpty()) {
        return EmptyDriver
      }
      else {
        return CommonDriver(targetParameters, varargParameter, EmptyDriver, method, typeParameters)
      }
    }
  }

  override fun typeParameters(): Collection<PsiTypeParameter> {
    return typeParameters
  }


  override fun collectSignatureSubstitutor(): PsiSubstitutor {
    val mapping = setUpParameterMapping(originalMethod, method).map { it.key.name to it.value }.toMap()
    val (constraints, samParameters) = collectOuterCallsInformation()
    val inferenceSession = CollectingGroovyInferenceSession(typeParameters().toTypedArray(),
                                                            proxyMethodMapping = mapping,
                                                            ignoreClosureArguments = samParameters)
    constraints.forEach { inferenceSession.addConstraint(it) }
    return inferenceSession.inferSubst()
  }

  override fun createParameterizedDriver(manager: ParameterizationManager,
                                         targetMethod: GrMethod,
                                         substitutor: PsiSubstitutor): InferenceDriver {
    if (varargParameter != null) {
      targetMethod.parameters.last().ellipsisDots?.delete()
    }
    val parameterMapping = setUpParameterMapping(method, targetMethod)
    val typeParameters = mutableListOf<PsiTypeParameter>()
    for (parameter in targetParameters) {
      val newParameter = parameterMapping.getValue(parameter)
      val isStrictInstance = parameter == varargParameter
      val newType = manager.createDeeplyParameterizedType(substitutor.substitute(parameter.type).forceWildcardsAsTypeArguments(),
                                                          isStrictInstance)
      newType.typeParameters.forEach { targetMethod.typeParameterList!!.add(it) }
      typeParameters.addAll(newType.typeParameters)
      if (parameter == varargParameter) {
        newParameter.setType(newType.type.createArrayType())
      }
      else {
        newParameter.setType(newType.type)
      }
    }
    val copiedVirtualMethod = createVirtualMethod(targetMethod)
    val closureDriver = ClosureDriver.createFromMethod(originalMethod, copiedVirtualMethod, manager.nameGenerator)
    val subst = closureDriver.collectSignatureSubstitutor()
    val newClosureDriver = closureDriver.createParameterizedDriver(manager, targetMethod, subst)
    return CommonDriver(targetParameters.map { parameterMapping.getValue(it) }.toSet(),
                        parameterMapping[varargParameter],
                        newClosureDriver,
                        originalMethod, typeParameters)
  }

  override fun collectOuterConstraints(): Collection<ConstraintFormula> {
    return collectOuterCallsInformation().first
  }


  private fun collectOuterCallsInformation(): Pair<Collection<ConstraintFormula>, Set<GrParameter>> {
    val constraintCollector = mutableListOf<ConstraintFormula>()
    for (parameter in targetParameters) {
      constraintCollector.add(ExpressionConstraint(parameter.type, parameter.initializerGroovy ?: continue))
    }
    val candidateSamParameters = targetParameters.map { it to PsiType.NULL as PsiType }.toMap(mutableMapOf())
    val definitelySamParameters = mutableSetOf<GrParameter>()
    val mapping = setUpParameterMapping(originalMethod, method)
    for (call in ReferencesSearch.search(originalMethod).findAll().mapNotNull { it.element.parent }) {
      if (call is GrExpression) {
        constraintCollector.add(ExpressionConstraint(null, call))
        fetchSamCoercions(candidateSamParameters, definitelySamParameters, call, mapping)
      }
      else if (call is GrConstructorInvocation) {
        val resolveResult = call.constructorReference.advancedResolve()
        if (resolveResult is GroovyMethodResult) {
          constraintCollector.add(MethodCallConstraint(null, resolveResult, method))
        }
      }
    }
    method.accept(object : GroovyRecursiveElementVisitor() {
      override fun visitMethodCallExpression(methodCallExpression: GrMethodCallExpression) {
        val resolveResult = methodCallExpression.advancedResolve() as? GroovyMethodResult
        val argumentMapping = resolveResult?.candidate?.argumentMapping ?: return
        argumentMapping.expectedTypes.forEach { (type, argument) ->
          val typeParameter = (argument.type?.resolve() as? PsiTypeParameter).ensure { it in typeParameters } ?: return@forEach
          constraintCollector.add(TypeConstraint(type, typeParameter.type(), method))
        }
      }
    })
    return Pair(constraintCollector, candidateSamParameters.keys.intersect(definitelySamParameters))
  }


  private fun fetchSamCoercions(samCandidates: MutableMap<GrParameter, PsiType>,
                                samParameters: MutableSet<GrParameter>,
                                call: GrExpression,
                                mapping: Map<GrParameter, GrParameter>) {
    val argumentMapping = ((call as? GrMethodCall)?.advancedResolve() as? GroovyMethodResult)?.candidate?.argumentMapping ?: return
    argumentMapping.expectedTypes.forEach { (_, argument) ->
      val virtualParameter = mapping[argumentMapping.targetParameter(argument)]?.ensure { it in samCandidates.keys } ?: return@forEach
      val argumentType = argument.type as? PsiClassType ?: return@forEach
      if (virtualParameter in samCandidates.keys) {
        if (argument.type.isClosureTypeDeep()) {
          return@forEach
        }
        val sam = findSingleAbstractMethod(argumentType.resolve() ?: return@forEach)
        if (sam == null) {
          samCandidates.remove(virtualParameter)
        }
        else {
          samParameters.add(virtualParameter)
          if (TypesUtil.canAssign(argumentType, samCandidates[virtualParameter]!!, method, METHOD_PARAMETER) == OK) {
            samCandidates[virtualParameter] = argumentType
          }
          else if (TypesUtil.canAssign(samCandidates[virtualParameter]!!, argumentType, method, METHOD_PARAMETER) != OK) {
            samCandidates.remove(virtualParameter)
          }
        }
      }
    }
  }


  override fun collectInnerConstraints(): TypeUsageInformation {
    val typeUsageInformation = closureDriver.collectInnerConstraints()
    val analyzer = RecursiveMethodAnalyzer(method)
    method.accept(analyzer)
    return analyzer.buildUsageInformation() + typeUsageInformation
  }

  override fun instantiate(resultMethod: GrMethod, resultSubstitutor: PsiSubstitutor) {
    if (resultMethod.parameters.last().typeElementGroovy == null) {
      resultMethod.parameters.last().ellipsisDots?.delete()
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