// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style;

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.impl.source.resolve.graphInference.constraints.TypeCompatibilityConstraint
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle
import org.jetbrains.plugins.groovy.intentions.base.Intention
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
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
    val typeIndex = createTypeParameters(methodParameters, elementFactory)

    val resolveSession = inferTypeArguments(typeIndex, method)
    val substitutor = resolveSession.inferSubst()
    for (typeParameterEntry in typeIndex.entries) {
      methodParameters[typeParameterEntry.key].setType(substitutor.substitute(typeParameterEntry.value))
    }
  }

  private fun inferTypeArguments(typeIndex: HashMap<Int, PsiTypeParameter>,
                                 method: GrMethod): GroovyInferenceSession {
    val resolveSession = GroovyInferenceSession(typeIndex.values.toTypedArray(), PsiSubstitutor.EMPTY, method)
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
    return resolveSession
  }

  private fun createTypeParameters(methodParameters: Array<GrParameter>,
                                   elementFactory: GroovyPsiElementFactory): HashMap<Int, PsiTypeParameter> {
    val typeIndex = HashMap<Int, PsiTypeParameter>()
    for (i in methodParameters.indices) {
      if (methodParameters[i].typeElement == null) {
        val newTypeParameter = elementFactory.createTypeParameter("T", PsiClassType.EMPTY_ARRAY)
        typeIndex.put(i, newTypeParameter);
      }
    }
    return typeIndex
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

