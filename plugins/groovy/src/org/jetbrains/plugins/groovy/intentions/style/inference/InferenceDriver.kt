// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.*
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrOperatorExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.ExpressionConstraint
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSession
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.OperatorExpressionConstraint
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import kotlin.collections.ArrayList
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

class InferenceDriver(private val method: GrMethod) {

  private val elementFactory: GroovyPsiElementFactory = GroovyPsiElementFactory.getInstance(method.project)
  private val nameGenerator = NameGenerator(method.typeParameters.mapNotNull { it.name })
  private val constantTypeParameters: MutableList<PsiTypeParameter> = ArrayList()
  private val closureParameters: MutableMap<GrParameter, ParametrizedClosure> = LinkedHashMap()
  private val defaultTypeParameterList: PsiTypeParameterList = (method.typeParameterList?.copy()
                                                                ?: elementFactory.createTypeParameterList()) as PsiTypeParameterList
  private val parameterIndex: MutableMap<GrParameter, PsiTypeParameter> = LinkedHashMap()
  val appearedClassTypes: MutableMap<String, List<PsiClass?>> = mutableMapOf()
  val constantTypes: List<PsiType> by lazy {
    constantTypeParameters.map { it.type() }
  }

  val flexibleTypes: List<PsiType> by lazy {
    method.parameters.map { it.type } + closureParameters.values.flatMap { it.typeParameters }.map { it.type() }
  }

  val forbiddingTypes: List<PsiType> by lazy {
    method.parameters.mapNotNull { (it.type as? PsiArrayType)?.componentType }
  }

  /**
   * Substitutes all non-typed parameters of the [method] with generic types
   */
  fun setUpNewTypeParameters() {
    if (!method.hasTypeParameters()) {
      method.addAfter(elementFactory.createTypeParameterList(), method.firstChild)
    }

    val generator = NameGenerator()
    for (parameter in method.parameters.filter { it.typeElement == null }) {
      val newTypeParameter = createNewTypeParameter(generator)
      method.typeParameterList!!.add(newTypeParameter)
      parameterIndex[parameter] = newTypeParameter
      parameter.setType(newTypeParameter.type())
    }
    createParametrizedClosures(generator)
  }

  private fun createParametrizedClosures(generator: NameGenerator) {
    val call = ReferencesSearch.search(method).mapNotNull { it.element.parent as? GrCall }.firstOrNull() ?: return
    // todo: default-valued parameters
    val argumentList = call.expressionArguments + call.closureArguments
    argumentList.zip(method.parameters).filter { it.first is GrClosableBlock }.forEach { (argument, parameter) ->
      val parametrizedClosure = ParametrizedClosure(parameter)
      closureParameters[parameter] = parametrizedClosure
      repeat((argument as GrClosableBlock).allParameters.size) {
        val newTypeParameter = createNewTypeParameter(generator)
        method.typeParameterList!!.add(newTypeParameter)
        parametrizedClosure.typeParameters.add(newTypeParameter)
      }
    }
  }


  private fun createNewTypeParameter(generator: NameGenerator? = null): PsiTypeParameter =
    elementFactory.createProperTypeParameter((generator ?: nameGenerator).name, PsiClassType.EMPTY_ARRAY)


  fun collectOuterCalls(session: GroovyInferenceSession) {
    collectOuterMethodCalls(session)
    closureParameters.values.forEach { setUpClosuresSignature(session, it) }
  }


  private fun collectOuterMethodCalls(inferenceSession: GroovyInferenceSession) {
    for (parameter in method.parameters) {
      inferenceSession.addConstraint(ExpressionConstraint(parameter.type, parameter.initializerGroovy ?: continue))
    }
    val references = ReferencesSearch.search(method).findAll()
    for (call in references.mapNotNull { it.element.parent as? GrExpression }) {
      inferenceSession.addConstraint(ExpressionConstraint(null, call))
    }
  }

  /**
   * All types in [parameterIndex] receive new deeply parametrized type
   */
  fun parametrizeMethod(signatureSubstitutor: PsiSubstitutor) {
    method.typeParameterList?.replace(defaultTypeParameterList.copy())
    constantTypeParameters.addAll(method.typeParameters)
    for ((parameter, typeParameter) in parameterIndex) {
      parameter.setType(createDeeplyParametrizedType(signatureSubstitutor.substitute(typeParameter)!!, method.typeParameterList!!,
                                                     parameter, signatureSubstitutor))
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
          val wildcards = Array(resolveResult.typeParameters.size) { PsiWildcardType.createUnbounded(method.manager) }
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
      isClosureType(target) -> {
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
        return registerTypeParameter(typeParameterList).createArrayType()
      }
      else -> {
        return when (target) {
          PsiType.getJavaLangObject(method.manager, method.resolveScope) -> {
            registerTypeParameter(typeParameterList)
          }
          is PsiIntersectionType -> target.accept(visitor) as PsiClassType
          else -> {
            registerTypeParameter(typeParameterList, target.accept(visitor) as PsiClassType)
          }
        }
      }
    }
  }


