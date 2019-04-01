// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.graphInference.InferenceBound
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle
import org.jetbrains.plugins.groovy.intentions.base.Intention
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrOperatorExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.*
import java.util.*


/**
 * @author knisht
 */

/**
 * Intention for deducing method argument types based on method calls and method body
 */
internal class InferMethodArgumentsIntention : Intention() {

  /**
   * Performs inference of arguments for [GrMethod] pointed by [element]
   * @param element used for pointing to processed method
   * @param project current project
   * @param editor current editor
   * @see [Intention.processIntention]
   */
  override fun processIntention(element: PsiElement, project: Project, editor: Editor?) {
    val method: GrMethod = element as GrMethod
    AddReturnTypeFix.applyFix(project, element)
    val elementFactory = GroovyPsiElementFactory.getInstance(project)
    val newTypeParameterList = elementFactory.createTypeParameterList()
    method.typeParameters.forEach { newTypeParameterList.add(it.copy()) }
    val parameterIndex = setUpNewTypeParameters(method, elementFactory)
    inferTypeArguments(parameterIndex, method, elementFactory, newTypeParameterList)
    if (method.typeParameters.isEmpty()) {
      method.typeParameterList?.delete()
    }
  }


  /**
   * Creates [PsiElement] with type parameter and it's superclasses from [variable] representation.
   * Used when [GroovyInferenceSession] fails to infer type for [variable], so type should not be reified.
   *
   * @param variable [InferenceVariable] that cannot be instantiated to any unambiguous type.
   * @param factory factory for creating [PsiElement]
   *
   * @return [PsiElement] with necessary bound. Ready for addition in [PsiParameterList]
   */
  private fun createBoundedTypeParameterElement(variable: InferenceVariable, factory: GroovyPsiElementFactory): PsiElement {
    val superTypes = variable.getBounds(InferenceBound.UPPER)
      .filter { it != PsiType.getJavaLangObject(variable.manager, variable.resolveScope) }
      .map { it as PsiClassType }
      .toTypedArray()
    return factory.createTypeParameter(variable.parameter.originalElement.text, superTypes)
  }

  /**
   * Gathers all information about type parameters passed in [parameterIndex].
   * @param parameterIndex map from position in argument list to corresponding type parameter
   * @param method method for which argument inference is computing
   *
   * @return [GroovyInferenceSession] which can be used for substituting types
   */
  private fun inferTypeArguments(parameterIndex: Map<GrParameter, PsiTypeParameter>,
                                 method: GrMethod,
                                 elementFactory: GroovyPsiElementFactory,
                                 defaultTypeParameterList: PsiTypeParameterList) {
    val defaultResolveSession = GroovyInferenceSession(parameterIndex.values.toTypedArray(), PsiSubstitutor.EMPTY, method,
                                                       propagateVariablesToNestedSessions = true)
    collectOuterMethodCalls(method, defaultResolveSession)
    collectInnerMethodCalls(method, defaultResolveSession)
    val substitutor = defaultResolveSession.inferSubst()
    for (entry in parameterIndex) {
      val variable = defaultResolveSession
        .getInferenceVariable(defaultResolveSession.substituteWithInferenceVariables(entry.key.type))
      if (variable.instantiation == PsiType.NULL) {
        defaultTypeParameterList.add(createBoundedTypeParameterElement(variable, elementFactory))
      }
      entry.key.setType(substitutor.substitute(entry.value))
    }
    if (parameterIndex.entries.map { it.key.type }.any { it is PsiClassType && it.parameters.any { param -> param is PsiWildcardType } }) {
      runWildcardInferencePhase(method, parameterIndex, elementFactory, defaultTypeParameterList)
    }
    else {
      method.typeParameterList?.replace(defaultTypeParameterList)
    }
  }


