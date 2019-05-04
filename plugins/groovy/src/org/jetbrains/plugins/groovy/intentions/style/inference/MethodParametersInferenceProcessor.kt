// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.*
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.containers.toArray
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrOperatorExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.ExpressionConstraint
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSession
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.OperatorExpressionConstraint
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type
import java.util.*
import kotlin.collections.LinkedHashSet
import kotlin.collections.set


/**
 * @author knisht
 */

/**
 * Allows to infer method parameters types regarding method calls and inner dependencies between types.
 */
class MethodParametersInferenceProcessor(val method: GrMethod) {
  private val elementFactory: GroovyPsiElementFactory = GroovyPsiElementFactory.getInstance(method.project)
  private val defaultTypeParameterList = (method.typeParameterList?.copy()
                                          ?: elementFactory.createTypeParameterList()) as PsiTypeParameterList
  private val nameGenerator = NameGenerator()
  private val targetParameters = method.parameters.filter { it.typeElement == null }

  /**
   * Performs full substitution for non-typed parameters of [method]
   * Inference is performed in 3 phases:
   * 1. Creating a type parameter for each of existing non-typed parameter
   * 2. Inferring new parameters signature cause of possible generic types. Creating new type parameters.
   * 3. Inferring dependencies between new type parameters and instantiating them.
   */
  fun runInferenceProcess() {
    val parameterIndex = setUpNewTypeParameters()
    val constantTypeParameters = setUpParametersSignature(parameterIndex)
    val registry = setUpRegistry(constantTypeParameters)
    inferTypeParameters(registry)
    if (method.typeParameters.isEmpty()) {
      method.typeParameterList?.delete()
    }
  }


  /**
   * Does first phase of inference
   */
  private fun setUpNewTypeParameters(): Map<GrParameter, PsiTypeParameter> {
    if (!method.hasTypeParameters()) {
      method.addAfter(elementFactory.createTypeParameterList(), method.firstChild)
    }
    val typeParameters = method.typeParameterList ?: return emptyMap()
    val parameterIndex = LinkedHashMap<GrParameter, PsiTypeParameter>()

    val generator = NameGenerator()
    for (param in method.parameters) {
      if (param.typeElement == null) {
        val newTypeParameter = elementFactory.createProperTypeParameter(generator.name, PsiClassType.EMPTY_ARRAY)
        typeParameters.add(newTypeParameter)
        parameterIndex[param] = newTypeParameter
        param.setType(newTypeParameter.type())
      }
    }
    return parameterIndex
  }


  /**
   * Does second phase of inference
   */
  private fun setUpParametersSignature(parameterIndex: Map<GrParameter, PsiTypeParameter>): Array<PsiTypeParameter> {
    val inferenceSession = GroovyInferenceSession(method.typeParameters, PsiSubstitutor.EMPTY, method,
                                                  propagateVariablesToNestedSessions = true)
    collectOuterMethodCalls(inferenceSession)
    val signatureSubstitutor = inferenceSession.inferSubst()
    method.typeParameterList?.replace(defaultTypeParameterList.copy())
    val defaultTypeParameters = method.typeParameters
    for ((parameter, typeParameter) in parameterIndex) {
      parameter.setType(
        createDeeplyParametrizedType(signatureSubstitutor.substitute(typeParameter)!!, method.typeParameterList!!))
    }
    return defaultTypeParameters
  }


  /**
   * Does third phase of inference
   */
  private fun inferTypeParameters(registry: InferenceUnitRegistry) {
    val graph = InferenceUnitGraph(registry)
    val representativeSubstitutor = collectRepresentativeSubstitutor(graph, registry)
    var resultSubstitutor = PsiSubstitutor.EMPTY
    for (unit in graph.getRepresentatives().filter { !it.constant }) {
      val preferableType = getPreferableType(graph, unit, representativeSubstitutor, resultSubstitutor)
      graph.getEqualUnits(unit).forEach { resultSubstitutor = resultSubstitutor.put(it.initialTypeParameter, preferableType) }
    }
    for (param in targetParameters) {
      param.setType(resultSubstitutor.substitute(param.type))
    }
    val residualTypeParameterList = buildResidualTypeParameterList(defaultTypeParameterList)
    method.typeParameterList?.replace(residualTypeParameterList)
  }

