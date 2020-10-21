// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.hint

import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.suggested.endOffset
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall

class GroovyImplicitNullArgumentCollector(editor: Editor, val settings : GroovyImplicitNullArgumentHintProvider.Settings) :
  FactoryInlayHintsCollector(editor) {

  override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
    if (!settings.showNullArgument) {
      return false
    }
    if (element !is GrArgumentList) {
      return true
    }
    if (element.allArguments.isNotEmpty()) {
      return true
    }
    val methodCall = element.parentOfType<GrCall>()?.takeIf { it.argumentList === element } ?: return true
    if (methodCall.hasClosureArguments()) {
      return true
    }
    val resolvedMethod = methodCall.resolveMethod() ?: return true
    if (resolvedMethod.parameterList.parametersCount == 1) {
      // TODO: primitive types
      // TODO: varargs
      sink.addInlineElement(element.firstChild.endOffset, true, factory.roundWithBackground(factory.smallText("null")), false)
    }
    return true
  }
}
