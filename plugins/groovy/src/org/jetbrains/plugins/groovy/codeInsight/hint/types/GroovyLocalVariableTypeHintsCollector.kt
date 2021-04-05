// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.hint.types

import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.openapi.editor.Editor
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiType
import com.intellij.util.containers.mapSmart
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConstructorCall
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
    if (element !is GrVariableDeclaration) {
      return true
    }
    val results = getPresentableType(element)
    for ((type, identifier) in results) {
      val typeRepresentation = factory.buildRepresentation(type, prefix = ": ").let(factory::roundWithBackground)
      val identifierRange = identifier.textRange ?: continue
      sink.addInlineElement(identifierRange.endOffset, true, typeRepresentation, false)
    }
    return true
  }

  private fun getPresentableType(variableDeclaration: GrVariableDeclaration): List<Pair<PsiType, PsiIdentifier>> {
    if (!variableDeclaration.isTuple) {
      val initializer = variableDeclaration.variables.singleOrNull()?.initializerGroovy ?: return emptyList()
      if (initializer is GrConstructorCall || initializer is GrSafeCastExpression || initializer is GrTypeCastExpression) {
        return emptyList()
      }
    }

    return variableDeclaration.variables
      .mapSmart {
        val type = it.typeGroovy ?: return@mapSmart null
        val identifier = it.nameIdentifier ?: return@mapSmart null
        type to identifier
      }.filterNotNull()
      .filter { (type, _) ->
        type != PsiType.NULL &&
        type != PsiType.VOID &&
        !type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)
      }
  }
}