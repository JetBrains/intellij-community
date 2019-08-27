// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.hint

import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.intentions.style.inference.MethodParameterAugmenter
import org.jetbrains.plugins.groovy.intentions.style.inference.driver.closure.getBlock
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier.DEF
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrVariableEnhancer

class GroovyParameterTypeHintsCollector(editor: Editor,
                                        private val settings: GroovyParameterTypeHintsInlayProvider.Settings) :
  FactoryInlayHintsCollector(editor) {

  override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
    if (!settings.showInferredParameterTypes) {
      return false
    }
    if (element is GrParameter && element.typeElement == null && !element.isVarArgs) {
      val type = if (getBlock(element) == null) {
        MethodParameterAugmenter().inferType(element)
      } else {
        GrVariableEnhancer.getEnhancedType(element)
      }?: return true
      val typeRepresentation = factory.buildRepresentation(type, " ").run { factory.roundWithBackground(this) }
      sink.addInlineElement(element.textOffset, false, typeRepresentation)
    }
    if (settings.showTypeParameterList &&
        element is GrMethod &&
        !element.hasTypeParameters() &&
        element.parameters.any { it.typeElement == null }) {
      val (virtualMethod, _) = MethodParameterAugmenter.createInferenceResult(element) ?: return true
      val typeParameterList = virtualMethod?.typeParameterList?.takeIf { it.typeParameters.isNotEmpty() } ?: return true
      val representation = factory.buildRepresentation(typeParameterList)
      if (element.modifierList.hasModifierProperty(DEF)) {
        sink.addInlineElement(element.modifierList.getModifier(DEF)!!.textRange.endOffset, true, representation)
      }
      else {
        sink.addInlineElement(element.textRange.startOffset, true, representation)
      }
    }
    return true
  }

}