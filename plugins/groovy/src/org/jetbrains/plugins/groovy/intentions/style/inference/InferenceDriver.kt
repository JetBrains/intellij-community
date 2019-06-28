// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.*
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
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

class InferenceDriver(val method: GrMethod) {

  private val elementFactory: GroovyPsiElementFactory = GroovyPsiElementFactory.getInstance(method.project)
  private val nameGenerator = NameGenerator(method.typeParameters.mapNotNull { it.name })
  private val constantTypeParameters: MutableList<PsiTypeParameter> = ArrayList()
  private val defaultTypeParameterList: PsiTypeParameterList = (method.typeParameterList?.copy()
                                                                ?: elementFactory.createTypeParameterList()) as PsiTypeParameterList
  private val parameterIndex: MutableMap<GrParameter, PsiTypeParameter> = LinkedHashMap()
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
    virtualMethod.parameters.map { it.type }
  }

  val forbiddingTypes: List<PsiType> by lazy {
    virtualMethod.parameters.mapNotNull { (it.type as? PsiArrayType)?.componentType } + contravariantTypes
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
    }
  }

  private fun createNewTypeParameter(generator: NameGenerator? = null): PsiTypeParameter =
    elementFactory.createProperTypeParameter((generator ?: nameGenerator).name, PsiClassType.EMPTY_ARRAY)


  fun collectOuterCalls(session: GroovyInferenceSession) {
    collectOuterMethodCalls(session)
  }


  private fun collectOuterMethodCalls(inferenceSession: GroovyInferenceSession) {
    for (parameter in virtualMethod.parameters) {
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
    virtualMethod.typeParameterList?.replace(defaultTypeParameterList.copy())
    constantTypeParameters.addAll(virtualMethod.typeParameters)
    for ((parameter, typeParameter) in parameterIndex) {
      parameter.setType(createDeeplyParametrizedType(signatureSubstitutor.substitute(typeParameter)!!, virtualMethod.typeParameterList!!))
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
                                           typeParameterList: PsiTypeParameterList
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
    when (target) {
      is PsiArrayType -> {
        return registerTypeParameter(typeParameterList).createArrayType()
      }
      else -> {
        return when (target) {
          PsiType.getJavaLangObject(virtualMethod.manager, virtualMethod.resolveScope) -> {
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

    virtualMethod.accept(object : GroovyRecursiveElementVisitor() {
      override fun visitCallExpression(callExpression: GrCallExpression) {
        val resolveResult = callExpression.advancedResolve() as? GroovyMethodResult
        val candidate = resolveResult?.candidate
        val receiver = candidate?.receiver as? PsiClassType
        receiver?.run {
          boundsCollector.computeIfAbsent(receiver.className) { mutableListOf() }.add(candidate.method.containingClass)
        }
        candidate?.argumentMapping?.expectedTypes?.forEach { (type, argument) ->
          val argumentType = (argument.type as? PsiClassType)
          argumentType?.run {
            boundsCollector.computeIfAbsent(argumentType.className) { mutableListOf() }.add((type as? PsiClassType)?.resolve())
          }
          resolveResult.contextSubstitutor.substitute(type)?.run { contravariantTypes.add(this) }
        }
        inferenceSession.addConstraint(ExpressionConstraint(null, callExpression))
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
    targetParameters.forEach { param ->
      param.setType(resultSubstitutor.substitute(param.type))
      when {
        param.type is PsiArrayType -> param.setType(
          resultSubstitutor.substitute((param.type as PsiArrayType).componentType).createArrayType())
      }
    }
    val residualTypeParameterList = buildResidualTypeParameterList(defaultTypeParameterList)
    virtualMethod.typeParameterList?.replace(residualTypeParameterList)
    if (virtualMethod.typeParameters.isEmpty()) {
      virtualMethod.typeParameterList?.delete()
    }
  }

  private fun buildResidualTypeParameterList(typeParameterList: PsiTypeParameterList): PsiTypeParameterList {
    virtualMethod.typeParameterList!!.replace(typeParameterList)
    val necessaryTypeParameters = LinkedHashSet<PsiTypeParameter>()
    necessaryTypeParameters.addAll(constantTypeParameters)
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
    return elementFactory.createProperTypeParameter(name, mappedSupertypes).apply {
      defaultTypeParameterList.add(this)
    }
  }
}