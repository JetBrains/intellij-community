// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.hint.types

import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.openapi.editor.Editor
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConstructorCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTypeCastExpression
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
    val type = getPresentableType(element.initializerGroovy) ?: return true
    val typeRepresentation = factory.buildRepresentation(type, prefix = ": ").let(factory::roundWithBackground)
    val identifierRange = element.nameIdentifier?.textRange ?: return true
    sink.addInlineElement(identifierRange.endOffset, true, typeRepresentation, false)
    return true
  }

  fun getPresentableType(expression : GrExpression?) : PsiType? {
    if (expression == null || expression is GrConstructorCall || expression is GrTypeCastExpression || expression is GrSafeCastExpression) {
      return null
    }
    val type = expression.type ?: return null
    if (type == PsiType.NULL || type == PsiType.VOID || type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
      return null
    }
    return type
  }
}