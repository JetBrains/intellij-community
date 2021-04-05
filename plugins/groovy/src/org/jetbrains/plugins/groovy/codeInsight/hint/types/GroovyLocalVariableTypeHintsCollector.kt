// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.hint.types

import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter

class GroovyLocalVariableTypeHintsCollector(editor: Editor) : FactoryInlayHintsCollector(editor) {

  override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
    if (!element.isValid) {
      return false
    }
    if (element is GrParameter) {
      return true
    }
    if (element !is GrVariable) {
      return true
    }
    if (element.typeElement != null) {
      return true
    }
    val type = element.initializerType ?: return false
    val typeRepresentation = factory.buildRepresentation(type, prefix = ": ").let(factory::roundWithBackground)
    val identifierRange = element.nameIdentifier?.textRange ?: return true
    sink.addInlineElement(identifierRange.endOffset, true, typeRepresentation, false)
    return true
  }
}