// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.driver.closure

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.lang.jvm.JvmParameter
import com.intellij.lang.jvm.annotation.JvmAnnotationConstantValue
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.intentions.closure.isClosureCall
import org.jetbrains.plugins.groovy.intentions.closure.isClosureCallMethod
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.BoundConstraint
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.BoundConstraint.ContainMarker.LOWER
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.RecursiveMethodAnalyzer
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.closure.ClosureParametersStorageBuilder.Companion.isReferenceTo
import org.jetbrains.plugins.groovy.intentions.style.inference.properResolve
import org.jetbrains.plugins.groovy.intentions.style.inference.resolve
import org.jetbrains.plugins.groovy.intentions.style.inference.typeParameter
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArrayInitializer
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil.inferClassAttribute
import org.jetbrains.plugins.groovy.lang.psi.impl.stringValue
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.ClosureSyntheticParameter
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.SignatureHintProcessor
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.*
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument
import org.jetbrains.plugins.groovy.lang.resolve.api.ArgumentMapping
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.impl.GdkArgumentMapping
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.ExpressionConstraint
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.TypeConstraint
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type

fun extractConstraintsFromClosureInvocations(closureParameter: ParameterizedClosure,
                                             instructions: List<ReadWriteVariableInstruction>): List<ConstraintFormula> {
  val collector = mutableListOf<ConstraintFormula>()
  for (call in instructions) {
    val nearestCall = call.element?.parentOfType<GrCall>()?.takeIf { it.isClosureCall() } ?: continue
    for (index in nearestCall.expressionArguments.indices) {
      collector.add(ExpressionConstraint(closureParameter.typeParameters[index].type(), nearestCall.expressionArguments[index]))
    }
  }
  return collector
}

inline fun GrMethod.forEachParameterUsage(action: (GrParameter, List<ReadWriteVariableInstruction>) -> Unit) {
  val usages =
    block
      ?.controlFlow
      ?.filterIsInstance<ReadWriteVariableInstruction>()
      ?.groupBy { it.element?.reference?.resolve() as? GrParameter } ?: return
  parameters
    .filter { usages.containsKey(it) }
    .forEach {
      val instructions = usages.getValue(it)
      action(it, instructions)
    }
}

fun analyzeClosureUsages(constraintCollector: MutableList<ConstraintFormula>,
                         closureParameter: ParameterizedClosure,
                         usages: List<ReadWriteVariableInstruction>,
                         dependentTypes: MutableSet<PsiTypeParameter>,
                         requiredTypesCollector: MutableMap<PsiTypeParameter, MutableList<BoundConstraint>>) {
  val parameter = closureParameter.parameter
  val parameterType = parameter.type
  val outerMethod = parameter.parentOfType<GrMethod>()!!
  val delegatesToCombiner = closureParameter.delegatesToCombiner
  parameter.setType(PsiType.getTypeByName(GROOVY_LANG_CLOSURE, parameter.project, parameter.resolveScope))
  for (usage in usages) {
    val element = usage.element ?: continue
    val directMethodResult = element.parentOfType<GrAssignmentExpression>()?.properResolve() as? GroovyMethodResult
    if (directMethodResult != null) {
      delegatesToCombiner.acceptResolveResult(directMethodResult)
    }
    val nearestCall = element.parentOfType<GrCall>() ?: continue
    val resolveResult = nearestCall.properResolve() as? GroovyMethodResult ?: return
    if (nearestCall.resolveMethod()?.containingClass?.qualifiedName == GROOVY_LANG_CLOSURE) {
      delegatesToCombiner.acceptResolveResult(resolveResult)
      collectClosureMethodInvocationDependencies(closureParameter, dependentTypes, requiredTypesCollector, constraintCollector,
                                                 resolveResult)
    }
    else {
      val mapping = resolveResult.candidate?.argumentMapping ?: continue
      val requiredArgument = mapping.arguments.find { it.isReferenceTo(parameter) } ?: continue
      val innerParameter = mapping.targetParameter(requiredArgument) ?: continue
      collectClosureParamsDependencies(innerParameter, resolveResult, closureParameter, constraintCollector, dependentTypes, outerMethod,
                                       requiredTypesCollector)
      processDelegatesToAnnotation(innerParameter, resolveResult, closureParameter.delegatesToCombiner)
    }
  }
  parameter.setType(parameterType)
}


