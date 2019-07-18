// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver.closure

import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.intentions.style.inference.NameGenerator
import org.jetbrains.plugins.groovy.intentions.style.inference.collectDependencies
import org.jetbrains.plugins.groovy.intentions.style.inference.createProperTypeParameter
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.ParameterizationManager
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.ParametersProcessor
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.TypeUsageInformation
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.setUpParameterMapping
import org.jetbrains.plugins.groovy.intentions.style.inference.typeParameter
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.ExpressionConstraint
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type

class ClosureProcessor private constructor(private val closureParameters: Map<GrParameter, ParameterizedClosure>) : ParametersProcessor {
  val method = closureParameters.keys.firstOrNull()?.parentOfType<GrMethod>()


  companion object {
    fun createFromMethod(method: GrMethod, virtualMethod: GrMethod, generator: NameGenerator): ClosureProcessor {
      val closureParameters = mutableMapOf<GrParameter, ParameterizedClosure>()
      val elementFactory = GroovyPsiElementFactory.getInstance(method.project)
      collectClosureArguments(method, virtualMethod).forEach { (parameter, calls) ->
        // todo: default-valued parameters
        val parameterizedClosure = ParameterizedClosure(parameter)
        closureParameters[parameter] = parameterizedClosure
        repeat((calls.first() as GrClosableBlock).allParameters.size) {
          val newTypeParameter = elementFactory.createProperTypeParameter(generator.name, PsiClassType.EMPTY_ARRAY)
          virtualMethod.typeParameterList!!.add(newTypeParameter)
          parameterizedClosure.typeParameters.add(newTypeParameter)
        }
      }
      return ClosureProcessor(closureParameters)
    }
  }


  override fun createParameterizedProcessor(manager: ParameterizationManager,
                                            parameterizedMethod: GrMethod,
                                            substitutor: PsiSubstitutor): ClosureProcessor {
    if (method == null) {
      return ClosureProcessor(emptyMap())
    }
    val parameterMapping = setUpParameterMapping(method, parameterizedMethod)
    val newClosureParameters = mutableMapOf<GrParameter, ParameterizedClosure>()
    for ((parameter, closureParameter) in closureParameters) {
      val newParameter = parameterMapping.getValue(parameter)
      val newClosureParameter = ParameterizedClosure(newParameter)
      closureParameter.typeParameters.forEach { directInnerParameter ->
        val innerParameterType = manager.createDeeplyParameterizedType(substitutor.substitute(directInnerParameter)!!)
        newClosureParameter.types.add(innerParameterType.type)
        newClosureParameter.typeParameters.addAll(innerParameterType.typeParameters)
        innerParameterType.typeParameters.forEach { parameterizedMethod.typeParameterList!!.add(it) }
      }
      newClosureParameters[newParameter] = newClosureParameter
    }
    return ClosureProcessor(newClosureParameters)
  }


  override fun collectOuterConstraints(method: GrMethod): Collection<ConstraintFormula> {
    if (this.method == null) {
      return emptyList()
    }
    val constraintCollector = mutableListOf<ConstraintFormula>()
    val closure = PsiType.getTypeByName(GroovyCommonClassNames.GROOVY_LANG_CLOSURE, this.method.project, this.method.resolveScope)
    val restoreTypeMapping = mutableMapOf<GrParameter, PsiTypeParameter>()
    for (parameter in closureParameters.keys) {
      // allows to resolve Closure#call
      restoreTypeMapping[parameter] = parameter.type.typeParameter()!!
      parameter.setType(closure)
    }
    val innerUsages = this.method.block
      ?.controlFlow
      ?.filterIsInstance<ReadWriteVariableInstruction>()
      ?.groupBy { it.element?.reference?.resolve() }
    if (innerUsages != null) {
      closureParameters
        .keys
        .forEach {
          if (innerUsages.containsKey(it)) {
            collectClosureParametersConstraints(constraintCollector, closureParameters.getValue(it), innerUsages.getValue(it))
          }
          it.setType(restoreTypeMapping[it]!!.type())
        }
    }
    return constraintCollector
  }


  override fun collectInnerConstraints(): TypeUsageInformation {
    if (method == null) {
      return TypeUsageInformation(emptySet(), emptyMap(), emptyList())
    }
    val constraintCollector = mutableListOf<ConstraintFormula>()
    method.block?.controlFlow
      ?.filterIsInstance<ReadWriteVariableInstruction>()
      ?.groupBy { it.element?.reference?.resolve() }
      ?.forEach { (parameter, usages) ->
        if (parameter is GrParameter && parameter in closureParameters.keys) {
          collectClosureParamsDependencies(constraintCollector, closureParameters.getValue(parameter), usages, this)
        }
      }
    return TypeUsageInformation(
      closureParameters.flatMap { it.value.types }.toSet(),
      emptyMap(),
      constraintCollector)
  }

  override fun instantiate(resultMethod: GrMethod, resultSubstitutor: PsiSubstitutor) {
    if (method == null) {
      return
    }
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

  fun processCall(callExpression: GrCall, constraintsCollector: MutableList<ConstraintFormula>) {
    val parameter = callExpression.firstChild?.run {
      closureParameters[reference?.resolve() as? GrParameter ?: firstChild?.reference?.resolve()]
    }
    parameter?.run {
      callExpression.expressionArguments.zip(parameter.types).forEach { (expression, parameterType) ->
        constraintsCollector.add(ExpressionConstraint(parameterType, expression))
      }
    }
  }
}
