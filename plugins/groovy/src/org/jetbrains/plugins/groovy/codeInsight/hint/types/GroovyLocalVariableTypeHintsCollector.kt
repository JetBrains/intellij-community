// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInsight.hint.types

import com.intellij.codeInsight.hints.JavaTypeHintsFactory
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.psi.*
import com.intellij.util.containers.mapSmart
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConstructorCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrOperatorExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTypeCastExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.impl.GrLiteralClassType

class GroovyLocalVariableTypeHintsCollector : SharedBypassCollector {

  override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
    if (!element.isValid || element.project.isDefault) {
      return
    }
    if (element is GrParameter) {
      return
    }
    if (element !is GrVariableDeclaration) {
      return
    }
    val variableTypes = getVariableTypes(element)
    for ((type, identifier) in variableTypes) {
      submitInlayHint(identifier, type, sink)
    }
  }

  private fun submitInlayHint(identifier: PsiIdentifier, type: PsiType, sink: InlayTreeSink) {
    val identifierRange = identifier.textRange ?: return
    sink.addPresentation(InlineInlayPosition(identifierRange.endOffset, relatedToPrevious = true), hasBackground = true) {
      text(": ")
      JavaTypeHintsFactory.typeHint(type, this)
    }
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
        type != PsiTypes.nullType() &&
        type != PsiTypes.voidType() &&
        type !is GrLiteralClassType &&
        !type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)
      }
  }
}