  fun collectInnerMethodCalls(inferenceSession: GroovyInferenceSession) {
    val boundsCollector = mutableMapOf<String, MutableList<PsiClass?>>()

    method.accept(object : GroovyRecursiveElementVisitor() {

      override fun visitCallExpression(callExpression: GrCallExpression) {
        val candidate = (callExpression.advancedResolve() as? GroovyMethodResult)?.candidate
        val receiver = candidate?.receiver as? PsiClassType
        receiver?.run {
          boundsCollector.computeIfAbsent(receiver.className) { mutableListOf() }.add(candidate.method.containingClass)
        }
        candidate?.argumentMapping?.expectedTypes?.forEach { (type, argument) ->
          val argumentType = (argument.type as? PsiClassType)
          argumentType?.run {
            boundsCollector.computeIfAbsent(argumentType.className) { mutableListOf() }.add((type as? PsiClassType)?.resolve())
          }
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

      override fun visitExpression(expression: GrExpression) {
        if (expression is GrOperatorExpression) {
          inferenceSession.addConstraint(OperatorExpressionConstraint(expression))
        }
        super.visitExpression(expression)
      }
    })
    appearedClassTypes.putAll(boundsCollector)
  }


  fun acceptFinalSubstitutor(resultSubstitutor: PsiSubstitutor) {
    val targetParameters = parameterIndex.keys
    if (targetParameters.any { isClosureType(it.type) }) {
      ParametrizedClosure.ensureImports(elementFactory, method.containingFile as GroovyFile)
    }
    targetParameters.forEach { param ->
      param.setType(resultSubstitutor.substitute(param.type))
      when {
        isClosureType(param.type) -> {
          closureParameters[param]!!.run {
            substituteTypes(resultSubstitutor)
            renderTypes(elementFactory)
          }
        }
        param.type is PsiArrayType -> param.setType(
          resultSubstitutor.substitute((param.type as PsiArrayType).componentType).createArrayType())
      }
    }
    val residualTypeParameterList = buildResidualTypeParameterList(defaultTypeParameterList)
    method.typeParameterList?.replace(residualTypeParameterList)
    if (method.typeParameters.isEmpty()) {
      method.typeParameterList?.delete()
    }
  }

  private fun buildResidualTypeParameterList(typeParameterList: PsiTypeParameterList): PsiTypeParameterList {
    method.typeParameterList!!.replace(typeParameterList)
    val necessaryTypeParameters = LinkedHashSet<PsiTypeParameter>()
    val visitor = object : PsiTypeVisitor<PsiType>() {

      override fun visitClassType(classType: PsiClassType?): PsiType? {
        classType ?: return classType
        val resolveElement = classType.resolveGenerics().element
        if (resolveElement is PsiTypeParameter) {
          if (resolveElement.name !in necessaryTypeParameters.map { it.name }) {
            necessaryTypeParameters.add(resolveElement)
            resolveElement.extendsList.referencedTypes.forEach { it.accept(this) }
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
    method.parameters.forEach { it.type.accept(visitor) }
    closureParameters.values.flatMap { it.types }.forEach { it.accept(visitor) }
    val resultingTypeParameterList = elementFactory.createTypeParameterList()
    necessaryTypeParameters.forEach { resultingTypeParameterList.add(it) }
    return resultingTypeParameterList
  }


  fun createBoundedTypeParameterElement(name: String,
                                        resultSubstitutor: PsiSubstitutor,
                                        advice: PsiType): PsiTypeParameter {
    val mappedSupertypes = when {
      advice is PsiClassType && (advice.name != name) -> arrayOf(resultSubstitutor.substitute(advice) as PsiClassType)
      advice is PsiIntersectionType -> PsiIntersectionType.flatten(advice.conjuncts, mutableSetOf()).map {
        resultSubstitutor.substitute(it) as PsiClassType
      }.toTypedArray()
      else -> emptyArray()
    }
    return elementFactory.createProperTypeParameter(name, mappedSupertypes).apply { defaultTypeParameterList.add(this) }
  }
}