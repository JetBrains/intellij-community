// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver.closure

import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.PsiTypeVisitor
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.intentions.style.inference.*
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.*
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
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
          val newTypeParameter = elementFactory.createProperTypeParameter(generator.name, PsiClassType.EMPTY_ARRAY)
          virtualMethod.typeParameterList!!.add(newTypeParameter)
          parameterizedClosure.typeParameters.add(newTypeParameter)
        }
      }
      val alreadyCreatedClosureParameters = closureParameters.keys
      virtualMethod.forEachParameterUsage { parameter, instructions ->
        if (!(parameter.type.isClosureTypeDeep() && parameter !in alreadyCreatedClosureParameters)) {
          return@forEachParameterUsage
        }
        val requiredCallInstruction = instructions.firstOrNull {
          (it.element?.parentOfType<GrCall>()?.advancedResolve() as? GroovyMethodResult)?.candidate?.receiver == parameter.type
        }
        val requiredCall = requiredCallInstruction?.element?.parentOfType<GrCall>() ?: return@forEachParameterUsage
        val parameterizedClosure = ParameterizedClosure(parameter)
        closureParameters[parameter] = parameterizedClosure
        repeat(requiredCall.expressionArguments.size) {
          val newTypeParameter = elementFactory.createProperTypeParameter(generator.name, PsiClassType.EMPTY_ARRAY)
          virtualMethod.typeParameterList!!.add(newTypeParameter)
          parameterizedClosure.typeParameters.add(newTypeParameter)
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
                               constraints)
        }
        newUsageInformation
      }
    }
    val closureBodyAnalysisResult = TypeUsageInformation.merge(typeInformation)
    val constraintCollector = mutableListOf<ConstraintFormula>()
    method.forEachParameterUsage { parameter, instructions ->
      if (parameter in closureParameters.keys) {
        collectClosureParamsDependencies(constraintCollector, closureParameters.getValue(parameter), instructions)
      }
    }
    val closureParamsTypeInformation = TypeUsageInformation(
      closureParameters.flatMap { it.value.types }.toSet(),
      emptyMap(),
      constraintCollector)
    return TypeUsageInformation.merge(listOf(closureParamsTypeInformation, closureBodyAnalysisResult))
  }

  private fun createMethodFromClosureBlock(body: GrClosableBlock,
                                           param: ParameterizedClosure): GrMethod {
    val parameters = param.types
      .zip(body.parameters)
      .joinToString { (type, name) -> type.canonicalText + " " + name.text }
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
    val gatheredTypeParameters = collectDependencies(method.typeParameterList!!,
                                                     resultSubstitutor)
    for ((parameter, closureParameter) in closureParameters) {
      closureParameter.substituteTypes(resultSubstitutor, gatheredTypeParameters)
      mapping.getValue(parameter).modifierList.addAnnotation(closureParameter.renderTypes(method.parameterList).substring(1))
    }
  }

  override fun acceptReducingVisitor(visitor: PsiTypeVisitor<*>, resultMethod: GrMethod) {
    closureParameters.values.flatMap { it.types }.forEach { it.accept(visitor) }
  }

}
