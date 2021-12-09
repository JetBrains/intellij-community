// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.hint.types

import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.openapi.editor.Editor
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiType
import com.intellij.util.containers.mapSmart
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConstructorCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrOperatorExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTypeCastExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter

class GroovyLocalVariableTypeHintsCollector(editor: Editor,
                                            val settings: GroovyLocalVariableTypeHintsInlayProvider.Settings) : FactoryInlayHintsCollector(editor) {

  override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
    if (!element.isValid || element.project.isDefault) {
      return false
    }
    if (element is GrParameter) {
      return true
    }
    if (element !is GrVariableDeclaration) {
      return true
    }
    val variableTypes = getVariableTypes(element)
    for ((type, identifier) in variableTypes) {
      submitInlayHint(identifier, type, sink)
    }
    return true
  }

  private fun submitInlayHint(identifier: PsiIdentifier, type : PsiType, sink: InlayHintsSink) {
    val identifierRange = identifier.textRange ?: return
    val typeRepresentation = factory.buildRepresentation(type)
    val (offset, representation) = if (settings.insertBeforeIdentifier) {
      identifierRange.startOffset to factory.seq(typeRepresentation, factory.smallText(" "))
    } else {
      identifierRange.endOffset to factory.seq(factory.smallText(": "), typeRepresentation)
    }
    sink.addInlineElement(offset, true, factory.roundWithBackground(representation), false)
  }

  private fun getVariableTypes(variableDeclaration: GrVariableDeclaration): List<Pair<PsiType, PsiIdentifier>> {
    if (!variableDeclaration.isTuple) {
      val initializer = variableDeclaration.variables.singleOrNull()?.initializerGroovy ?: return emptyList()
      if (initializer is GrConstructorCall || initializer is GrSafeCastExpression || initializer is GrTypeCastExpression || initializer is GrOperatorExpression) {
        return emptyList()
      }
    }

    return variableDeclaration.variables
      .mapSmart {
        if (it is GrField || it.typeElementGroovy != null) {
          return@mapSmart null
        }
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
