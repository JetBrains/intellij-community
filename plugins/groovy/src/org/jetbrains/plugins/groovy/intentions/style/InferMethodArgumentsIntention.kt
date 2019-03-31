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
    val needTypeParameters = method.hasTypeParameters()
    val newTypeParameterList = elementFactory.createTypeParameterList()
    method.typeParameters.forEach { newTypeParameterList.add(it.copy()) }
    val parameterIndex = setUpNewTypeParameters(method, elementFactory)
    val resolveSession = inferTypeArguments(parameterIndex, method)
    val substitutor = resolveSession.inferSubst()

    var needAdditionalTypeParameters = false
    for (parameterEntry in parameterIndex.entries) {
      val variable = resolveSession
        .getInferenceVariable(resolveSession.substituteWithInferenceVariables(parameterEntry.key.type))
      if (variable.instantiation == PsiType.NULL) {
        needAdditionalTypeParameters = true
        newTypeParameterList.add(createBoundedTypeParameterElement(variable, elementFactory))
      }
      parameterEntry.key.setType(substitutor.substitute(parameterEntry.value))
    }
    if (!needTypeParameters && !needAdditionalTypeParameters) {
      method.typeParameterList?.delete()
    }
    else {
      method.typeParameterList?.replace(newTypeParameterList)
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
                                 method: GrMethod): GroovyInferenceSession {
    val resolveSession = GroovyInferenceSession(parameterIndex.values.toTypedArray(), PsiSubstitutor.EMPTY, method,
                                                propagateVariablesToNestedSessions = true)
    collectOuterMethodCalls(method, resolveSession)
    collectInnerMethodCalls(method, resolveSession)
    return resolveSession
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
    val parameterIndex = HashMap<GrParameter, PsiTypeParameter>()

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

