// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver.closure

import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.intentions.style.inference.*
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.*
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.BoundConstraint.ContainMarker.INHABIT
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.BoundConstraint.ContainMarker.UPPER
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod

class ClosureDriver private constructor(private val closureParameters: Map<GrParameter, ParameterizedClosure>,
                                        private val method: GrMethod,
                                        private val environment: SignatureInferenceEnvironment) : InferenceDriver {

  companion object {
    fun createFromMethod(method: GrMethod,
                         virtualMethodPointer: SmartPsiElementPointer<GrMethod>,
                         generator: NameGenerator,
                         environment: SignatureInferenceEnvironment): InferenceDriver {
      val virtualMethod = virtualMethodPointer.element ?: return EmptyDriver
      val builder = ClosureParametersStorageBuilder(generator, virtualMethod)
      val visitedParameters = builder.extractClosuresFromOuterCalls(method, virtualMethod, environment.getAllCallsToMethod(virtualMethod))
      virtualMethod.forEachParameterUsage { parameter, instructions ->
        if (!parameter.type.isClosureTypeDeep() || parameter in visitedParameters) {
          return@forEachParameterUsage
        }
        val callUsages = instructions.mapNotNull { it.element?.parentOfType<GrCall>() }
        if (!builder.extractClosuresFromCallInvocation(callUsages, parameter)) {
          builder.extractClosuresFromOtherMethodInvocations(callUsages, parameter)
        }
      }
      val closureParameters = builder.build()
      return if (closureParameters.isEmpty()) EmptyDriver else ClosureDriver(closureParameters, virtualMethod, environment)
    }
  }


  private fun produceParameterizedClosure(parameterMapping: Map<GrParameter, GrParameter>,
                                          closureParameter: ParameterizedClosure,
                                          substitutor: PsiSubstitutor,
                                          manager: ParameterizationManager): ParameterizedClosure {
    val newParameter = parameterMapping.getValue(closureParameter.parameter)
    val newTypes = mutableListOf<PsiType>()
    val newTypeParameters = mutableListOf<PsiTypeParameter>()
    for (directInnerParameter in closureParameter.typeParameters) {
      val substitutedType = substitutor.substitute(directInnerParameter)?.forceWildcardsAsTypeArguments()
      val generifiedType = substitutedType ?: getJavaLangObject(closureParameter.parameter)
      val (createdType, createdTypeParameters) = manager.createDeeplyParameterizedType(generifiedType)
      newTypes.add(createdType)
      newTypeParameters.addAll(createdTypeParameters)
    }
    return ParameterizedClosure(newParameter, newTypeParameters, closureParameter.closureArguments, newTypes)
  }


  override fun createParameterizedDriver(manager: ParameterizationManager,
                                         targetMethod: GrMethod,
                                         substitutor: PsiSubstitutor): InferenceDriver {
    val parameterMapping = setUpParameterMapping(method, targetMethod)
    val newClosureParameters = mutableMapOf<GrParameter, ParameterizedClosure>()
    val typeParameterList = targetMethod.typeParameterList ?: return EmptyDriver
    for ((_, closureParameter) in closureParameters) {
      val newClosureParameter = produceParameterizedClosure(parameterMapping, closureParameter, substitutor, manager)
      newClosureParameter.typeParameters.forEach { typeParameterList.add(it) }
      newClosureParameters[newClosureParameter.parameter] = newClosureParameter
    }
    return ClosureDriver(newClosureParameters, targetMethod, environment)
  }

  override fun typeParameters(): Collection<PsiTypeParameter> {
    return closureParameters.flatMap { it.value.typeParameters }
  }


  override fun collectOuterConstraints(): Collection<ConstraintFormula> {
    val constraintCollector = mutableListOf<ConstraintFormula>()
    method.forEachParameterUsage { parameter, instructions ->
      if (parameter in closureParameters) {
        constraintCollector.addAll(extractConstraintsFromClosureInvocations(closureParameters.getValue(parameter), instructions))
      }
    }
    return constraintCollector
  }


  private fun collectDeepClosureArgumentsConstraints(): List<TypeUsageInformation> {
    return closureParameters.values.flatMap { parameter ->
      parameter.closureArguments.map { closureBlock ->
        val newMethod = createMethodFromClosureBlock(closureBlock, parameter, method.typeParameterList!!)
        val emptyEnvironment =
          SignatureInferenceEnvironment(method, GlobalSearchScope.EMPTY_SCOPE, environment.signatureInferenceContext, true)
        val commonDriver = CommonDriver.createDirectlyFromMethod(newMethod, emptyEnvironment)
        val usageInformation = commonDriver.collectInnerConstraints()
        val typeParameterMapping = newMethod.typeParameters.zip(method.typeParameters).toMap()
        val shiftedRequiredTypes = usageInformation.requiredClassTypes.map { (param, list) ->
          typeParameterMapping.getValue(param) to list.map { if (it.marker == UPPER) BoundConstraint(it.type, INHABIT) else it }
        }.toMap()
        val newUsageInformation = usageInformation.run {
          TypeUsageInformation(shiftedRequiredTypes, constraints, dependentTypes.map { typeParameterMapping.getValue(it) }.toSet(),
                               constrainingExpressions)
        }
        newUsageInformation
      }
    }
  }

  private fun collectClosureInvocationConstraints(): TypeUsageInformation {
    val builder = TypeUsageInformationBuilder(method, environment.signatureInferenceContext)
    method.forEachParameterUsage { parameter, instructions ->
      if (parameter in closureParameters.keys) {
        analyzeClosureUsages(closureParameters.getValue(parameter), instructions, builder)
      }
    }
    return builder.build()
  }

  override fun collectInnerConstraints(): TypeUsageInformation {
    val closureBodyAnalysisResult = TypeUsageInformation.merge(collectDeepClosureArgumentsConstraints())
    val closureInvocationConstraints = collectClosureInvocationConstraints()
    return closureInvocationConstraints + closureBodyAnalysisResult
  }

  override fun instantiate(resultMethod: GrMethod) {
    val mapping = setUpParameterMapping(method, resultMethod)
    for ((_, closureParameter) in closureParameters) {
      val createdAnnotations = closureParameter.renderTypes(method.parameterList)
      for ((parameter, anno) in createdAnnotations) {
        if (anno.isEmpty()) {
          continue
        }
        mapping.getValue(parameter).modifierList.addAnnotation(anno.substring(1))
      }
    }
  }

  override fun acceptTypeVisitor(visitor: PsiTypeMapper, resultMethod: GrMethod): InferenceDriver {
    val mapping = setUpParameterMapping(method, resultMethod)
    val parameterizedClosureCollector = mutableListOf<Pair<GrParameter, ParameterizedClosure>>()
    for ((parameter, closure) in closureParameters) {
      val newTypes = closure.types.map { it.accept(visitor) }
      val newParameter = mapping.getValue(parameter)
      val newParameterizedClosure = ParameterizedClosure(newParameter, closure.typeParameters, closure.closureArguments, newTypes, closure.delegatesToCombiner)
      parameterizedClosureCollector.add(newParameter to newParameterizedClosure)
    }
    return ClosureDriver(parameterizedClosureCollector.toMap(), resultMethod, environment)
  }

}
