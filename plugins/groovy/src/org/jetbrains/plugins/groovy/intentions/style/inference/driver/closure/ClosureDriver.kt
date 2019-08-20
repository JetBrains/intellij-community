// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver.closure

import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.PsiTypeVisitor
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.intentions.style.inference.NameGenerator
import org.jetbrains.plugins.groovy.intentions.style.inference.collectDependencies
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.*
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.BoundConstraint.ContainMarker.INHABIT
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.BoundConstraint.ContainMarker.UPPER
import org.jetbrains.plugins.groovy.intentions.style.inference.forceWildcardsAsTypeArguments
import org.jetbrains.plugins.groovy.intentions.style.inference.isClosureTypeDeep
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod

class ClosureDriver private constructor(private val closureParameters: Map<GrParameter, ParameterizedClosure>,
                                        private val method: GrMethod) : InferenceDriver {

  companion object {
    fun createFromMethod(method: GrMethod, virtualMethod: GrMethod, generator: NameGenerator, scope: SearchScope): InferenceDriver {
      val builder = ClosureParametersStorageBuilder(generator, virtualMethod)
      val visitedParameters = builder.extractClosuresFromOuterCalls(method, virtualMethod, scope)
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
      return if (closureParameters.isEmpty()) EmptyDriver else ClosureDriver(closureParameters, virtualMethod)
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
    return ClosureDriver(newClosureParameters, targetMethod)
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
        val newMethod = createMethodFromClosureBlock(closureBlock, parameter)
        val commonDriver = CommonDriver.createDirectlyFromMethod(newMethod)
        val usageInformation = commonDriver.collectInnerConstraints()
        val typeParameterMapping = newMethod.typeParameters.zip(method.typeParameters).toMap()
        val shiftedRequiredTypes = usageInformation.requiredClassTypes.map { (param, list) ->
          typeParameterMapping.getValue(param) to list.map { if (it.marker == UPPER) BoundConstraint(it.type, INHABIT) else it }
        }.toMap()
        val newUsageInformation = usageInformation.run {
          TypeUsageInformation(shiftedRequiredTypes, constraints, dependentTypes.map { typeParameterMapping.getValue(it) }.toSet())
        }
        newUsageInformation
      }
    }
  }

  private fun collectClosureInvocationConstraints(): TypeUsageInformation {
    val constraintCollector = mutableListOf<ConstraintFormula>()
    val requiredTypesCollector = mutableMapOf<PsiTypeParameter, MutableList<BoundConstraint>>()
    val dependentTypes = mutableSetOf<PsiTypeParameter>()
    method.forEachParameterUsage { parameter, instructions ->
      if (parameter !in closureParameters.keys) {
        return@forEachParameterUsage
      }
      analyzeClosureUsages(constraintCollector, closureParameters.getValue(parameter), instructions, dependentTypes, requiredTypesCollector)
    }
    return TypeUsageInformation(
      requiredTypesCollector.filter { it.key in closureParameters.flatMap { (_, parameterizedClosure) -> parameterizedClosure.typeParameters } },
      constraintCollector,
      dependentTypes)
  }

  override fun collectInnerConstraints(): TypeUsageInformation {
    val closureBodyAnalysisResult = TypeUsageInformation.merge(collectDeepClosureArgumentsConstraints())
    val closureInvocationConstraints = collectClosureInvocationConstraints()
    return closureInvocationConstraints + closureBodyAnalysisResult
  }

  private fun createMethodFromClosureBlock(body: GrClosableBlock,
                                           param: ParameterizedClosure): GrMethod {
    val enrichedBodyParameters = if (param.types.size == 1 && body.parameters.isEmpty()) listOf("it") else body.parameters.map { it.name }
    val parameters = param.types
      .zip(enrichedBodyParameters)
      .joinToString { (type, name) -> type.canonicalText + " " + name }
    val statements = body.statements.joinToString("\n") { it.text }
    return GroovyPsiElementFactory
      .getInstance(method.project)
      .createMethodFromText("""
        def ${method.typeParameterList!!.text} void unique_named_method($parameters) {
          $statements
        }
      """.trimIndent(), method)
  }

  override fun instantiate(resultMethod: GrMethod, resultSubstitutor: PsiSubstitutor) {
    val mapping = setUpParameterMapping(method, resultMethod)
    val gatheredTypeParameters = collectDependencies(method.typeParameterList!!, resultSubstitutor)
    for ((_, closureParameter) in closureParameters) {
      closureParameter.renderTypes(method.parameterList, resultSubstitutor, gatheredTypeParameters).forEach { (parameter, anno) ->
        if (anno.isEmpty()) {
          return@forEach
        }
        mapping.getValue(parameter).modifierList.addAnnotation(anno.substring(1))
      }
    }
  }

  override fun acceptReducingVisitor(visitor: PsiTypeVisitor<*>, resultMethod: GrMethod) {
    closureParameters.values.flatMap { it.types }.forEach { it.accept(visitor) }
  }

}
