// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.containers.toArray
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrOperatorExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.ExpressionConstraint
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSession
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.OperatorExpressionConstraint
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type
import java.util.*
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
    inferTypeParameters(constantTypeParameters)
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
  private fun inferTypeParameters(constantTypeParameters: Array<PsiTypeParameter>) {
    val inferenceSession = GroovyInferenceSession(method.typeParameters, PsiSubstitutor.EMPTY, method,
                                                  propagateVariablesToNestedSessions = true)
    collectInnerMethodCalls(inferenceSession)
    val constantInferenceVariables = getConstantInferenceVariables(constantTypeParameters, inferenceSession)
    inferenceSession.inferSubst()
    val inferenceVariables = method.typeParameters.map { getInferenceVariable(inferenceSession, it.type()) }.toList()
    val graph = createInferenceVariableGraph(inferenceVariables, inferenceSession)
    graph.adjustFlexibleVariables(method)
    val representatives = inferenceVariables.map { graph.getRepresentative(it)!! }.toSet()
    val representativeSubstitutor = collectRepresentativeSubstitutor(graph)
    var resultSubstitutor = PsiSubstitutor.EMPTY
    for (variable in representatives) {
      if (variable in constantInferenceVariables) {
        continue
      }
      val equalTypeParameters = inferenceVariables.filter {
        it.instantiation == PsiType.NULL &&
        graph.getRepresentative(it) == variable &&
        it !in constantInferenceVariables
      }
      if (equalTypeParameters.isNotEmpty()) {
        val parent = graph.nodes[variable]?.directParent?.inferenceVariable
        val advice = parent?.type()
        val newTypeParam = createBoundedTypeParameterElement(variable,
                                                             inferenceSession.restoreNameSubstitution, representativeSubstitutor,
                                                             advice)
        defaultTypeParameterList.add(newTypeParam)
        resultSubstitutor = resultSubstitutor.put(variable, newTypeParam.type())
        equalTypeParameters.forEach { resultSubstitutor = resultSubstitutor.put(it, newTypeParam.type()) }
      }
      else {
        val newTypeParam = createBoundedTypeParameterElement(variable,
                                                             inferenceSession.restoreNameSubstitution, representativeSubstitutor,
                                                             variable.instantiation)
        if (variable.instantiation is PsiIntersectionType ||
            (graph.nodes[variable]?.directParent != null && variable.parameter.type() !in targetParameters.map { it.type })) {
          defaultTypeParameterList.add(newTypeParam)
          resultSubstitutor = resultSubstitutor.put(variable, newTypeParam.type())
        }
        else {
          resultSubstitutor = resultSubstitutor.put(variable, inferenceSession.restoreNameSubstitution.substitute(
            representativeSubstitutor.substitute(variable.instantiation)))
        }
      }
    }
    for (param in targetParameters) {
      param.setType(resultSubstitutor.substitute(inferenceSession.substituteWithInferenceVariables(param.type)))
    }
    method.typeParameterList?.replace(defaultTypeParameterList)
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
        val parameters = classType.parameters
        val replacedParameters = parameters.map { it.accept(this) }.toArray(emptyArray())
        val resolveResult = classType.resolveGenerics()
        return elementFactory.createType(resolveResult.element ?: return null, *replacedParameters)
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
    if (target is PsiIntersectionType || (target is PsiClassType && target.hasParameters())) {
      return target.accept(visitor)
    }
    else {
      val typeParam = elementFactory.createProperTypeParameter(nameGenerator.name, arrayOf(target as PsiClassType))
      typeParameterList.add(typeParam)
      return typeParam.type()
    }
  }


  /**
   * Creates ready for insertion type parameter with correct dependency on other type parameter.
   */
  private fun createBoundedTypeParameterElement(variable: InferenceVariable,
                                                restoreNameSubstitution: PsiSubstitutor,
                                                relationSubstitutor: PsiSubstitutor = PsiSubstitutor.EMPTY,
                                                supertypeAdvice: PsiType? = null): PsiTypeParameter {
    val parametrizedSupertype = supertypeAdvice ?: variable.instantiation
    val superTypes = if (parametrizedSupertype != null && parametrizedSupertype != PsiType.NULL)
      if (parametrizedSupertype is PsiClassType)
        arrayOf(restoreNameSubstitution.substitute(relationSubstitutor.substitute(parametrizedSupertype)) as PsiClassType)
      else
        (parametrizedSupertype as PsiIntersectionType).conjuncts.map {
          restoreNameSubstitution.substitute(relationSubstitutor.substitute(it)) as PsiClassType
        }.toTypedArray()
    else
      variable.upperBounds()
        .filter { it != PsiType.getJavaLangObject(variable.manager, variable.resolveScope) }
        .map { it as PsiClassType }
        .toTypedArray()
    return elementFactory.createProperTypeParameter(restoreNameSubstitution.substitute(variable.type()).canonicalText, superTypes)
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
    for (occurrence in references) {
      if (occurrence is GrReferenceExpression) {
        val call = occurrence.parent
        if (call is GrExpression) {
          resolveSession.addConstraint(ExpressionConstraint(null, call))
        }
      }
    }
  }

}