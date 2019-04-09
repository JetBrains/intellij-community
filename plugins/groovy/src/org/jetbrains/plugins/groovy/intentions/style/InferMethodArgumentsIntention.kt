// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.graphInference.InferenceBound
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.containers.toArray
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
import kotlin.collections.ArrayList


/**
 * @author knisht
 */

/**
 * Intention for deducing method argument types based on method calls and method body
 */
internal class InferMethodArgumentsIntention : Intention() {

  /**
   * Performs inference of parameters for [GrMethod] pointed by [element]
   * @param element used for pointing to processed method
   * @param project current project
   * @param editor current editor
   * @see [Intention.processIntention]
   */
  override fun processIntention(element: PsiElement, project: Project, editor: Editor?) {
    val method: GrMethod = element as GrMethod
    AddReturnTypeFix.applyFix(project, element)
    val elementFactory = GroovyPsiElementFactory.getInstance(project)
    val defaultTypeParameterList = (method.typeParameterList?.copy() ?: elementFactory.createTypeParameterList()) as PsiTypeParameterList
    val parameterIndex = setUpNewTypeParameters(method, elementFactory)
    inferTypeParameters(parameterIndex, method, elementFactory, defaultTypeParameterList)
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
  private fun createBoundedTypeParameterElement(variable: InferenceVariable,
                                                factory: GroovyPsiElementFactory,
                                                restoreNameSubstitution: PsiSubstitutor): PsiTypeParameter {
    val typeParameterAmongSuperclasses = variable.getBounds(InferenceBound.UPPER).firstOrNull {
      restoreNameSubstitution.substitute(it) != it
    }
    val superTypes = if (typeParameterAmongSuperclasses != null)
      arrayOf(restoreNameSubstitution.substitute(typeParameterAmongSuperclasses) as PsiClassType)
    else
      variable.getBounds(InferenceBound.UPPER)
        .filter { it != PsiType.getJavaLangObject(variable.manager, variable.resolveScope) }
        .map { restoreNameSubstitution.substitute(it) as PsiClassType }
        .toTypedArray()
    return factory.createTypeParameter(restoreNameSubstitution.substitute(variable.type()).canonicalText, superTypes)
  }

  /**
   * Gathers all information about type parameters passed in [parameterIndex].
   * @param parameterIndex map from position in argument list to corresponding type parameter
   * @param method method for which argument inference is computing
   *
   * @return [GroovyInferenceSession] which can be used for substituting types
   */
  private fun inferTypeParameters(parameterIndex: Map<GrParameter, PsiTypeParameter>,
                                  method: GrMethod,
                                  elementFactory: GroovyPsiElementFactory,
                                  defaultTypeParameterList: PsiTypeParameterList) {
    val defaultInferenceSession = GroovyInferenceSession(method.typeParameters, PsiSubstitutor.EMPTY, method,
                                                         propagateVariablesToNestedSessions = true)
    collectOuterMethodCalls(method, defaultInferenceSession)
    collectInnerMethodCalls(method, defaultInferenceSession)
    for (typeParam in method.typeParameters.subtract(parameterIndex.values)) {
      defaultInferenceSession.getInferenceVariable(
        defaultInferenceSession.substituteWithInferenceVariables(typeParam.type())).instantiation = typeParam.type()
    }
    val substitutor = defaultInferenceSession.inferSubst()
    for (entry in parameterIndex) {
      val variable = defaultInferenceSession
        .getInferenceVariable(defaultInferenceSession.substituteWithInferenceVariables(entry.key.type))
      if (variable.instantiation.equalsToText(entry.value.type().canonicalText)) {
        defaultTypeParameterList.add(
          createBoundedTypeParameterElement(variable, elementFactory, defaultInferenceSession.restoreNameSubstitution))
      }
      entry.key.setType(substitutor.substitute(entry.value))
    }
    if (parameterIndex.entries.map { it.key.type }.any { it is PsiClassType && it.parameters.any { param -> param is PsiWildcardType } }) {
      deepInference(method, parameterIndex, elementFactory, defaultTypeParameterList)
    }
    else {
      method.typeParameterList?.replace(defaultTypeParameterList)
    }
  }


  private fun createDeeplyParametrizedType(target: PsiClassType,
                                           elementFactory: GroovyPsiElementFactory,
                                           typeParameterList: PsiTypeParameterList, generator: NameGenerator): PsiType {
    val visitor = object : PsiTypeMapper() {

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
        val typeParam = elementFactory.createTypeParameter(generator.name, upperBounds)
        typeParameterList.add(typeParam)
        return typeParam.type()
      }

      override fun visitCapturedWildcardType(type: PsiCapturedWildcardType?): PsiType? {
        type ?: return type
        val upperBound = type.upperBound.accept(this) as PsiClassType
        val typeParam = elementFactory.createTypeParameter(generator.name, arrayOf(upperBound))
        typeParameterList.add(typeParam)
        return typeParam.type()
      }

    }
    if (target.hasParameters()) {
      return target.accept(visitor)
    }
    else {
      val typeParam = elementFactory.createTypeParameter(generator.name, arrayOf(target))
      typeParameterList.add(typeParam)
      return typeParam.type()
    }
  }


  private fun deepInference(method: GrMethod,
                            parameterIndex: Map<GrParameter, PsiTypeParameter>,
                            elementFactory: GroovyPsiElementFactory,
                            defaultTypeParameterList: PsiTypeParameterList) {
    method.typeParameterList?.replace(defaultTypeParameterList)
    val nameGenerator = NameGenerator()
    val defaultTypeParameters = method.typeParameters
    for (parameter in parameterIndex.keys) {
      if (parameter.type is PsiClassType) {
        parameter.setType(
          createDeeplyParametrizedType(parameter.type as PsiClassType, elementFactory, method.typeParameterList!!, nameGenerator))
      }
    }
    val inferenceSession = GroovyInferenceSession(method.typeParameters, PsiSubstitutor.EMPTY, method,
                                                  propagateVariablesToNestedSessions = true)
    collectOuterMethodCalls(method, inferenceSession)
    collectInnerMethodCalls(method, inferenceSession)
    for (param in defaultTypeParameters) {
      // set fixed instantiation for parameters whose type parameter was known before inference
      inferenceSession.getInferenceVariable(inferenceSession.substituteWithInferenceVariables(param.type())).instantiation = param.type()
    }
    inferenceSession.inferSubst()
    val inferenceVars = ArrayList<InferenceVariable>()
    for (typeParameter in method.typeParameters) {
      inferenceVars.add(inferenceSession.getInferenceVariable(inferenceSession.substituteWithInferenceVariables(typeParameter.type())))
    }
    val order = resolveInferenceVariableOrder(inferenceVars, inferenceSession)
    for (equalInferenceVariables in order) {
      val newTypeParam = createBoundedTypeParameterElement(equalInferenceVariables[0], elementFactory,
                                                           inferenceSession.restoreNameSubstitution)
      defaultTypeParameterList.add(newTypeParam)
      equalInferenceVariables.forEach { it.instantiation = newTypeParam.type() }
    }
    val infrenceSubstitutor = inferenceSession.inferSubst()
    for (param in parameterIndex.keys) {
      param.setType(infrenceSubstitutor.substitute(param.type))
    }
    method.typeParameterList?.replace(defaultTypeParameterList)
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

