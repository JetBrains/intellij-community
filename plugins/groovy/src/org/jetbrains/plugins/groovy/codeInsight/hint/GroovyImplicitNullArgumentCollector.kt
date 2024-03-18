// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInsight.hint

import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.suggested.endOffset
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil

class GroovyImplicitNullArgumentCollector : SharedBypassCollector {
  override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
    if (element !is GrArgumentList) {
      return
    }
    if (element.allArguments.isNotEmpty()) {
      return
    }
    val methodCall = element.parentOfType<GrCall>()?.takeIf { it.argumentList === element } ?: return
    if (PsiUtil.isEligibleForInvocationWithNull(methodCall)) {
      sink.addPresentation(InlineInlayPosition(element.firstChild.endOffset, relatedToPrevious = true), hasBackground = true) {
        text("null")
      }
    }
  }
}
