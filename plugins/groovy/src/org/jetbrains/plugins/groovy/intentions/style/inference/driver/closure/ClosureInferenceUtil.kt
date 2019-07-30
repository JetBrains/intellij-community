// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver.closure

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.intentions.style.inference.CollectingGroovyInferenceSession
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.closure.ParameterizedClosure.Companion.FROM_ABSTRACT_TYPE_METHODS
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.closure.ParameterizedClosure.Companion.FROM_STRING
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.closure.ParameterizedClosure.Companion.MAP_ENTRY_OR_KEY_VALUE
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.closure.ParameterizedClosure.Companion.SIMPLE_TYPE
import org.jetbrains.plugins.groovy.intentions.style.inference.unreachable
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArrayInitializer
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.SignatureHintProcessor
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.ExpressionConstraint
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.MethodCallConstraint
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.TypeConstraint
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type

fun collectClosureParametersConstraints(collector: MutableList<ConstraintFormula>,
                                        closureParameter: ParameterizedClosure,
                                        instructions: List<ReadWriteVariableInstruction>) {
  for (call in instructions) {
    val nearestCall = call.element?.parentOfType<GrCall>() ?: continue
    if ((nearestCall.advancedResolve() as? GroovyMethodResult)?.candidate?.receiver == closureParameter.parameter.type) {
      for (index in nearestCall.expressionArguments.indices) {
        collector.add(ExpressionConstraint(closureParameter.typeParameters[index].type(), nearestCall.expressionArguments[index]))
      }
    }
  }
}

fun GrMethod.forEachParameterUsage(action: (GrParameter, List<ReadWriteVariableInstruction>) -> Unit) {
  val usages =
    block
      ?.controlFlow
      ?.filterIsInstance<ReadWriteVariableInstruction>()
      ?.groupBy { it.element?.reference?.resolve() } ?: return
  parameters
    .filter { usages.containsKey(it) }
    .forEach {
      val instructions = usages.getValue(it)
      action(it, instructions)
    }
}

fun collectClosureArguments(method: GrMethod, virtualMethod: GrMethod): Map<GrParameter, List<GrExpression>> {
  val allArgumentExpressions = extractArgumentExpressions(method,
                                                          method.parameters.filter { it.typeElement == null },
                                                          mutableSetOf())
  val proxyMapping = method.parameters.zip(virtualMethod.parameters).toMap()
  return allArgumentExpressions
    .filter { (_, arguments) ->
      val hasClosableBlock = arguments.fold(false) { meetBlockBefore, expression ->
        when {
          expression is GrClosableBlock -> true
          expression.type?.equalsToText(GroovyCommonClassNames.GROOVY_LANG_CLOSURE) != true -> return@filter false
          else -> meetBlockBefore
        }
      }
      hasClosableBlock
    }
    .map { (parameter, expressions) -> proxyMapping.getValue(parameter)!! to expressions.filterIsInstance<GrClosableBlock>() }
    .toMap()

}

private fun extractArgumentExpressions(method: GrMethod,
                                       targetParameters: Collection<GrParameter>,
                                       visitedMethods: MutableSet<GrMethod>): Map<GrParameter, List<GrExpression>> {
  if (targetParameters.isEmpty()) {
    return emptyMap()
  }
  visitedMethods.add(method)
  val expressionStorage = mutableMapOf<GrParameter, MutableList<GrExpression>>()
  targetParameters.forEach { expressionStorage[it] = mutableListOf() }
  //todo: rewrite to ArgumentMapping usage (or delete this part at all, it might be quite slow)
  for (call in ReferencesSearch.search(method).findAll().mapNotNull { it.element.parent as? GrCall }) {
    val argumentList = call.expressionArguments + call.closureArguments
    val targetExpressions = argumentList.zip(method.parameters).filter { it.second in targetParameters }
    val objectTypedExpressions = targetExpressions.filter { it.first.type?.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) ?: true }
    (targetExpressions - objectTypedExpressions).forEach { expressionStorage[it.second]!!.add(it.first) }
    val enclosingMethodParameterMapping = objectTypedExpressions.mapNotNull { (expression, targetParameter) ->
      val resolved = expression.reference?.resolve() as? GrParameter
      resolved?.run { this to targetParameter }
    }.toMap()
    val enclosingMethod = call.parentOfType(GrMethod::class)
    if (enclosingMethod != null && !visitedMethods.contains(enclosingMethod)) {
      val argumentsForEnclosingParameters =
        extractArgumentExpressions(enclosingMethod, enclosingMethodParameterMapping.keys, visitedMethods)
      argumentsForEnclosingParameters.forEach { expressionStorage[enclosingMethodParameterMapping[it.key]]!!.addAll(it.value) }
    }
  }
  return expressionStorage
}