fun collectClosureMethodInvocationDependencies(parameterizedClosure: ParameterizedClosure,
                                               dependentTypes: MutableSet<PsiTypeParameter>,
                                               requiredTypesCollector: MutableMap<PsiTypeParameter, MutableList<BoundConstraint>>,
                                               constraintCollector: MutableList<ConstraintFormula>,
                                               resolveResult: GroovyMethodResult) {
  if (resolveResult.candidate?.method.isClosureCallMethod()) {
    val arguments = resolveResult.candidate?.argumentMapping?.arguments ?: return
    val expectedTypes = parameterizedClosure.types.zip(arguments)
    val method = parameterizedClosure.parameter.parentOfType<GrMethod>() ?: return
    for ((expectedType, argument) in expectedTypes) {
      val argumentType = argument.type ?: continue
      constraintCollector.add(TypeConstraint(expectedType, argumentType, method))
      RecursiveMethodAnalyzer.induceDeepConstraints(expectedType, argumentType, dependentTypes, requiredTypesCollector,
                                                    method.typeParameters.toSet(), LOWER)
    }
  }
}

fun extractSignature(innerParameter: PsiParameter, resolveResult: GroovyMethodResult): Array<PsiType>? {
  val closureParamsAnnotation = innerParameter.annotations.find { it.qualifiedName == GROOVY_TRANSFORM_STC_CLOSURE_PARAMS } ?: return null
  val valueAttribute = inferClassAttribute(closureParamsAnnotation, "value")?.qualifiedName ?: return null
  val hintProcessor = SignatureHintProcessor.getHintProcessor(valueAttribute) ?: return null
  val optionsAttribute = closureParamsAnnotation.findAttributeValue("options")
  val arrayOptionsAttribute = AnnotationUtil.arrayAttributeValues(optionsAttribute)
  val options = arrayOptionsAttribute.mapNotNull { (it as? PsiLiteral)?.stringValue() }.toTypedArray()
  val invokedMethod = resolveResult.candidate?.method ?: return null
  return hintProcessor.inferExpectedSignatures(invokedMethod, resolveResult.substitutor, options).singleOrNull() ?: return null
}

fun collectClosureParamsDependencies(innerParameter: PsiParameter,
                                     resolveResult: GroovyMethodResult,
                                     closureParameter: ParameterizedClosure,
                                     constraintCollector: MutableList<ConstraintFormula>,
                                     dependentTypes: MutableSet<PsiTypeParameter>,
                                     outerMethod: GrMethod,
                                     requiredTypesCollector: MutableMap<PsiTypeParameter, MutableList<BoundConstraint>>) {
  val signature = extractSignature(innerParameter, resolveResult) ?: return
  for ((inferredType, typeParameter) in signature.zip(closureParameter.typeParameters)) {
    constraintCollector.add(TypeConstraint(typeParameter.type(), inferredType, outerMethod))
    val inferredTypeParameter = inferredType.typeParameter()
    if (inferredTypeParameter != null) {
      dependentTypes.add(typeParameter)
      dependentTypes.add(inferredTypeParameter)
    }
    else {
      requiredTypesCollector.computeIfAbsent(typeParameter) { mutableListOf() }.add(BoundConstraint(inferredType, LOWER))
    }
  }
}

fun processDelegatesToAnnotation(innerParameter: PsiParameter, resolveResult: GroovyMethodResult, combiner: DelegatesToCombiner) {
  val annotation = innerParameter.annotations.find { it.qualifiedName == GROOVY_LANG_DELEGATES_TO } ?: return
  trySetStrategyAttribute(annotation, combiner)
  if (!trySetValueAttribute(annotation, combiner)) {
    if (!trySetTypeAttribute(annotation, combiner, resolveResult)) {
      trySetParameterDelegate(annotation, combiner, resolveResult)
    }
  }
}

private fun trySetValueAttribute(annotation: PsiAnnotation, combiner: DelegatesToCombiner): Boolean {
  val valueAttribute = annotation.findDeclaredAttributeValue("value")?.reference?.resolve() as? PsiClass
  if (valueAttribute != null && valueAttribute.qualifiedName != GROOVY_LANG_DELEGATES_TO_TARGET) {
    combiner.setDelegate(valueAttribute)
    return true
  }
  else {
    return false
  }
}

private fun trySetTypeAttribute(annotation: PsiAnnotation, combiner: DelegatesToCombiner, resolveResult: GroovyMethodResult): Boolean {
  val typeAttribute = annotation.findDeclaredAttributeValue("type")?.stringValue()
  if (typeAttribute != null) {
    val createdType = createTypeSignature(typeAttribute, resolveResult.substitutor, annotation)
    if (createdType != null) {
      combiner.setTypeDelegate(createdType)
    }
    return true
  }
  return false
}

