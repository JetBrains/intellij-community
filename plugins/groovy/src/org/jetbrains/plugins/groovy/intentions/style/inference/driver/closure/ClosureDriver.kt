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
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.BoundConstraint.ContainMarker.*
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.RecursiveMethodAnalyzer.Companion.induceDeepConstraints
import org.jetbrains.plugins.groovy.intentions.style.inference.forceWildcardsAsTypeArguments
import org.jetbrains.plugins.groovy.intentions.style.inference.isClosureTypeDeep
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.TypeConstraint

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


  override fun createParameterizedDriver(manager: ParameterizationManager,
                                         targetMethod: GrMethod,
                                         substitutor: PsiSubstitutor): ClosureDriver {
    val parameterMapping = setUpParameterMapping(method, targetMethod)
    val newClosureParameters = mutableMapOf<GrParameter, ParameterizedClosure>()
    for ((parameter, closureParameter) in closureParameters) {
      val newParameter = parameterMapping.getValue(parameter)
      val newTypes = mutableListOf<PsiType>()
      val newTypeParameters = mutableListOf<PsiTypeParameter>()
      closureParameter.typeParameters.forEach { directInnerParameter ->
        val innerParameterType = manager.createDeeplyParameterizedType(
          substitutor.substitute(directInnerParameter)?.forceWildcardsAsTypeArguments()!!)
        newTypes.add(innerParameterType.type)
        newTypeParameters.addAll(innerParameterType.typeParameters)
        innerParameterType.typeParameters.forEach { targetMethod.typeParameterList!!.add(it) }
      }
      val newClosureParameter = ParameterizedClosure(newParameter, newTypeParameters, closureParameter.closureArguments, newTypes)
      newClosureParameters[newParameter] = newClosureParameter
    }
    return ClosureDriver(newClosureParameters, targetMethod)
  }

  override fun typeParameters(): Collection<PsiTypeParameter> {
    return closureParameters.flatMap { it.value.typeParameters }
  }


  override fun collectOuterConstraints(): Collection<ConstraintFormula> {
    val constraintCollector = mutableListOf<ConstraintFormula>()
    method.forEachParameterUsage { parameter, instructions ->
      if (parameter in closureParameters.keys) {
        collectClosureParametersConstraints(constraintCollector, closureParameters.getValue(parameter), instructions)
      }
    }
    return constraintCollector
  }


  override fun collectInnerConstraints(): TypeUsageInformation {
    val typeInformation = closureParameters.values.flatMap { parameter ->
      parameter.closureArguments.map { closureBlock ->
        val newMethod = createMethodFromClosureBlock(closureBlock, parameter)
        val commonDriver = CommonDriver.createDirectlyFromMethod(newMethod)
        val usageInformation = commonDriver.collectInnerConstraints()
        val mapping = newMethod.typeParameters.zip(method.typeParameters).toMap()
        val newUsageInformation = usageInformation.run {
          TypeUsageInformation(requiredClassTypes.map { (param, list) ->
            mapping.getValue(param) to list.map { if (it.marker == UPPER) BoundConstraint(it.type, INHABIT) else it }
          }.toMap(), constraints, dependentTypes.map { mapping.getValue(it) }.toSet())
        }
        newUsageInformation
      }
    }
    val closureBodyAnalysisResult = TypeUsageInformation.merge(typeInformation)
    val constraintCollector = mutableListOf<ConstraintFormula>()
    val requiredTypesCollector = mutableMapOf<PsiTypeParameter, MutableList<BoundConstraint>>()
    val dependentTypes = mutableSetOf<PsiTypeParameter>()
    method.forEachParameterUsage { parameter, instructions ->
      if (parameter !in closureParameters.keys) {
        return@forEachParameterUsage
      }
      collectClosureParamsDependencies(constraintCollector, closureParameters.getValue(parameter), instructions, dependentTypes,
                                       requiredTypesCollector)
      collectDelegatesToDependencies(closureParameters.getValue(parameter), instructions)
      val delegatesToCombiner = closureParameters.getValue(parameter).delegatesToCombiner
      for (usage in instructions) {
        val nearestCall = usage.element!!.parentOfType<GrCall>()
        if (nearestCall != usage.element?.parent && nearestCall != usage.element?.parent?.parent) {
          val reference = (usage.element!!.parentOfType<GrAssignmentExpression>()?.lValue as? GrReferenceExpression)?.lValueReference
          val accessorResult = (reference?.advancedResolve() as? GroovyMethodResult) ?: continue
          delegatesToCombiner.acceptResolveResult(accessorResult)
        }
        nearestCall ?: continue
        if (nearestCall.resolveMethod()?.containingClass?.qualifiedName == GroovyCommonClassNames.GROOVY_LANG_CLOSURE) {
          val resolveResult = nearestCall.advancedResolve() as? GroovyMethodResult ?: continue
          delegatesToCombiner.acceptResolveResult(resolveResult)
          if (nearestCall.resolveMethod()?.name == "call") {
            val candidate = resolveResult.candidate ?: continue
            val argumentCorrespondence =
              closureParameters[parameter]?.types?.zip(candidate.argumentMapping?.arguments ?: continue) ?: continue
            argumentCorrespondence.forEach { (type, argument) ->
              constraintCollector.add(TypeConstraint(type, argument.type, method))
              induceDeepConstraints(type, argument.type!!, dependentTypes, requiredTypesCollector, method.typeParameters.toSet(), LOWER)
            }
          }
        }
      }
    }
    val closureParamsTypeInformation = TypeUsageInformation(
      requiredTypesCollector.filter { it.key in closureParameters.flatMap { parameterizedClosure -> parameterizedClosure.value.typeParameters } },
      constraintCollector,
      dependentTypes)
    return TypeUsageInformation.merge(listOf(closureParamsTypeInformation, closureBodyAnalysisResult))
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
