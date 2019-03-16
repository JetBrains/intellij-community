// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.impl.source.resolve.graphInference.constraints.SubtypingConstraint
import com.intellij.psi.impl.source.resolve.graphInference.constraints.TypeCompatibilityConstraint
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle
import org.jetbrains.plugins.groovy.intentions.base.Intention
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSession
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type


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
    val methodParameters = method.parameters
    val elementFactory = GroovyPsiElementFactory.getInstance(project)
    val typeIndex = createTypeParameters(methodParameters, elementFactory)

    val resolveSession = inferTypeArguments(typeIndex, method)
    val substitutor = resolveSession.inferSubst()
    for (typeParameterEntry in typeIndex.entries) {
      methodParameters[typeParameterEntry.key].setType(substitutor.substitute(typeParameterEntry.value))
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
    val resolveSession = GroovyInferenceSession(typeIndex.values.toTypedArray(), PsiSubstitutor.EMPTY, method)
    collectOuterMethodCalls(method, typeIndex, resolveSession)
    collectInnerMethodCalls(typeIndex, method, resolveSession)
    return resolveSession
  }

  /**
   * Searches for method calls in [method] body and tries to infer arguments for [typeIndex] parameters.
   */
  private fun collectInnerMethodCalls(typeIndex: HashMap<Int, PsiTypeParameter>,
                                      method: GrMethod,
                                      resolveSession: GroovyInferenceSession) {
    for (i in typeIndex.keys) {
      val usages = ReferencesSearch.search(method.parameterList.parameters[i] ?: continue, method.useScope)
      for (usage in usages) {
        if (usage is GrReferenceExpression) {
          val contextCall = PsiTreeUtil.getParentOfType(usage, GrMethodCall::class.java, true) ?: continue
          val usedMethod = contextCall.resolveMethod() ?: continue
          val resolveResult = contextCall.advancedResolve() as GroovyMethodResult
          val methodSignature = usedMethod.getSignature(resolveResult.partialSubstitutor)
          val index = contextCall.argumentList.getExpressionArgumentIndex(usage)
          resolveSession.addConstraint(SubtypingConstraint(resolveSession.substituteWithInferenceVariables(typeIndex[i]?.type()),
                                                           methodSignature.parameterTypes[index]))
        }
      }
    }
  }

  /**
   * Searches for [method] calls in file and trues to infer arguments for [typeIndex] parameters
   */
  private fun collectOuterMethodCalls(method: GrMethod,
                                      typeIndex: HashMap<Int, PsiTypeParameter>,
                                      resolveSession: GroovyInferenceSession) {
    val references = ReferencesSearch.search(method).findAll()
    for (occurrence in references) {
      if (occurrence is GrReferenceExpression) {
        val call = occurrence.parent
        if (call is GrCall) {
          val args = call.argumentList ?: continue
          for (i in args.expressionArguments.indices) {
            if (typeIndex.containsKey(i)) {
              val arg = args.expressionArguments[i]
              resolveSession.addConstraint(
                TypeCompatibilityConstraint(resolveSession.substituteWithInferenceVariables(typeIndex[i]?.type()),
                                            arg.type ?: continue))
            }
          }
        }
      }
    }
  }

  /**
   * Collects all parameters without explicit type and generifies them.
   */
  private fun createTypeParameters(methodParameters: Array<GrParameter>,
                                   elementFactory: GroovyPsiElementFactory): HashMap<Int, PsiTypeParameter> {
    val typeIndex = HashMap<Int, PsiTypeParameter>()
    for (i in methodParameters.indices) {
      if (methodParameters[i].typeElement == null) {
        val newTypeParameter = elementFactory.createTypeParameter("T", PsiClassType.EMPTY_ARRAY)
        typeIndex.put(i, newTypeParameter)
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

