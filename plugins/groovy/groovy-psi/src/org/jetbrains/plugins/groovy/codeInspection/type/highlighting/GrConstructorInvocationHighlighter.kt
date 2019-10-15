// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.type.highlighting

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.lang.jvm.actions.createConstructorActions
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.requests.CreateConstructorFromGroovyUsageRequest
import org.jetbrains.plugins.groovy.highlighting.HighlightSink
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList

class GrConstructorInvocationHighlighter(val invocation: GrConstructorInvocation,
                                         sink: HighlightSink) : ConstructorCallHighlighter(invocation.constructorReference, sink) {

  override fun getArgumentList(): GrArgumentList = invocation.argumentList

  override fun getHighlightElement(): PsiElement = getArgumentList()

  override fun generateFixes(results: Set<GroovyMethodResult>): Array<LocalQuickFix> {
    val fixes = super.generateFixes(results)

    val targetClass = invocation.delegatedClass ?: return fixes
    if (!targetClass.manager.isInProject(targetClass)) return fixes
    val request = CreateConstructorFromGroovyUsageRequest(invocation, emptyList())

    val createConstructorActions = createConstructorActions(targetClass, request)
    return fixes + QuickfixUtil.intentionsToFixes(invocation, createConstructorActions)
  }
}
