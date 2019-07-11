// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.*
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrOperatorExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

class InferenceDriver(val method: GrMethod) {

  private val elementFactory: GroovyPsiElementFactory = GroovyPsiElementFactory.getInstance(method.project)
  private val nameGenerator = NameGenerator(method.typeParameters.mapNotNull { it.name })
  private val constantTypeParameters: MutableList<PsiTypeParameter> = ArrayList()
  private val closureParameters: MutableMap<GrParameter, ParametrizedClosure> = LinkedHashMap()
  private val defaultTypeParameterList: PsiTypeParameterList = (method.typeParameterList?.copy()
                                                                ?: elementFactory.createTypeParameterList()) as PsiTypeParameterList
  private val parameterIndex: MutableMap<GrParameter, PsiTypeParameter> = LinkedHashMap()
  private val varargParameters: MutableSet<GrParameter> = mutableSetOf()
  val finalTypeParameterList: PsiTypeParameterList = elementFactory.createTypeParameterList()

  /**
   * Gathers expressions that will be assigned to parameters of [virtualMethod]
   */
  private val extractedExpressions by lazy {
    val allAcceptedExpressions = extractAcceptedExpressions(method, method.parameters.filter { it.typeElement == null }, mutableSetOf())
    val proxyMapping = method.parameters.zip(virtualMethod.parameters).toMap()
    allAcceptedExpressions.map { (parameter, expressions) -> Pair(proxyMapping.getValue(parameter), expressions) }
  }
  val contravariantTypes = mutableSetOf<PsiType>()
  val virtualMethod: GrMethod
  val virtualParametersMapping: Map<String, GrParameter>
  val appearedClassTypes: MutableMap<String, List<PsiClass?>> = mutableMapOf()

  init {
    if (method.isConstructor) {
      virtualMethod = elementFactory.createConstructorFromText(method.name, method.text, method)
    }
    else {
      virtualMethod = elementFactory.createMethodFromText(method.text, method)
    }
    virtualParametersMapping = method.parameters.map { it.name }.zip(virtualMethod.parameters).toMap()
  }

  val constantTypes: List<PsiType> by lazy {
    constantTypeParameters.map { it.type() }
  }

  val flexibleTypes: List<PsiType> by lazy {
    virtualMethod.parameters.map { it.type } + closureParameters.values.flatMap { it.typeParameters }.map { it.type() }
  }

  val forbiddingTypes: List<PsiType> by lazy {
    virtualMethod.parameters.mapNotNull { (it.type as? PsiArrayType)?.componentType } + contravariantTypes + virtualMethod.parameters.filter { it.isVarArgs }.map { it.type }
  }

  /**
   * Substitutes all non-typed parameters of the [virtualMethod] with generic types
   */
  fun setUpNewTypeParameters() {
    if (!virtualMethod.hasTypeParameters()) {
      virtualMethod.addAfter(elementFactory.createTypeParameterList(), virtualMethod.firstChild)
    }
    val generator = NameGenerator(virtualMethod.typeParameters.mapNotNull { it.name })
    for (parameter in virtualMethod.parameters.filter { it.typeElement == null }) {
      val newTypeParameter = createNewTypeParameter(generator)
      virtualMethod.typeParameterList!!.add(newTypeParameter)
      parameterIndex[parameter] = newTypeParameter
      parameter.setType(newTypeParameter.type())
      if (parameter.isVarArgs) {
        varargParameters.add(parameter)
        parameter.ellipsisDots!!.delete()
      }
    }
    createParametrizedClosures(generator)
  }

