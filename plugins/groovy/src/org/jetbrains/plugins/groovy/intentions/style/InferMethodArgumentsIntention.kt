// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style;

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.graphInference.constraints.TypeCompatibilityConstraint
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle
import org.jetbrains.plugins.groovy.intentions.base.Intention
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSession
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type


/**
 * @author knisht
 */


internal class InferMethodArgumentsIntention : Intention() {

  override fun processIntention(element: PsiElement, project: Project, editor: Editor?) {
    val method: GrMethod = element as GrMethod
    val methodParameters = method.parameters
    val elementFactory = GroovyPsiElementFactory.getInstance(project)
    val collectedArgumentTypes = ArrayList<PsiType>()
    val collectedTypeParameters = ArrayList<PsiTypeParameter>()
    val additionalTypeParametersIndex = HashMap<PsiTypeParameter, Int>()
    collectedTypeParameters.addAll(method.typeParameters)
    for (i in methodParameters.indices) {
      val currentArgumentParameter = methodParameters[i]
      if (currentArgumentParameter.typeElement == null) {
        val newTypeParameter = elementFactory.createTypeParameter("T", PsiClassType.EMPTY_ARRAY)
        collectedTypeParameters.add(newTypeParameter)
        collectedArgumentTypes.add(newTypeParameter.type())
        additionalTypeParametersIndex[newTypeParameter] = i
      }
      else {
        collectedArgumentTypes.add(currentArgumentParameter.type)
      }
    }

    val resolveSession = GroovyInferenceSession(collectedTypeParameters.toArray(emptyArray()), PsiSubstitutor.EMPTY, method)
    val references = ReferencesSearch.search(method).findAll()
    for (occurrence in references) {
      if (occurrence is GrReferenceExpression) {
        val call = occurrence.parent
        if (call is GrCall) {
          val args = call.argumentList ?: continue
          for (i in args.expressionArguments.indices) {
            val arg = args.expressionArguments[i]
            resolveSession.addConstraint(
              TypeCompatibilityConstraint(resolveSession.substituteWithInferenceVariables(collectedArgumentTypes[i]),
                                          arg.type ?: continue))
          }
        }
      }
    }
    val substitutor = resolveSession.inferSubst()
    for (typeParameter in collectedTypeParameters) {
      val type = substitutor.substitutionMap[typeParameter] ?: return
      if (additionalTypeParametersIndex.containsKey(typeParameter)) {
        val param: Int = additionalTypeParametersIndex[typeParameter] ?: return
        methodParameters[param].setType(type)
      }
      else {
        val typeElement = elementFactory.createTypeElement(type)
        ReferencesSearch
          .search(typeParameter)
          .forEach { it.element.firstChild.replace(typeElement) }
      }
    }
    method.typeParameterList?.delete()
  }

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

