// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.hint

import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.refactoring.suggested.endOffset
import org.jetbrains.plugins.groovy.intentions.style.inference.MethodParameterAugmenter
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier.DEF
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeAugmenter
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrVariableEnhancer

class GroovyParameterTypeHintsCollector(editor: Editor,
                                        private val settings: GroovyParameterTypeHintsInlayProvider.Settings) :
  FactoryInlayHintsCollector(editor) {

  override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
    if (!settings.showInferredParameterTypes) {
      return false
    }
    if (!element.isValid) {
      return false
    }
    if (element is GrParameter && element.typeElement == null && !element.isVarArgs) {
      val type: PsiType = getRepresentableType(element) ?: return true
      val typeRepresentation = factory.buildRepresentation(type, " ").run { factory.roundWithBackground(this) }
      sink.addInlineElement(element.textOffset, false, typeRepresentation, false)
    }
    if (element is GrClosableBlock && element.parameterList.isEmpty) {
      val itParameter: GrParameter = element.allParameters.singleOrNull() ?: return true
      val type: PsiType = getRepresentableType(itParameter) ?: return true
      val textRepresentation: InlayPresentation = factory.roundWithBackground(factory.buildRepresentation(type, " it -> "))
      sink.addInlineElement(element.lBrace.endOffset, true, textRepresentation, false)
    }
    if (settings.showTypeParameterList &&
        element is GrMethod &&
        !element.hasTypeParameters() &&
        element.parameters.any { it.typeElement == null }) {
      val (virtualMethod, _) = MethodParameterAugmenter.createInferenceResult(element) ?: return true
      val typeParameterList = virtualMethod?.typeParameterList?.takeIf { it.typeParameters.isNotEmpty() } ?: return true
      val representation = factory.buildRepresentation(typeParameterList)
      if (element.modifierList.hasModifierProperty(DEF)) {
        sink.addInlineElement(element.modifierList.getModifier(DEF)!!.textRange.endOffset, true, representation, false)
      }
      else {
        sink.addInlineElement(element.textRange.startOffset, true, representation, false)
      }
    }
    return true
  }

  private fun getRepresentableType(variable: GrVariable): PsiType? {
    val inferredType: PsiType? = GrVariableEnhancer.getEnhancedType(variable) ?: TypeAugmenter.inferAugmentedType(variable)
    return inferredType?.takeIf { !it.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) }
  }

}
