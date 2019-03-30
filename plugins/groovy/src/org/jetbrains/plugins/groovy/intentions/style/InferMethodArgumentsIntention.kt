// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiTypeParameter
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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.*


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
    val methodParameters = method.parameters
    val elementFactory = GroovyPsiElementFactory.getInstance(project)
    val defaultTypeParameterList = method.typeParameterList?.copy()
    val typeIndex = createTypeParameters(method, elementFactory)

    val resolveSession = inferTypeArguments(typeIndex, method)
    val substitutor = resolveSession.inferSubst()
    for (typeParameterEntry in typeIndex.entries) {
      methodParameters[typeParameterEntry.key].setType(substitutor.substitute(typeParameterEntry.value))
    }
    if (defaultTypeParameterList == null) {
      method.typeParameterList?.delete()
    }
    else {
      method.typeParameterList?.replace(defaultTypeParameterList)
    }
  }

  /**
   * Gathers all information about type parameters passed in [typeIndex].
   * @param typeIndex map from position in argument list to corresponding type parameter
   * @param method method for which argument inference is computing
   *
   * @return [GroovyInferenceSession] which can be used for substituting types
   */
  private fun inferTypeArguments(typeIndex: HashMap<Int, PsiTypeParameter>,
                                 method: GrMethod): GroovyInferenceSession {
    for (i in method.parameters.indices) {
      if (method.parameters[i].typeElement == null) {
        method.parameters[i].setType(typeIndex[i]?.type())
      }
    }
    val resolveSession = GroovyInferenceSession(typeIndex.values.toTypedArray(), PsiSubstitutor.EMPTY, method)
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

      override fun visitExpression(expression: GrExpression) {
        if (expression is GrOperatorExpression) {
          resolveSession.addConstraint(OperatorExpressionConstraint(expression))
        }
        else {
          resolveSession.addConstraint(ExpressionConstraint(null, expression))
        }
        super.visitExpression(expression)
      }
    }
    method.accept(visitor)
  }

  /**
   * Searches for [method] calls in file and trues to infer arguments for it
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
  private fun createTypeParameters(method: GrMethod,
                                   elementFactory: GroovyPsiElementFactory): HashMap<Int, PsiTypeParameter> {
    val typeIndex = HashMap<Int, PsiTypeParameter>()
    if (!method.hasTypeParameters()) {
      method.addAfter(elementFactory.createTypeParameterList(), method.firstChild)
    }
    val params = method.typeParameterList

    for (i in method.parameters.indices) {
      if (method.parameters[i].typeElement == null) {
        val newTypeParameter = elementFactory.createTypeParameter(produceTypeParameterName(i), PsiClassType.EMPTY_ARRAY)
        params?.add(newTypeParameter)
        typeIndex[i] = newTypeParameter
      }
    }
    return typeIndex
  }

  /**
   * Predicate for activating intention.
   * @return [PsiElementPredicate], which returns true if ginev element points to method header and there are any non-typed arguments
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

