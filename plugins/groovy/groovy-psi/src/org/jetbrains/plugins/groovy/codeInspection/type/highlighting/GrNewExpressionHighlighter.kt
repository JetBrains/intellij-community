// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.type.highlighting

import com.intellij.codeInspection.IntentionWrapper.wrapToQuickFixes
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.lang.jvm.actions.createConstructorActions
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.requests.CreateConstructorFromGroovyUsageRequest
import org.jetbrains.plugins.groovy.highlighting.HighlightSink
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyCallReference

class GrNewExpressionHighlighter(
  private val newExpression: GrNewExpression,
  reference: GroovyCallReference,
  sink: HighlightSink
) : ConstructorCallHighlighter(reference, sink) {

  override val highlightElement: PsiElement
    get() = requireNotNull(newExpression.argumentList ?: newExpression.referenceElement) {
      "reference of new expression should exist if it is a constructor call"
    }

  override fun buildFixes(): List<LocalQuickFix> {
    val constructor = reference.advancedResolve().element
    val targetClass = PsiTreeUtil.getParentOfType(constructor, PsiClass::class.java) ?: return emptyList()
    if (!targetClass.manager.isInProject(targetClass)) return emptyList()
    val request = CreateConstructorFromGroovyUsageRequest(newExpression, emptyList())

    val createConstructorActions = createConstructorActions(targetClass, request)
    return wrapToQuickFixes(createConstructorActions, newExpression.containingFile)
  }
}
