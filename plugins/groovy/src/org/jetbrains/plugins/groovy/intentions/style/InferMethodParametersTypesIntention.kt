// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle
import org.jetbrains.plugins.groovy.intentions.base.Intention
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate
import org.jetbrains.plugins.groovy.intentions.style.inference.MethodParametersInferenceProcessor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod


/**
 * Intention for deducing method argument types based on method calls and method body
 */
internal class InferMethodParametersTypesIntention : Intention() {

  /**
   * Performs inference of parameters for [GrMethod] referenced by [element]
   * @param element used for referencing to processed method
   * @see [Intention.processIntention]
   */
  override fun processIntention(element: PsiElement, project: Project, editor: Editor?) {
    val method: GrMethod = element as GrMethod
    if (!method.isConstructor) {
      AddReturnTypeFix.applyFix(project, element)
    }
    val processor = MethodParametersInferenceProcessor(method)
    val virtualMethod = processor.runInferenceProcess()
    if (virtualMethod.hasTypeParameters()) {
      if (method.hasTypeParameters()) {
        method.typeParameterList!!.replace(virtualMethod.typeParameterList!!)
      }
      else {
        method.addAfter(virtualMethod.typeParameterList!!, method.firstChild)
      }
    }
    else {
      method.typeParameterList?.delete()
    }
    method.parameters.zip(virtualMethod.parameters).forEach { (actual, inferred) -> actual.setType(inferred.type) }
    if (method.isConstructor || method.returnTypeElementGroovy != null && !method.hasTypeParameters()) {
      method.modifierList.setModifierProperty("def", false)
    }
  }

  /**
   * Predicate for activating intention.
   * @return [PsiElementPredicate], which returns true if given element points to method header and there are any non-typed arguments
   */
  override fun getElementPredicate(): PsiElementPredicate =
    PsiElementPredicate { element -> element is GrMethod && (element !is GrOpenBlock) && element.parameters.any { it.typeElement == null } }

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

