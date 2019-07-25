// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style

import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.codeStyle.GrReferenceAdjuster
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle
import org.jetbrains.plugins.groovy.intentions.base.Intention
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate
import org.jetbrains.plugins.groovy.intentions.style.inference.recursiveSubstitute
import org.jetbrains.plugins.groovy.intentions.style.inference.runInferenceProcess
import org.jetbrains.plugins.groovy.lang.psi.GrQualifiedReference
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier.DEF
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.putAll


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
      val inferredType = method.inferredReturnType
      val returnType = TypesUtil.unboxPrimitiveTypeWrapper(
        if (inferredType == null || inferredType == PsiType.NULL) {
          PsiType.getJavaLangObject(PsiManager.getInstance(project), method.resolveScope)
        }
        else {
          inferredType
        })
      GrReferenceAdjuster.shortenAllReferencesIn(method.setReturnType(returnType))
      method.modifierList.setModifierProperty(DEF, false)
    }
    val virtualMethod = runInferenceProcess(method)
    if (!method.isConstructor) {
      method.returnType = virtualMethod.returnType
      GrReferenceAdjuster.shortenAllReferencesIn(method.returnTypeElementGroovy)
    }
    if (virtualMethod.hasTypeParameters()) {
      when {
        method.hasTypeParameters() -> method.typeParameterList!!.replace(virtualMethod.typeParameterList!!)
        method.isConstructor -> {
          val parameterSubstitutor = collectParameterSubstitutor(virtualMethod)
          virtualMethod.parameters.forEach { it.setType(parameterSubstitutor.recursiveSubstitute(it.type)) }
        }
        else -> method.addAfter(virtualMethod.typeParameterList!!, method.firstChild)
      }
    }
    method.parameters.zip(virtualMethod.parameters).forEach { (actual, inferred) ->
      actual.setType(inferred.type)
      actual.modifierList.setModifierProperty("def", false)
      if (actual.isVarArgs && !inferred.isVarArgs) {
        actual.ellipsisDots!!.delete()
      }
      val currentAnnotations = actual.annotations.map { it.text }
      inferred.annotations.forEach {
        if (it.text !in currentAnnotations) {
          val anno = actual.modifierList.addAnnotation(it.text.substring(1))
          GrReferenceAdjuster.shortenAllReferencesIn((anno as GrAnnotation).originalElement as GroovyPsiElement)
          GrReferenceAdjuster.shortenReference(anno.findAttributeValue("value")?.reference as? GrQualifiedReference<*> ?: return)
        }
      }
    }
    method.typeParameters.forEach { GrReferenceAdjuster.shortenAllReferencesIn(it.originalElement as GroovyPsiElement?) }
    if ((method.isConstructor && !method.hasTypeParameters()) || method.hasModifier(JvmModifier.STATIC)) {
      method.modifierList.setModifierProperty("def", false)
    }
  }

  private fun collectParameterSubstitutor(virtualMethod: GrMethod): PsiSubstitutor {
    val parameterTypes = virtualMethod.typeParameters.map {
      it.extendsListTypes.firstOrNull() ?: PsiType.getJavaLangObject(virtualMethod.manager, virtualMethod.resolveScope)
    }
    return PsiSubstitutor.EMPTY.putAll(virtualMethod.typeParameters, parameterTypes.toTypedArray())
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

