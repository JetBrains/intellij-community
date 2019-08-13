// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver.closure

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.intentions.style.inference.*
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.BoundConstraint
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.BoundConstraint.ContainMarker.LOWER
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.closure.ClosureParamsCombiner.Companion.FROM_ABSTRACT_TYPE_METHODS
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.closure.ClosureParamsCombiner.Companion.FROM_STRING
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.closure.ClosureParamsCombiner.Companion.MAP_ENTRY_OR_KEY_VALUE
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.closure.ClosureParamsCombiner.Companion.SIMPLE_TYPE
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArrayInitializer
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.stringValue
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.ClosureSyntheticParameter
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.SignatureHintProcessor
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.ExpressionConstraint
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.TypeConstraint
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type

fun collectClosureParametersConstraints(collector: MutableList<ConstraintFormula>,
                                        closureParameter: ParameterizedClosure,
                                        instructions: List<ReadWriteVariableInstruction>) {
  for (call in instructions) {
    val nearestCall = call.element?.parentOfType<GrCall>() ?: continue
    val isClosureCall =
      (nearestCall.advancedResolve() as? GroovyMethodResult)?.candidate?.run {
        receiver == closureParameter.parameter.type && method.name == "call"
      } ?: false
    if (isClosureCall) {
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

fun collectClosureArguments(method: GrMethod, virtualMethod: GrMethod, scope: SearchScope): Map<GrParameter, List<GrExpression>> {
  val allArgumentExpressions = extractArgumentExpressions(method,
                                                          method.parameters.filter { it.typeElement == null },
                                                          mutableSetOf(), scope)
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
                                       visitedMethods: MutableSet<GrMethod>, scope: SearchScope): Map<GrParameter, List<GrExpression>> {
  if (targetParameters.isEmpty()) {
    return emptyMap()
  }
  visitedMethods.add(method)
  val expressionStorage = mutableMapOf<GrParameter, MutableList<GrExpression>>()
  targetParameters.forEach { expressionStorage[it] = mutableListOf() }
  //todo: rewrite to ArgumentMapping usage (or delete this part at all, it might be quite slow)
  for (call in ReferencesSearch.search(method, scope).findAll().mapNotNull { it.element.parent as? GrCall }) {
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
        extractArgumentExpressions(enclosingMethod, enclosingMethodParameterMapping.keys, visitedMethods, scope)
      argumentsForEnclosingParameters.forEach { expressionStorage[enclosingMethodParameterMapping[it.key]]!!.addAll(it.value) }
    }
  }
  return expressionStorage
}

fun collectClosureParamsDependencies(constraintCollector: MutableList<ConstraintFormula>,
                                     closureParameter: ParameterizedClosure,
                                     usages: List<ReadWriteVariableInstruction>,
                                     dependentTypes: MutableSet<PsiTypeParameter>,
                                     requiredTypesCollector: MutableMap<PsiTypeParameter, MutableList<BoundConstraint>>) {
  val parameter = closureParameter.parameter
  val parameterType = parameter.type
  val outerMethod = parameter.parentOfType<GrMethod>()!!
  parameter.setType(PsiType.getTypeByName(GroovyCommonClassNames.GROOVY_LANG_CLOSURE, parameter.project, parameter.resolveScope))
  usages.forEachClosureForwarding { resolveResult ->
    val mapping = resolveResult.candidate?.argumentMapping ?: return@forEachClosureForwarding
    val innerParameter = mapping.targetParameter(
      mapping.arguments.find { it is ExpressionArgument && it.expression.reference?.resolve() == parameter }
      ?: return@forEachClosureForwarding
    ) ?: return@forEachClosureForwarding
    val hint = innerParameter.annotations.mapNotNull {
      GrAnnotationUtil.inferClassAttribute(it, "value")?.qualifiedName
    }.mapNotNull { SignatureHintProcessor.getHintProcessor(it) }.firstOrNull() ?: return@forEachClosureForwarding
    val options =
      AnnotationUtil.arrayAttributeValues(innerParameter.annotations.first().findAttributeValue("options")).mapNotNull {
        (it as? PsiLiteral)?.value as? String
      }.toTypedArray()
    val gdkGuard = when (val innerMethod = resolveResult.candidate?.method) {
      is GrGdkMethod -> innerMethod.staticMethod
      else -> innerMethod
    }
    val signatures = hint.inferExpectedSignatures(gdkGuard!!, resolveResult.substitutor, options)
    signatures.first().zip(closureParameter.typeParameters).forEach { (type, typeParameter) ->
      constraintCollector.add(TypeConstraint(typeParameter.type(), type, outerMethod))
      if (type.isTypeParameter()) {
        dependentTypes.add(typeParameter)
        dependentTypes.add(type.typeParameter()!!)
      }
      else {
        requiredTypesCollector.computeIfAbsent(typeParameter) { mutableListOf() }.add(BoundConstraint(type, LOWER))
      }
    }
  }
  parameter.setType(parameterType)
}

fun collectDelegatesToDependencies(closureParameter: ParameterizedClosure,
                                   usages: List<ReadWriteVariableInstruction>) {
  val parameter = closureParameter.parameter
  val combiner = closureParameter.delegatesToCombiner
  usages.forEachClosureForwarding { resolveResult ->
    val mapping = resolveResult.candidate?.argumentMapping ?: return@forEachClosureForwarding
    val innerParameter = mapping.targetParameter(
      mapping.arguments.find { it is ExpressionArgument && it.expression.reference?.resolve() == parameter }
      ?: return@forEachClosureForwarding
    ) ?: return@forEachClosureForwarding
    val annotation = innerParameter.annotations.find { it.qualifiedName == "groovy.lang.DelegatesTo" } ?: return@forEachClosureForwarding
    val valueAttribute = annotation.findDeclaredAttributeValue("value")?.reference?.resolve() as? PsiClass
    val typeAttribute = annotation.findDeclaredAttributeValue("type")?.stringValue()
    val strategyAttribute = annotation.findDeclaredAttributeValue("strategy")?.run(::extractIntValue)
    if (valueAttribute != null && valueAttribute.name != "DelegatesTo.Target") {
      combiner.setDelegate(valueAttribute)
    }
    else if (typeAttribute != null) {
      createTypeSignature(typeAttribute, resolveResult.substitutor, innerParameter)?.run {
        combiner.setTypeDelegate(this)
      }
    }
    else {
      val delegateParameters = resolveResult.candidate?.method?.parameters?.mapNotNull { param ->
        param.annotations.find { anno -> anno.qualifiedName == "groovy.lang.DelegatesTo.Target" }?.run { param to this }
      } ?: return@forEachClosureForwarding
      val targetLiteral = annotation.findDeclaredAttributeValue("target")
      val targetParameter = if (targetLiteral != null) {
        delegateParameters.find { (_, anno) ->
          if (anno !is GrAnnotation) return@find false
          anno.parameterList.attributes.find { it.name == "value" || it.name == null }?.value?.stringValue() == targetLiteral.stringValue()
        }
      }
      else {
        delegateParameters.firstOrNull()
      }?.first
      val argument = mapping.arguments.find { mapping.targetParameter(it) == targetParameter } ?: return@forEachClosureForwarding
      val argumentExpression = (argument as? ExpressionArgument)?.expression
      if (argumentExpression != null) {
        combiner.setDelegate(argumentExpression)
      }
      else {
        combiner.setDelegate(argument.type.resolve() ?: return@forEachClosureForwarding)
      }
    }
    if (strategyAttribute != null) {
      combiner.setStrategy(strategyAttribute)
    }
  }

}

private fun extractIntValue(attribute: PsiAnnotationMemberValue): String? {
  return when (attribute) {
    is PsiLiteralValue -> (attribute.value as? Int).toString()
    is GrExpression -> attribute.text
    else -> null
  }
}

fun createTypeSignature(signatureRepresentation: String, substitutor: PsiSubstitutor, context: PsiElement): PsiType? {
  val newType = JavaPsiFacade.getElementFactory(context.project).createTypeFromText("UnknownType<$signatureRepresentation>", context)
  return (newType as PsiClassType).parameters.map { substitutor.substitute(it) }.singleOrNull()
}


private fun List<ReadWriteVariableInstruction>.forEachClosureForwarding(action: (GroovyMethodResult) -> Unit) {
  for (usage in this) {
    val nearestCall = usage.element!!.parentOfType<GrCall>() ?: continue
    if (nearestCall == usage.element!!.parent && nearestCall.resolveMethod()?.containingClass?.qualifiedName == GroovyCommonClassNames.GROOVY_LANG_CLOSURE) {
      continue
    }
    val resolveResult = nearestCall.advancedResolve() as? GroovyMethodResult ?: continue
    action(resolveResult)
  }

}


fun availableParameterNumber(annotation: GrAnnotation): Int {
  val value = (annotation.parameterList.attributes.find { it.name == "value" }?.value?.reference?.resolve() as? PsiClass) ?: return 0
  val options = lazy {
    annotation.parameterList.attributes.find { it.name == "options" }?.value as? GrAnnotationArrayInitializer
  }
  return when (value.name) {
    SIMPLE_TYPE -> parseSimpleType(options.value ?: return 0)
    FROM_STRING -> parseFromString(options.value ?: return 0)
    in ClosureParamsCombiner.availableHints -> return 1
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


data class AnnotatingResult(val parameter: GrParameter, val annotationText: String)


fun getBlock(parameter: GrParameter): GrClosableBlock? = when (parameter) {
  is ClosureSyntheticParameter -> parameter.closure
  else -> {
    with(parameter.parentOfType<GrClosableBlock>()) { return if (this == null) null else extractBlock(this, parameter) }
  }
}

private fun extractBlock(block: GrClosableBlock, parameter: GrParameter): GrClosableBlock? {
  if (block.parameterList.getParameterNumber(parameter) == -1) {
    val outerBlock = block.parentOfType<GrClosableBlock>()
    if (outerBlock != null) {
      return extractBlock(block, parameter)
    }
    else {
      return null
    }
  }
  else {
    return block
  }
}

fun inferTypeFromTypeHint(parameter: GrParameter): PsiType? {
  val closureBlock = getBlock(parameter) ?: return null
  val index = if (parameter is ClosureSyntheticParameter) 0 else closureBlock.parameterList.getParameterNumber(parameter)
  val methodCall = closureBlock?.parentOfType<GrCall>() ?: return null
  val method = (methodCall.resolveMethod() as? GrMethod)
                 ?.takeIf { method -> method.parameters.any { it.typeElement == null } } ?: return null
  val (virtualMethod, substitutor) = MethodParameterAugmenter.createInferenceResult(method) ?: return null
  val resolveResult = methodCall.advancedResolve() as? GroovyMethodResult ?: return null
  val methodParameter = (resolveResult.candidate?.argumentMapping?.targetParameter(
    ExpressionArgument(closureBlock)) as? GrParameter)?.takeIf { it.typeElement == null } ?: return null
  val virtualParameter = virtualMethod?.parameters?.getOrNull(method.parameterList.getParameterNumber(methodParameter)) ?: return null
  val anno = virtualParameter.modifierList.annotations.find { it.shortName == ClosureParamsCombiner.CLOSURE_PARAMS }
             ?: return null
  val className = (anno.findAttributeValue("value") as? GrReferenceExpression)?.qualifiedReferenceName ?: return null
  val processor = SignatureHintProcessor.getHintProcessor(className) ?: return null
  val options = AnnotationUtil.arrayAttributeValues(anno.findAttributeValue("options")).mapNotNull { (it as? PsiLiteral)?.stringValue() }
  val completeContextSubstitutor = substitutor compose resolveResult.substitutor
  val signatures = processor.inferExpectedSignatures(virtualMethod, completeContextSubstitutor, options.toTypedArray())
  val parameters = closureBlock.allParameters
  return signatures.singleOrNull { it.size == parameters.size }?.getOrNull(index)
}


infix fun PsiSubstitutor.compose(right: PsiSubstitutor): PsiSubstitutor {
  val typeParameters = substitutionMap.keys
  var newSubstitutor = PsiSubstitutor.EMPTY
  typeParameters.forEach { typeParameter ->
    newSubstitutor = newSubstitutor.put(typeParameter, right.substitute(this.substitute(typeParameter)))
  }
  return newSubstitutor
}

fun PsiType.any(predicate: (PsiType) -> Boolean): Boolean {
  var mark = false
  accept(object : PsiTypeVisitor<Unit>() {
    override fun visitClassType(classType: PsiClassType?) {
      if (predicate(classType ?: return)) {
        mark = true
      }
      classType.parameters.forEach { it.accept(this) }
    }

    override fun visitWildcardType(wildcardType: PsiWildcardType?) {
      wildcardType?.bound?.accept(this)
    }

    override fun visitIntersectionType(intersectionType: PsiIntersectionType?) {
      intersectionType?.conjuncts?.forEach { it.accept(this) }
    }

    override fun visitArrayType(arrayType: PsiArrayType?) {
      arrayType?.componentType?.accept(this)
    }
  })
  return mark
}