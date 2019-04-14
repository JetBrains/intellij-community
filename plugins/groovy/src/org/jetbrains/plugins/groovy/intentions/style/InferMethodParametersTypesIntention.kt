// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.graphInference.InferenceBound
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable
import com.intellij.util.containers.toArray
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle
import org.jetbrains.plugins.groovy.intentions.base.Intention
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate
import org.jetbrains.plugins.groovy.intentions.style.inference.InferenceVariableGraph
import org.jetbrains.plugins.groovy.intentions.style.inference.MethodParametersInferenceProcessor
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSession
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type


/**
 * @author knisht
 */

/**
 * Intention for deducing method argument types based on method calls and method body
 */
internal class InferMethodParametersTypesIntention : Intention() {

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
    val processor = MethodParametersInferenceProcessor(method, elementFactory)
    processor.runInferenceProcess()
  }

  /**
   * Predicate for activating intention.
   * @return [PsiElementPredicate], which returns true if given element points to method header and there are any non-typed arguments
   */
  override fun getElementPredicate(): PsiElementPredicate {
    return PsiElementPredicate { element -> element is GrMethod && (element !is GrOpenBlock) && element.parameters.any { it.typeElement == null } }
  }

  override fun isStopElement(element: PsiElement?): Boolean {
    return element is GrOpenBlock || element is GrMethod || super.isStopElement(element)
  }

  override fun getText(): String {
    return GroovyIntentionsBundle.message("infer.method.parameters.types")
  }

  override fun getFamilyName(): String {
    return GroovyIntentionsBundle.message("infer.method.parameters.types.for.method.declaration")
  }


}

