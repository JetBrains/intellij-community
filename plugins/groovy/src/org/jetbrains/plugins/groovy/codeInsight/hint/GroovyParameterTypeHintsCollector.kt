// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.hint

import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.codeStyle.GrReferenceAdjuster
import org.jetbrains.plugins.groovy.intentions.style.inference.MethodParameterAugmenter
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter

class GroovyParameterTypeHintsCollector(editor: Editor) : FactoryInlayHintsCollector(editor) {

  override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
    if (element is GrParameter && element.typeElement == null) {
      val type = MethodParameterAugmenter().inferType(element) ?: return false
      val typeElement = GroovyPsiElementFactory.getInstance(element.project).createTypeElement(type)
      GrReferenceAdjuster.shortenAllReferencesIn(typeElement)
      val factory = PresentationFactory(editor as EditorImpl)
      sink.addInlineElement(element.textRange.startOffset, true, factory.roundWithBackground(factory.smallText(type.presentableText + " ")))
    }
    return true
  }

}