  private fun extractAcceptedExpressions(method: GrMethod,
                                         targetParameters: Collection<GrParameter>,
                                         visitedMethods: MutableSet<GrMethod>): Map<GrParameter, List<GrExpression>> {
    if (targetParameters.isEmpty()) {
      return emptyMap()
    }
    visitedMethods.add(method)
    val referencesStorage = mutableMapOf<GrParameter, MutableList<GrExpression>>()
    targetParameters.forEach { referencesStorage[it] = mutableListOf() }
    for (call in ReferencesSearch.search(method).findAll().mapNotNull { it.element.parent as? GrCall }) {
      val argumentList = call.expressionArguments + call.closureArguments
      val targetExpressions = argumentList.zip(method.parameters).filter { it.second in targetParameters }
      val insufficientExpressions = targetExpressions.filter { it.first.type?.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) ?: true }
      (targetExpressions - insufficientExpressions).forEach { referencesStorage[it.second]!!.add(it.first) }
      val enclosingMethodParameterMapping = insufficientExpressions.mapNotNull { (expression, targetParameter) ->
        val resolved = expression.reference?.resolve() as? GrParameter
        resolved?.run {
          Pair(this, targetParameter)
        }
      }.toMap()
      val enclosingMethod = call.parentOfType(GrMethod::class)
      if (enclosingMethod != null && !visitedMethods.contains(enclosingMethod)) {
        val acceptedExpressionsForEnclosingParameters = extractAcceptedExpressions(enclosingMethod, enclosingMethodParameterMapping.keys,
                                                                                   visitedMethods)
        acceptedExpressionsForEnclosingParameters.forEach { referencesStorage[enclosingMethodParameterMapping[it.key]]!!.addAll(it.value) }
      }
    }
    return referencesStorage
  }

  // To find which arguments are closures we need to analyze random call of the processed method.
  private fun createParametrizedClosures(generator: NameGenerator) {
    extractedExpressions
      .filter { (_, acceptedTypes) -> acceptedTypes.all { it is GrClosableBlock } && acceptedTypes.isNotEmpty() }
      .forEach { (parameter, calls) ->
        // todo: default-valued parameters
        val parametrizedClosure = ParametrizedClosure(parameter)
        closureParameters[parameter] = parametrizedClosure
        repeat((calls.first() as GrClosableBlock).allParameters.size) {
          val newTypeParameter = createNewTypeParameter(generator)
          virtualMethod.typeParameterList!!.add(newTypeParameter)
          parametrizedClosure.typeParameters.add(newTypeParameter)
        }
      }
  }

  private fun createNewTypeParameter(generator: NameGenerator? = null): PsiTypeParameter =
    elementFactory.createProperTypeParameter((generator ?: nameGenerator).name, PsiClassType.EMPTY_ARRAY)


  fun collectOuterCalls(session: GroovyInferenceSession) {
    for (parameter in closureParameters.keys) {
      // allows to resolve Closure#call
      parameter.setType(elementFactory.createTypeByFQClassName(GroovyCommonClassNames.GROOVY_LANG_CLOSURE))
    }
    val innerUsages = virtualMethod.block
      ?.controlFlow
      ?.filterIsInstance<ReadWriteVariableInstruction>()
      ?.groupBy { it.element?.reference?.resolve() }
    if (innerUsages != null) {
      closureParameters.keys.forEach {
        if (innerUsages.containsKey(it)) {
          setUpClosuresSignature(session, closureParameters[it]!!, innerUsages.getValue(it))
        }
        it.setType(parameterIndex[it]!!.type())
      }
    }
    collectOuterMethodCalls(session)
  }


  private fun collectOuterMethodCalls(inferenceSession: GroovyInferenceSession) {
    for (parameter in virtualMethod.parameters) {
      inferenceSession.addConstraint(ExpressionConstraint(parameter.type, parameter.initializerGroovy ?: continue))
    }
    for (call in ReferencesSearch.search(method).findAll().mapNotNull { it.element.parent as? GrExpression }) {
      inferenceSession.addConstraint(ExpressionConstraint(null, call))
    }
    for ((parameter, expressions) in extractedExpressions) {
      expressions.forEach {
        inferenceSession.addConstraint(TypeConstraint(parameter.type, it.type ?: return, method))
      }
    }
  }

  /**
   * All types in [parameterIndex] receive new deeply parametrized type
   */
  fun parametrizeMethod(signatureSubstitutor: PsiSubstitutor) {
    virtualMethod.typeParameterList?.replace(defaultTypeParameterList.copy())
    constantTypeParameters.addAll(virtualMethod.typeParameters)
    for ((parameter, typeParameter) in parameterIndex) {
      parameter.setType(
        createDeeplyParametrizedType(signatureSubstitutor.substitute(typeParameter)!!, virtualMethod.typeParameterList!!, parameter,
                                     signatureSubstitutor))
    }
  }


  fun registerTypeParameter(typeParameterList: PsiTypeParameterList, vararg supertypes: PsiClassType): PsiType {
    val typeParameter =
      elementFactory.createProperTypeParameter(nameGenerator.name, supertypes.filter {
        !it.equalsToText(GroovyCommonClassNames.GROOVY_OBJECT)
      }.toTypedArray())
    typeParameterList.add(typeParameter)
    return typeParameter.type()
  }

  /**
   * Creates type parameter with upper bound of [target].
   * If [target] is parametrized, all it's parameter types will also be parametrized.
   */
  private fun createDeeplyParametrizedType(target: PsiType,
                                           typeParameterList: PsiTypeParameterList,
                                           parameter: GrParameter?,
                                           signatureSubstitutor: PsiSubstitutor
  ): PsiType {
    val visitor = object : PsiTypeMapper() {

      override fun visitClassType(classType: PsiClassType?): PsiType? {
        classType ?: return classType
        val generifiedClassType = if (classType.isRaw) {
          val resolveResult = classType.resolve()!!
          val wildcards = Array(resolveResult.typeParameters.size) { PsiWildcardType.createUnbounded(virtualMethod.manager) }
          elementFactory.createType(resolveResult, *wildcards)
        }
        else classType
        val mappedParameters = generifiedClassType.parameters.map { it.accept(this) }.toTypedArray()
        if (classType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
          return registerTypeParameter(typeParameterList)
        }
        else {
          return elementFactory.createType(classType.resolve() ?: return null, *mappedParameters)
        }
      }

      override fun visitWildcardType(wildcardType: PsiWildcardType?): PsiType? {
        wildcardType ?: return wildcardType
        val upperBounds = if (wildcardType.isExtends) arrayOf(wildcardType.extendsBound.accept(this) as PsiClassType) else emptyArray()
        return registerTypeParameter(typeParameterList, *upperBounds)
      }

      override fun visitIntersectionType(intersectionType: PsiIntersectionType?): PsiType? {
        intersectionType ?: return intersectionType
        val parametrizedConjuncts = intersectionType.conjuncts.map { it.accept(this) as PsiClassType }.toTypedArray()
        return registerTypeParameter(typeParameterList, *parametrizedConjuncts)
      }

    }
    when {
      isClosureType(target) && closureParameters.containsKey(parameter) -> {
        val closureParameter = closureParameters[parameter]!!
        val initialTypeParameters = typeParameterList.typeParameters
        closureParameter.typeParameters.run {
          forEach {
            val topLevelType = createDeeplyParametrizedType(signatureSubstitutor.substitute(it)!!, typeParameterList, null,
                                                            signatureSubstitutor)
            closureParameter.types.add(topLevelType)
          }
          val createdTypeParameters = typeParameterList.typeParameters.subtract(initialTypeParameters.asIterable())
          clear()
          addAll(createdTypeParameters)
        }
        return target.accept(visitor)
      }
      target is PsiArrayType -> {
        return target.componentType.accept(visitor).createArrayType()
      }
      parameter in varargParameters -> {
        return target.accept(visitor).createArrayType()
      }
      target is PsiClassType && target.resolve() is PsiTypeParameter -> return registerTypeParameter(typeParameterList)
      target is PsiIntersectionType -> return target.accept(visitor) as PsiClassType
      target == PsiType.getJavaLangObject(virtualMethod.manager, virtualMethod.resolveScope) -> {
        return registerTypeParameter(typeParameterList)
      }
      else -> {
        return registerTypeParameter(typeParameterList, target.accept(visitor) as PsiClassType)
      }
    }
  }


  fun collectInnerMethodCalls(inferenceSession: GroovyInferenceSession) {
    val boundsCollector = mutableMapOf<String, MutableList<PsiClass?>>()

    virtualMethod.accept(object : GroovyRecursiveElementVisitor() {
      override fun visitCallExpression(callExpression: GrCallExpression) {
        val resolveResult = callExpression.advancedResolve() as? GroovyMethodResult
        val candidate = resolveResult?.candidate
        val receiver = candidate?.receiver as? PsiClassType
        receiver?.run {
          val endpointClassName = extractEndpointType(receiver).className
          if (endpointClassName != null) {
            boundsCollector.computeIfAbsent(endpointClassName) { mutableListOf() }.add(candidate.method.containingClass)
          }
        }
        candidate?.argumentMapping?.expectedTypes?.forEach { (type, argument) ->
          val argumentType = (argument.type as? PsiClassType)
          argumentType?.run {
            boundsCollector.computeIfAbsent(argumentType.className) { mutableListOf() }.add((type as? PsiClassType)?.resolve())
          }
          resolveResult.contextSubstitutor.substitute(type)?.run { contravariantTypes.add(this) }
        }
        if (isClosureType(receiver)) {
          val parameter = callExpression.firstChild.run {
            closureParameters[reference?.resolve() as? GrParameter ?: firstChild.reference?.resolve()]
          }
          parameter?.run {
            callExpression.expressionArguments.zip(parameter.types).forEach { (expression, parameterType) ->
              inferenceSession.addConstraint(
                ExpressionConstraint(inferenceSession.substituteWithInferenceVariables(parameterType), expression))
            }
          }
        }
        else {
          inferenceSession.addConstraint(ExpressionConstraint(null, callExpression))
        }
        super.visitCallExpression(callExpression)
      }

      override fun visitAssignmentExpression(expression: GrAssignmentExpression) {
        inferenceSession.addConstraint(ExpressionConstraint(null, expression))
        super.visitAssignmentExpression(expression)
      }

      override fun visitVariable(variable: GrVariable) {
        variable.initializerGroovy?.run {
          inferenceSession.addConstraint(ExpressionConstraint(variable.declaredType, this))
        }
        super.visitVariable(variable)
      }

      override fun visitExpression(expression: GrExpression) {
        if (expression is GrOperatorExpression) {
          inferenceSession.addConstraint(OperatorExpressionConstraint(expression))
        }
        super.visitExpression(expression)
      }
    })
    appearedClassTypes.putAll(boundsCollector)
    virtualMethod.block?.controlFlow
      ?.filterIsInstance<ReadWriteVariableInstruction>()
      ?.groupBy { it.element?.reference?.resolve() }
      ?.forEach { (parameter, usages) ->
        if (parameter is GrParameter && parameter in closureParameters.keys) {
          collectDeepClosureDependencies(inferenceSession, closureParameters[parameter]!!, usages)
        }
      }
  }

  /**
   * Reaches type parameter that does not extend other type parameter
   * @param type should be a type parameter
   */
  private tailrec fun extractEndpointType(type: PsiClassType): PsiClassType =
    if (type.superTypes.size == 1 && type.superTypes.first() in virtualMethod.typeParameters.map { it.type() }) {
      extractEndpointType(type.superTypes.first() as PsiClassType)
    }
    else {
      type
    }


  fun acceptFinalSubstitutor(resultSubstitutor: PsiSubstitutor) {
    val targetParameters = parameterIndex.keys
    targetParameters.forEach { param ->
      param.setType(resultSubstitutor.substitute(param.type))
      when {
        isClosureType(param.type) -> {
          closureParameters[param]?.run {
            substituteTypes(resultSubstitutor)
            renderTypes(virtualMethod.parameterList)
          }
        }
        param.type is PsiArrayType -> param.setType(
          resultSubstitutor.substitute((param.type as PsiArrayType).componentType).createArrayType())
      }
    }
    val residualTypeParameterList = buildResidualTypeParameterList(finalTypeParameterList)
    virtualMethod.typeParameterList?.replace(residualTypeParameterList)
    if (virtualMethod.typeParameters.isEmpty()) {
      virtualMethod.typeParameterList?.delete()
    }
  }

  private fun buildResidualTypeParameterList(typeParameterList: PsiTypeParameterList): PsiTypeParameterList {
    virtualMethod.typeParameterList!!.replace(typeParameterList)
    val necessaryTypeParameters = LinkedHashSet<PsiTypeParameter>()
    val visitor = object : PsiTypeVisitor<PsiType>() {

      override fun visitClassType(classType: PsiClassType?): PsiType? {
        classType ?: return classType
        val resolvedClass = classType.resolveGenerics().element
        if (resolvedClass is PsiTypeParameter) {
          if (resolvedClass.name !in necessaryTypeParameters.map { it.name }) {
            necessaryTypeParameters.add(resolvedClass)
            resolvedClass.extendsList.referencedTypes.forEach { it.accept(this) }
          }
        }
        classType.parameters.forEach { it.accept(this) }
        return super.visitClassType(classType)
      }

      override fun visitWildcardType(wildcardType: PsiWildcardType?): PsiType? {
        wildcardType?.extendsBound?.accept(this)
        return super.visitWildcardType(wildcardType)
      }

      override fun visitArrayType(arrayType: PsiArrayType?): PsiType? {
        arrayType?.componentType?.accept(this)
        return super.visitArrayType(arrayType)
      }
    }
    virtualMethod.parameters.forEach { it.type.accept(visitor) }
    closureParameters.values.flatMap { it.types }.forEach { it.accept(visitor) }
    val takenNames = necessaryTypeParameters.map { it.name }
    val remainedConstantParameters = constantTypeParameters.filter { it.name !in takenNames }
    //necessaryTypeParameters.addAll(remainedConstantParameters)
    return elementFactory.createMethodFromText(
      "def <${(remainedConstantParameters + necessaryTypeParameters).joinToString(", ") { it.text }}> void foo() {}").typeParameterList!!
  }


  fun createBoundedTypeParameter(name: String,
                                 resultSubstitutor: PsiSubstitutor,
                                 advice: PsiType): PsiTypeParameter {
    val mappedSupertypes = when {
      advice is PsiClassType && (advice.name != name) -> arrayOf(resultSubstitutor.substitute(advice) as PsiClassType)
      advice is PsiIntersectionType -> PsiIntersectionType.flatten(advice.conjuncts, mutableSetOf()).map {
        resultSubstitutor.substitute(it) as PsiClassType
      }.toTypedArray()
      else -> emptyArray()
    }
    return elementFactory.createProperTypeParameter(name, mappedSupertypes).apply {
      finalTypeParameterList.add(this)
    }
  }

  fun treatedAsOverriddenMethod(): Boolean {
    method.containingClass ?: return false
    val candidateMethodsDomain = if (method.annotations.any { it.qualifiedName == CommonClassNames.JAVA_LANG_OVERRIDE }) {
      method.containingClass!!.supers
    }
    else {
      method.containingClass!!.interfaces
    }
    val alreadyOverriddenMethods = (method.containingClass!!.supers + method.containingClass)
      .flatMap { it.findMethodsByName(method.name, true).asIterable() }
      .flatMap { it.findSuperMethods().asIterable() }
    val overridableMethod = candidateMethodsDomain
      .flatMap { it.findMethodsByName(method.name, true).asIterable() }
      .subtract(alreadyOverriddenMethods)
      .firstOrNull { methodsMatch(it, method) }
    if (overridableMethod != null) {
      overridableMethod.parameters
        .zip(virtualMethod.parameters)
        .forEach { (patternParameter, virtualParameter) -> virtualParameter.setType(patternParameter.type as? PsiClassType) }
      return true
    }
    else {
      return false
    }
  }

  private fun methodsMatch(pattern: PsiMethod,
                           tested: GrMethod): Boolean {
    val parameterList = pattern.parameters.zip(tested.parameters)
    if (parameterList.size != pattern.parameterList.parametersCount || parameterList.size != tested.parameterList.parametersCount) {
      return false
    }
    return parameterList.all { (patternParameter, testedParameter) ->
      testedParameter.typeElement == null || testedParameter.type == patternParameter.type
    }
  }
}