  private fun getPreferableType(graph: InferenceUnitGraph,
                                unit: InferenceUnit,
                                representativeSubstitutor: PsiSubstitutor,
                                resultSubstitutor: PsiSubstitutor): PsiType {
    val equalTypeParameters = graph.getEqualUnits(unit).filter { it.typeInstantiation == PsiType.NULL }
    val mayBeDirectlyInstantiated = equalTypeParameters.isEmpty() &&
                                    when {
                                      unit.flexible -> (unit.typeInstantiation !is PsiIntersectionType)
                                      else -> unit.subtypes.size <= 1
                                    }
    when {
      mayBeDirectlyInstantiated -> {
        val instantiation = when {
          unit.flexible || unit.subtypes.size != 0 -> unit.typeInstantiation
          unit.typeInstantiation == unit.type -> PsiWildcardType.createUnbounded(method.manager)
          else -> PsiWildcardType.createExtends(method.manager, unit.typeInstantiation)
        }
        return resultSubstitutor.substitute(representativeSubstitutor.substitute(instantiation))
      }
      else -> {
        val parent = unit.unitInstantiation
        val advice = if (parent?.typeInstantiation == PsiType.NULL) parent?.type else parent?.typeInstantiation
        val newTypeParam = createBoundedTypeParameterElement(unit, representativeSubstitutor, advice)
        defaultTypeParameterList.add(newTypeParam)
        return newTypeParam.type()
      }
    }
  }

  private fun setUpRegistry(constantTypeParameters: Array<PsiTypeParameter>): InferenceUnitRegistry {
    val inferenceSession = GroovyInferenceSession(method.typeParameters, PsiSubstitutor.EMPTY, method,
                                                  propagateVariablesToNestedSessions = true)
    collectInnerMethodCalls(inferenceSession)
    constantTypeParameters.map { it.type() }.forEach { getInferenceVariable(inferenceSession, it).instantiation = it }
    inferenceSession.inferSubst()
    val registry = InferenceUnitRegistry()
    val inferenceVariables = method.typeParameters.map { getInferenceVariable(inferenceSession, it.type()) }.toList()
    registry.setUpUnits(inferenceVariables, inferenceSession)
    constantTypeParameters.map { it.type() }.forEach { registry.searchUnit(it)?.constant = true }
    method.parameters.mapNotNull { registry.searchUnit(it.type) }.forEach { it.flexible = true }
    return registry
  }


  /**
   * Creates type parameter with upper bound of [target].
   * If [target] is parametrized, all it's parameter types will also be parametrized.
   */
  private fun createDeeplyParametrizedType(target: PsiType,
                                           typeParameterList: PsiTypeParameterList): PsiType {
    val visitor = object : PsiTypeMapper() {

      private fun registerTypeParameter(vararg supertypes: PsiClassType): PsiType {
        val typeParameter = elementFactory.createProperTypeParameter(nameGenerator.name, supertypes)
        typeParameterList.add(typeParameter)
        return typeParameter.type()
      }

      override fun visitClassType(classType: PsiClassType?): PsiType? {
        classType ?: return classType
        val generifiedClassType = if (classType.isRaw) {
          val resolveResult = classType.resolve()!!
          val wildcards = Array(resolveResult.typeParameters.size) { PsiWildcardType.createUnbounded(method.manager) }
          elementFactory.createType(resolveResult, *wildcards)
        }
        else classType
        val parameters = generifiedClassType.parameters
        val replacedParameters = parameters.map { it.accept(this) }.toArray(emptyArray())
        val resolveResult = generifiedClassType.resolveGenerics()
        if (classType == PsiType.getJavaLangObject(method.manager, method.resolveScope)) {
          return registerTypeParameter()
        }
        else {
          return elementFactory.createType(resolveResult.element ?: return null, *replacedParameters)
        }
      }

      override fun visitWildcardType(wildcardType: PsiWildcardType?): PsiType? {
        wildcardType ?: return wildcardType
        val upperBounds = if (wildcardType.isExtends) arrayOf(wildcardType.extendsBound.accept(this) as PsiClassType) else emptyArray()
        return registerTypeParameter(*upperBounds)
      }

      override fun visitCapturedWildcardType(type: PsiCapturedWildcardType?): PsiType? {
        type ?: return type
        val upperBound = type.upperBound.accept(this) as PsiClassType
        return registerTypeParameter(upperBound)
      }

      override fun visitIntersectionType(intersectionType: PsiIntersectionType?): PsiType? {
        intersectionType ?: return intersectionType
        val parametrizedConjuncts = intersectionType.conjuncts.map { it.accept(this) as PsiClassType }.toTypedArray()
        return registerTypeParameter(*parametrizedConjuncts)
      }

    }
    if (target is PsiIntersectionType || (target is PsiClassType && (target.hasParameters() || target.isRaw))) {
      return target.accept(visitor)
    }
    else {
      val typeParam = elementFactory.createProperTypeParameter(nameGenerator.name, arrayOf(target as PsiClassType))
      typeParameterList.add(typeParam)
      return typeParam.type()
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
          necessaryTypeParameters.add(resolveElement)
          resolveElement.extendsList.referencedTypes.forEach { it.accept(this) }
        }
        classType.parameters.forEach { it.accept(this) }
        return super.visitClassType(classType)
      }

