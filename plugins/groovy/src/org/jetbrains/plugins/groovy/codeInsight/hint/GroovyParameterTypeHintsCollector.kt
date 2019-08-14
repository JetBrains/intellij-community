// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.hint

import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.SpacePresentation
import com.intellij.openapi.editor.Editor
import com.intellij.psi.*
import org.jetbrains.plugins.groovy.intentions.style.inference.MethodParameterAugmenter
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier.DEF
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod

class GroovyParameterTypeHintsCollector(editor: Editor,
                                        private val settings: GroovyParameterTypeHintsInlayProvider.Settings) :
  FactoryInlayHintsCollector(editor) {

  override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
    if (!settings.showInferredParameterTypes) {
      return false
    }
    if (element is GrParameter && element.typeElement == null && !element.isVarArgs) {
      val type = MethodParameterAugmenter().inferType(element) ?: return true
      sink.addInlineElement(startOffsetWithSkippedWhitespaces(element), false, buildRepresentation(factory, type))
    }
    if (settings.showTypeParameterList &&
        element is GrMethod &&
        !element.hasTypeParameters() &&
        element.parameters.any { it.typeElement == null }) {
      val (virtualMethod, _) = MethodParameterAugmenter.createInferenceResult(element) ?: return true
      val typeParameterList = virtualMethod?.typeParameterList?.takeIf { it.typeParameters.isNotEmpty() } ?: return true
      val representation = factory.roundWithBackground(factory.smallText(typeParameterList.text))
      if (element.modifierList.hasModifierProperty(DEF)) {
        sink.addInlineElement(element.modifierList.getModifier(DEF)!!.textRange.endOffset, true, representation)
      }
      else {
        sink.addInlineElement(element.textRange.startOffset, true, representation)
      }
    }
    return true
  }


  companion object {
    fun buildRepresentation(factory: PresentationFactory, type: PsiType): InlayPresentation {
      return type.accept(object : PsiTypeVisitor<InlayPresentation>() {
        private val visitor = this

        override fun visitClassType(classType: PsiClassType): InlayPresentation = with(factory) {
          val classParameters = if (classType.hasParameters()) {
            listOf(smallText("<"), *classType.parameters.map { it.accept(visitor) }.toTypedArray(), smallText(">"))
          }
          else {
            emptyList()
          }
          return seq(
            psiSingleReference(smallText(classType.name)) { classType.resolve() },
            *classParameters.toTypedArray()
          )
        }

        override fun visitArrayType(arrayType: PsiArrayType): InlayPresentation = with(factory) {
          return seq(
            arrayType.componentType.accept(visitor),
            smallText("[]")
          )
        }

        override fun visitWildcardType(wildcardType: PsiWildcardType): InlayPresentation = with(factory) {
          val boundRepresentation = wildcardType.bound?.accept(visitor)
          val boundKeywordRepresentation = when {
            wildcardType.isExtends -> seq(smallText(" extends "), boundRepresentation!!)
            wildcardType.isSuper -> seq(smallText(" super "), boundRepresentation!!)
            else -> SpacePresentation(0, 0)
          }
          return seq(
            smallText("?"),
            boundKeywordRepresentation
          )
        }

        override fun visitPrimitiveType(primitiveType: PsiPrimitiveType): InlayPresentation = with(factory) {
          return smallText(primitiveType.name)
        }

      }).let { factory.roundWithBackground(it) }
    }


    fun startOffsetWithSkippedWhitespaces(element: PsiElement): Int {
      var currentElement = element
      while (currentElement.prevSibling is PsiWhiteSpace) {
        currentElement = element.prevSibling
      }
      return currentElement.textOffset
    }

  }

}