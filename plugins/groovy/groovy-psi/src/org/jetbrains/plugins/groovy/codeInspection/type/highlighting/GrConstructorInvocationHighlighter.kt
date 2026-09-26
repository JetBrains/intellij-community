// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.type.highlighting

import com.intellij.codeInspection.IntentionWrapper.wrapToQuickFixes
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.lang.jvm.actions.createConstructorActions
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.requests.CreateConstructorFromGroovyUsageRequest
import org.jetbrains.plugins.groovy.highlighting.HighlightSink
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation

class GrConstructorInvocationHighlighter(
  private val invocation: GrConstructorInvocation,
  sink: HighlightSink
) : ConstructorCallHighlighter(invocation.constructorReference, sink) {

  override val highlightElement: PsiElement get() = invocation.argumentList

  override fun buildFixes(): List<LocalQuickFix> {
    val targetClass = invocation.delegatedClass ?: return emptyList()
    if (!targetClass.manager.isInProject(targetClass)) return emptyList()
    val request = CreateConstructorFromGroovyUsageRequest(invocation, emptyList())
    val createConstructorActions = createConstructorActions(targetClass, request)
    return wrapToQuickFixes(createConstructorActions, invocation.containingFile)
  }
}
