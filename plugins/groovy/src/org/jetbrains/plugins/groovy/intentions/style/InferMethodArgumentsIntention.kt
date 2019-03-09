// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style;

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.impl.source.resolve.graphInference.constraints.TypeCompatibilityConstraint
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSession
import java.util.*


/**
 * @author knisht
 */


internal class InferMethodArgumentsIntention : IntentionAction {
  override fun getText(): String {
    return GroovyIntentionsBundle.message("infer.method.arguments")
  }

  override fun getFamilyName(): String {
    return GroovyIntentionsBundle.message("infer.method.arguments.for.method.declaration")
  }

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    if (editor == null || file == null) {
      return false
    }
    else {
      val offset = editor.caretModel.offset
      return findMethod(file, offset) != null
    }
  }

  private fun findMethod(file: PsiFile, offset: Int): GrMethod? {
    val at = file.findElementAt(offset)
    val method = PsiTreeUtil.getParentOfType(at, GrMethod::class.java, false, GrTypeDefinition::class.java, GrClosableBlock::class.java)
    val textRange = method?.textRange
    if (textRange != null && (!textRange.contains(offset) && !textRange.contains(offset - 1))) {
      return null
    }
    val parameters = method?.parameters
    if (parameters != null && method.typeParameters.isNotEmpty()) {
      return method
    }
    else {
      return null
    }

  }


  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    if (editor == null || file == null) {
      return
    }
    val method: GrMethod = findMethod(file, editor.caretModel.offset) ?: return
    val methodParameters = method.parameters
    val typeParameters = method.typeParameters
    val resolveSession = GroovyInferenceSession(typeParameters, PsiSubstitutor.EMPTY, method)
    val references = HashSet<PsiReference>()
    ReferencesSearch.search(method).forEach { references.add(it) }
    for (occurrence in references) {
      if (occurrence is GrReferenceExpression) {
        val call = occurrence.parent
        if (call is GrCall) {
          val args = call.argumentList ?: continue
          for (i in args.expressionArguments.indices) {
            val arg = args.expressionArguments[i]
            resolveSession.addConstraint(
              TypeCompatibilityConstraint(resolveSession.substituteWithInferenceVariables(methodParameters[i].type),
                                          arg.type ?: continue))
          }
        }
      }
    }
    val substitutor = resolveSession.inferSubst()

    val factory = GroovyPsiElementFactory.getInstance(project)
    for (param in typeParameters) {
      val type = substitutor.substitutionMap[param] ?: return
      val typeElement = factory.createTypeElement(type)
      val typeReferences = ReferencesSearch.search(param).findAll()
      for (ref in typeReferences) {
        ref.element.firstChild.replace(typeElement)
      }
    }
    method.typeParameterList?.delete()
  }

  override fun startInWriteAction(): Boolean {
    return true
  }

}

