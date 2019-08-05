// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver.closure

import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.PsiTypeVisitor
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.intentions.style.inference.*
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.*
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.RecursiveMethodAnalyzer.Companion.setConstraints
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type

class ClosureDriver private constructor(private val closureParameters: Map<GrParameter, ParameterizedClosure>) : InferenceDriver {

  val method = closureParameters.keys.first().parentOfType<GrMethod>()!!


  companion object {
    fun createFromMethod(method: GrMethod, virtualMethod: GrMethod, generator: NameGenerator): InferenceDriver {
      val closureParameters = mutableMapOf<GrParameter, ParameterizedClosure>()
      val elementFactory = GroovyPsiElementFactory.getInstance(method.project)
      collectClosureArguments(method, virtualMethod).forEach { (parameter, calls) ->
        // todo: default-valued parameters
        val parameterizedClosure = ParameterizedClosure(parameter)
        parameterizedClosure.closureArguments.addAll(calls.map { it as GrClosableBlock })
        closureParameters[parameter] = parameterizedClosure
        repeat((calls.first() as GrClosableBlock).allParameters.size) {
          val newTypeParameter = elementFactory.createProperTypeParameter(generator.name, null)
          virtualMethod.typeParameterList!!.add(newTypeParameter)
          parameterizedClosure.typeParameters.add(newTypeParameter)
        }
      }
      val alreadyCreatedClosureParameters = closureParameters.keys
      virtualMethod.forEachParameterUsage { parameter, instructions ->
        if (!(parameter.type.isClosureTypeDeep() && parameter !in alreadyCreatedClosureParameters)) {
          return@forEachParameterUsage
        }
        val callUsages = instructions.mapNotNull { it.element?.parentOfType<GrCall>() }
        val directClosureCall = callUsages.firstOrNull {
          (it.advancedResolve() as? GroovyMethodResult)?.candidate?.receiver == parameter.type
        }
        if (directClosureCall != null) {
          val parameterizedClosure = ParameterizedClosure(parameter)
          closureParameters[parameter] = parameterizedClosure
          repeat(directClosureCall.expressionArguments.size) {
            val newTypeParameter = elementFactory.createProperTypeParameter(generator.name, null)
            virtualMethod.typeParameterList!!.add(newTypeParameter)
            parameterizedClosure.typeParameters.add(newTypeParameter)
          }
        }
        else {
          callUsages.find { call ->
            val argument = call.argumentList?.allArguments?.find { it.reference?.resolve() == parameter } ?: return@find false
            val parameterIndex = call.argumentList?.getExpressionArgumentIndex(argument as? GrExpression) ?: return@find false
            val targetParameter = call.resolveMethod()?.parameters?.get(parameterIndex) as? GrParameter ?: return@find false
            val closureParamsAnno = targetParameter.modifierList.annotations.find {
              it.qualifiedName == ParameterizedClosure.CLOSURE_PARAMS_FQ
            } ?: return@find false
            val parameterizedClosure = ParameterizedClosure(parameter)
            closureParameters[parameter] = parameterizedClosure
            repeat(availableParameterNumber(closureParamsAnno)) {
              val newTypeParameter = elementFactory.createProperTypeParameter(generator.name, null)
              virtualMethod.typeParameterList!!.add(newTypeParameter)
              parameterizedClosure.typeParameters.add(newTypeParameter)
            }
            return@find true
          }
        }
      }
      if (closureParameters.isEmpty()) {
        return EmptyDriver
      }
      else {
        return ClosureDriver(closureParameters)
      }
    }
  }


  override fun createParameterizedDriver(manager: ParameterizationManager,
                                         targetMethod: GrMethod,
                                         substitutor: PsiSubstitutor): ClosureDriver {
    val parameterMapping = setUpParameterMapping(method, targetMethod)
    val newClosureParameters = mutableMapOf<GrParameter, ParameterizedClosure>()
    for ((parameter, closureParameter) in closureParameters) {
      val newParameter = parameterMapping.getValue(parameter)
      val newClosureParameter = ParameterizedClosure(newParameter)
      newClosureParameter.closureArguments.addAll(closureParameter.closureArguments)
      closureParameter.typeParameters.forEach { directInnerParameter ->
        val innerParameterType = manager.createDeeplyParameterizedType(
          substitutor.substitute(directInnerParameter)!!)
        newClosureParameter.types.add(innerParameterType.type)
        newClosureParameter.typeParameters.addAll(innerParameterType.typeParameters)
        innerParameterType.typeParameters.forEach { targetMethod.typeParameterList!!.add(it) }
      }
      newClosureParameters[newParameter] = newClosureParameter
    }
    return ClosureDriver(newClosureParameters)
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
          TypeUsageInformation(contravariantTypes.mapNotNull { mapping[it.typeParameter()]?.type() }.toSet(),
                               requiredClassTypes.map { (param, list) -> mapping.getValue(param) to list }.toMap(),
                               constraints,
                               covariantTypes.mapNotNull { mapping[it.typeParameter()]?.type() }.toSet(),
                               dependentTypes.map { mapping.getValue(it) }.toSet())
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
      val delegatesToCombiner = closureParameters.getValue(parameter).combiner
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
              setConstraints(type, argument.type!!, dependentTypes, requiredTypesCollector, method.typeParameters.toSet())
            }
          }
        }
      }
    }
    val closureParamsTypeInformation = TypeUsageInformation(
      closureParameters.flatMap { it.value.types }.toSet(),
      requiredTypesCollector,
      constraintCollector,
      emptySet(),
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
    for ((parameter, closureParameter) in closureParameters) {
      closureParameter.substituteTypes(resultSubstitutor, gatheredTypeParameters)
      closureParameter.renderTypes(method.parameterList).forEach {
        if (it.isEmpty()) {
          return@forEach
        }
        mapping.getValue(parameter).modifierList.addAnnotation(it.substring(1))
      }
    }
  }

  override fun acceptReducingVisitor(visitor: PsiTypeVisitor<*>, resultMethod: GrMethod) {
    closureParameters.values.flatMap { it.types }.forEach { it.accept(visitor) }
  }

}