  /**
   * Replaces all wildcards with new type parameters and then runs [GroovyInferenceSession] to collect more dependencies.
   * All type parameters that wildcards gain will not be regarded as [InferenceVariable].
   *
   * @param method method that is currently processed
   * @param parameterIndex map from parameters to their parameter types. These parameters are under inference in this intention.
   * @param elementFactory factory which can produce new [PsiElement]
   * @param defaultTypeParameterList original parameter list that method has in the very beginning.
   * It can vary through inference phases, so it is important to keep track on initial type parameters.
   *
   */
  private fun runWildcardInferencePhase(method: GrMethod,
                                        parameterIndex: Map<GrParameter, PsiTypeParameter>,
                                        elementFactory: GroovyPsiElementFactory,
                                        defaultTypeParameterList: PsiTypeParameterList) {
    method.typeParameterList?.replace(defaultTypeParameterList)
    val typeParameterList = method.typeParameterList!!
    val remainingTypeParameterList = typeParameterList.copy()
    var nameCounter = 0
    val newParameterIndex = LinkedHashMap<GrParameter, PsiTypeParameter>()
    for (paramEntry in parameterIndex) {
      val requiredType = paramEntry.key.type
      if (requiredType is PsiClassType && requiredType.hasParameters()) {
        val insteadOfWildcards = ArrayList<PsiType>()
        for (classParameterType in requiredType.parameters.filter { it is PsiWildcardType }) {
          val newTypeParameter = elementFactory.createTypeParameter(produceTypeParameterName(nameCounter), PsiClassType.EMPTY_ARRAY)
          ++nameCounter
          // todo: hide name generating in some helper class
          typeParameterList.add(newTypeParameter)
          remainingTypeParameterList.add(newTypeParameter.copy())
          insteadOfWildcards.add(newTypeParameter.type())
        }
        val parametrizedRequiredType = elementFactory.createType(requiredType.resolve()!!, *insteadOfWildcards.toTypedArray())
        paramEntry.key.setType(parametrizedRequiredType)
      }
      else {
        val newTypeParameter = elementFactory.createTypeParameter(produceTypeParameterName(nameCounter), PsiClassType.EMPTY_ARRAY)
        ++nameCounter
        typeParameterList.add(newTypeParameter)
        newParameterIndex[paramEntry.key] = newTypeParameter
        paramEntry.key.setType(newTypeParameter.type())
      }
    }
    val inferenceSession = GroovyInferenceSession(newParameterIndex.values.toTypedArray(), PsiSubstitutor.EMPTY, method,
                                                  propagateVariablesToNestedSessions = true)
    collectInnerMethodCalls(method, inferenceSession)
    val newSubst = inferenceSession.inferSubst()
    for (entry in newParameterIndex) {
      entry.key.setType(newSubst.substitute(entry.value))
    }
    method.typeParameterList?.replace(remainingTypeParameterList)
  }


  /**
   * Searches for method calls in [method] body and tries to infer parameter types.
   */
  private fun collectInnerMethodCalls(method: GrMethod,
                                      resolveSession: GroovyInferenceSession) {
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
   * Searches for [method] calls in file and tries to infer arguments for it
   */
  private fun collectOuterMethodCalls(method: GrMethod,
                                      resolveSession: GroovyInferenceSession) {
    val references = ReferencesSearch.search(method).findAll()
    for (occurrence in references) {
      if (occurrence is GrReferenceExpression) {
        val call = occurrence.parent
        if (call is GrCall) {
          val methodResult = call.advancedResolve() as GroovyMethodResult
          resolveSession.addConstraint(MethodCallConstraint(null, methodResult, method.context ?: continue))
        }
      }
    }
  }

  /**
   * Collects all parameters without explicit type and generifies them.
   */
  private fun setUpNewTypeParameters(method: GrMethod,
                                     elementFactory: GroovyPsiElementFactory): Map<GrParameter, PsiTypeParameter> {
    if (!method.hasTypeParameters()) {
      method.addAfter(elementFactory.createTypeParameterList(), method.firstChild)
    }
    val typeParameters = method.typeParameterList ?: return emptyMap()
    val parameterIndex = LinkedHashMap<GrParameter, PsiTypeParameter>()

    var counter = 0
    for (param in method.parameters) {
      if (param.typeElement == null) {
        val newTypeParameter = elementFactory.createTypeParameter(produceTypeParameterName(counter), PsiClassType.EMPTY_ARRAY)
        typeParameters.add(newTypeParameter)
        parameterIndex[param] = newTypeParameter
        param.setType(newTypeParameter.type())
        ++counter
      }
    }
    return parameterIndex
  }

  /**
   * Predicate for activating intention.
   * @return [PsiElementPredicate], which returns true if given element points to method header and there are any non-typed arguments
   */
  override fun getElementPredicate(): PsiElementPredicate {
    return object : PsiElementPredicate {
      override fun satisfiedBy(element: PsiElement): Boolean {
        return element is GrMethod && (element !is GrOpenBlock) && element.parameters.any { it.typeElement == null }
      }

    }
  }

  override fun isStopElement(element: PsiElement?): Boolean {
    return element is GrOpenBlock || element is GrMethod || super.isStopElement(element)
  }

  override fun getText(): String {
    return GroovyIntentionsBundle.message("infer.method.arguments")
  }

  override fun getFamilyName(): String {
    return GroovyIntentionsBundle.message("infer.method.arguments.for.method.declaration")
  }


}