fun collectClosureParamsDependencies(constraintCollector: MutableList<ConstraintFormula>,
                                     closureParameter: ParameterizedClosure,
                                     usages: List<ReadWriteVariableInstruction>) {
  val parameter = closureParameter.parameter
  val parameterType = parameter.type
  parameter.setType(PsiType.getTypeByName(GroovyCommonClassNames.GROOVY_LANG_CLOSURE, parameter.project, parameter.resolveScope))
  for (usage in usages) {
    val nearestCall = usage.element!!.parentOfType<GrCall>() ?: continue
    if (nearestCall == usage.element!!.parent && nearestCall.resolveMethod()?.containingClass?.qualifiedName == GroovyCommonClassNames.GROOVY_LANG_CLOSURE) {
      continue
    }
    val resolveResult = nearestCall.advancedResolve() as? GroovyMethodResult ?: continue
    val mapping = resolveResult.candidate?.argumentMapping ?: continue
    val innerParameter = mapping.targetParameter(
      mapping.arguments.find { it is ExpressionArgument && it.expression.reference?.resolve() == parameter } ?: continue
    ) ?: continue
    val hint = innerParameter.annotations.mapNotNull {
      GrAnnotationUtil.inferClassAttribute(it, "value")?.qualifiedName
    }.mapNotNull { SignatureHintProcessor.getHintProcessor(it) }.firstOrNull() ?: continue
    val options =
      AnnotationUtil.arrayAttributeValues(innerParameter.annotations.first().findAttributeValue("options")).mapNotNull {
        (it as? PsiLiteral)?.value as? String
      }.toTypedArray()
    val outerMethod = usage.element!!.parentOfType<GrMethod>()!!
    val innerMethod = nearestCall.resolveMethod()
    // We cannot use GroovyMethodResult#getSubstitutor because we need to leave type parameters of outerMethod among substitution variants
    val substitutor = collectGenericSubstitutor(resolveResult, outerMethod)
    val gdkGuard = when (innerMethod) {
      is GrGdkMethod -> innerMethod.staticMethod
      else -> innerMethod
    }
    val signatures = hint.inferExpectedSignatures(gdkGuard!!, substitutor, options)
    signatures.first().zip(closureParameter.typeParameters).forEach { (type, typeParameter) ->
      constraintCollector.add(TypeConstraint(typeParameter.type(), type, outerMethod))
    }
  }
  parameter.setType(parameterType)
}

private fun collectGenericSubstitutor(resolveResult: GroovyMethodResult, outerMethod: GrMethod): PsiSubstitutor {
  val outerParameters = outerMethod.typeParameters.map { it.type() }.toSet()
  val resolveSession = CollectingGroovyInferenceSession(
    outerMethod.typeParameters.filter {
      (it.extendsListTypes.run { isEmpty() || first() in outerParameters })
    }.toTypedArray(), PsiSubstitutor.EMPTY, outerMethod)
  resolveSession.addConstraint(MethodCallConstraint(null, resolveResult, outerMethod))
  for (typeParameter in outerMethod.typeParameters) {
    resolveSession.getInferenceVariable(
      resolveSession.substituteWithInferenceVariables(typeParameter.type()))?.instantiation = typeParameter.type()
  }
  return resolveSession.inferSubst(resolveResult)
}


fun availableParameterNumber(annotation: GrAnnotation): Int {
  val value = (annotation.parameterList.attributes.find { it.name == "value" }?.value?.reference?.resolve() as? PsiClass) ?: return 0
  val options = lazy {
    annotation.parameterList.attributes.find { it.name == "options" }?.value as? GrAnnotationArrayInitializer
  }
  return when (value.name) {
    SIMPLE_TYPE -> parseSimpleType(options.value ?: return 0)
    FROM_STRING -> parseFromString(options.value ?: return 0)
    in ParameterizedClosure.availableHints -> return 1
    FROM_ABSTRACT_TYPE_METHODS -> /*todo*/ return 0
    MAP_ENTRY_OR_KEY_VALUE -> /*todo*/ return 2
    else -> unreachable()
  }
}

private fun parseFromString(signatures: GrAnnotationArrayInitializer): Int {
  val signature = signatures.initializers.firstOrNull()?.text ?: return 0
  return signature.count { it == ',' } + 1
}

private fun parseSimpleType(parameterTypes: GrAnnotationArrayInitializer): Int {
  return parameterTypes.initializers.size
}


