// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.type.highlighting

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.lang.jvm.actions.createConstructorActions
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil.intentionsToFixes
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.requests.CreateConstructorFromGroovyUsageRequest
import org.jetbrains.plugins.groovy.highlighting.HighlightSink
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyCallReference

class GrNewExpressionHighlighter(val newExpression: GrNewExpression,
                                 reference: GroovyCallReference,
                                 sink: HighlightSink) : ConstructorCallHighlighter(reference, sink) {

  override fun getArgumentList(): GrArgumentList? = newExpression.argumentList

  override fun getHighlightElement(): PsiElement {
    val element = getArgumentList() ?: newExpression.referenceElement
    if (element != null) return element
    throw IncorrectOperationException("reference of new expression should exist if it is a constructor call")
  }

  override fun generateFixes(results: Set<GroovyMethodResult>): Array<LocalQuickFix> {
    val fixes = super.generateFixes(results)
    val constructor = reference.advancedResolve().element
    val targetClass = PsiTreeUtil.getParentOfType(constructor, PsiClass::class.java) ?: return fixes
    if (!targetClass.manager.isInProject(targetClass)) return fixes
    val request = CreateConstructorFromGroovyUsageRequest(newExpression, emptyList())

    val createConstructorActions = createConstructorActions(targetClass, request)
    return fixes + intentionsToFixes(newExpression, createConstructorActions)
  }
}