private fun trySetStrategyAttribute(annotation: PsiAnnotation, combiner: DelegatesToCombiner) {
  val strategyAttribute = annotation.findDeclaredAttributeValue("strategy")?.run(::extractIntRepresentation)
  if (strategyAttribute != null) {
    combiner.setStrategy(strategyAttribute)
  }
}

private fun trySetParameterDelegate(annotation: PsiAnnotation, combiner: DelegatesToCombiner, resolveResult: GroovyMethodResult) {
  val mapping = resolveResult.candidate?.argumentMapping ?: return
  val methodParameters = resolveResult.candidate?.method?.parameters?.takeIf { it.size > 1 }?.asList() ?: return
  val targetParameter = findTargetParameter(annotation, methodParameters)
  val argument = mapping.arguments.find { mapping.getProperTargetParameter(it, methodParameters[0]) == targetParameter } ?: return
  val argumentExpression = (argument as? ExpressionArgument)?.expression
  if (argumentExpression != null) {
    combiner.setDelegate(argumentExpression)
  }
  else {
    val argumentType = argument.type.resolve() ?: return
    combiner.setDelegate(argumentType)
  }
}

private fun ArgumentMapping.getProperTargetParameter(argument: Argument, firstParameter: JvmParameter) = when (this) {
  is GdkArgumentMapping -> {
    if (argument == arguments[0]) {
      firstParameter
    }
    else {
      targetParameter(argument)
    }
  }
  else -> targetParameter(argument)
}

private fun findTargetParameter(annotation: PsiAnnotation, methodParameters: Iterable<JvmParameter>): JvmParameter? {
  val delegatingParameters = methodParameters.mapNotNull { param ->
    val delegatesToAnnotation = param.annotations.find { it.qualifiedName == GROOVY_LANG_DELEGATES_TO_TARGET }
    delegatesToAnnotation?.let { param to it }
  }
  val targetLiteral = annotation.findDeclaredAttributeValue("target")
  if (targetLiteral != null) {
    return delegatingParameters.find { (_, anno) ->
      if (anno is GrAnnotation) {
        anno.findAttributeValue("value")?.stringValue() == targetLiteral.stringValue()
      }
      else {
        val value = (anno.findAttribute("value")?.attributeValue as? JvmAnnotationConstantValue)
        value?.constantValue as? String == targetLiteral.stringValue()
      }
    }?.first
  }
  else {
    return delegatingParameters.firstOrNull()?.first
  }
}

internal fun createMethodFromClosureBlock(body: GrClosableBlock,
                                          param: ParameterizedClosure,
                                          typeParameterList: PsiTypeParameterList): GrMethod {
  val enrichedBodyParameters = if (param.types.size == 1 && body.parameters.isEmpty()) listOf("it") else body.parameters.map { it.name }
  val parameters = param.types
    .zip(enrichedBodyParameters)
    .joinToString { (type, name) -> type.canonicalText + " " + name }
  val statements = body.statements.joinToString("\n") { it.text }
  return GroovyPsiElementFactory
    .getInstance(typeParameterList.project)
    .createMethodFromText("""
        def ${typeParameterList.text} void unique_named_method($parameters) {
          $statements
        }
      """.trimIndent(), typeParameterList)
}

private fun extractIntRepresentation(attribute: PsiAnnotationMemberValue): String? {
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


fun availableParameterNumber(annotation: PsiAnnotation): Int {
  val value = (annotation.parameterList.attributes.find { it.name == "value" }?.value?.reference?.resolve() as? PsiClass) ?: return 0
  val options = lazy {
    annotation.parameterList.attributes.find { it.name == "options" }?.value as? GrAnnotationArrayInitializer
  }
  return when (value.qualifiedName) {
    GROOVY_TRANSFORM_STC_SIMPLE_TYPE -> parseSimpleType(options.value ?: return 0)
    GROOVY_TRANSFORM_STC_FROM_STRING -> parseFromString(options.value ?: return 0)
    in ClosureParamsCombiner.availableHints -> 1
    GROOVY_TRANSFORM_STC_FROM_ABSTRACT_TYPE_METHODS -> /*todo*/ 0
    GROOVY_TRANSFORM_STC_MAP_ENTRY_OR_KEY_VALUE -> /*todo*/ 2
    else -> 0
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

internal infix fun PsiSubstitutor.compose(right: PsiSubstitutor): PsiSubstitutor {
  val typeParameters = substitutionMap.keys
  var newSubstitutor = PsiSubstitutor.EMPTY
  typeParameters.forEach { typeParameter ->
    newSubstitutor = newSubstitutor.put(typeParameter, right.substitute(this.substitute(typeParameter)))
  }
  return newSubstitutor
}

inline fun PsiType.anyComponent(crossinline predicate: (PsiType) -> Boolean): Boolean {
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