      override fun visitWildcardType(wildcardType: PsiWildcardType?): PsiType? {
        wildcardType?.extendsBound?.accept(this)
        return super.visitWildcardType(wildcardType)
      }

      override fun visitCapturedWildcardType(capturedWildcardType: PsiCapturedWildcardType?): PsiType? {
        capturedWildcardType?.wildcard?.accept(this)
        return super.visitCapturedWildcardType(capturedWildcardType)
      }
    }
    method.parameters.forEach { it.type.accept(visitor) }
    val resultingTypeParameterList = elementFactory.createTypeParameterList()
    necessaryTypeParameters.forEach { resultingTypeParameterList.add(it) }
    return resultingTypeParameterList
  }

  /**
   * Creates ready for insertion type parameter with correct extends list.
   */
  private fun createBoundedTypeParameterElement(unit: InferenceUnit,
                                                relationSubstitutor: PsiSubstitutor = PsiSubstitutor.EMPTY,
                                                supertypeAdvice: PsiType? = null): PsiTypeParameter {
    val superType = supertypeAdvice ?: unit.typeInstantiation
    val mappedSupertypes = when (superType) {
      is PsiClassType -> arrayOf(relationSubstitutor.substitute(superType) as PsiClassType)
      is PsiIntersectionType -> superType.conjuncts.map { relationSubstitutor.substitute(it) as PsiClassType }.toTypedArray()
      else -> emptyArray()
    }
    return elementFactory.createProperTypeParameter(unit.initialTypeParameter.name!!, mappedSupertypes)
  }


  /**
   * Scans [method] body for calls and registers dependencies between type parameters.
   */
  private fun collectInnerMethodCalls(resolveSession: GroovyInferenceSession) {
    val visitor = object : GroovyRecursiveElementVisitor() {

      override fun visitCallExpression(callExpression: GrCallExpression) {
        resolveSession.addConstraint(ExpressionConstraint(null, callExpression))
        super.visitCallExpression(callExpression)
      }

      override fun visitExpression(expression: GrExpression) {
        if (expression is GrOperatorExpression) {
          resolveSession.addConstraint(OperatorExpressionConstraint(expression))
        }
        super.visitExpression(expression)
      }
    }
    method.accept(visitor)
  }

  /**
   * Scans environment for calls of [method] to infer basic parameter signature.
   */
  private fun collectOuterMethodCalls(resolveSession: GroovyInferenceSession) {
    val references = ReferencesSearch.search(method).findAll()
    for (parameter in method.parameters) {
      resolveSession.addConstraint(ExpressionConstraint(parameter.type, parameter.initializerGroovy ?: continue))
    }
    for (occurrence in references) {
//      if (occurrence is GrReferenceElement) {
        val call = occurrence.element.parent
        if (call is GrExpression) {
          resolveSession.addConstraint(ExpressionConstraint(null, call))
        }
//      }
    }
  }

}