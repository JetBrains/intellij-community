// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GroovyUnresolvedAccessChecker")

package org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.QuickFixActionRegistrar
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.QuickFixFactory
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider
import com.intellij.codeInspection.ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.groovy.GroovyBundle.message
import org.jetbrains.plugins.groovy.codeInspection.GroovyQuickFixFactory
import org.jetbrains.plugins.groovy.highlighting.HighlightSink
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil

fun checkUnresolvedReference(
  expression: GrReferenceExpression,
  highlightIfGroovyObjectOverridden: Boolean,
  highlightIfMissingMethodsDeclared: Boolean,
  highlightSink: HighlightSink
) {
  val referenceNameElement = expression.referenceNameElement ?: return
  val referenceName = expression.referenceName ?: return

  val resolveResult = GrUnresolvedAccessChecker.getBestResolveResult(expression)
  if (resolveResult.element != null) {
    if (!GrUnresolvedAccessChecker.isStaticOk(resolveResult)) {
      highlightSink.registerProblem(referenceNameElement, LIKE_UNKNOWN_SYMBOL, message("cannot.reference.non.static", referenceName))
    }
    return
  }

  if (ResolveUtil.isKeyOfMap(expression) || ResolveUtil.isClassReference(expression)) {
    return
  }

  if (!highlightIfGroovyObjectOverridden && GrUnresolvedAccessChecker.areGroovyObjectMethodsOverridden(expression)) return
  if (!highlightIfMissingMethodsDeclared && GrUnresolvedAccessChecker.areMissingMethodsDeclared(expression)) return

  val actions = ArrayList<IntentionAction>()
  val parent = expression.parent
  if (parent is GrMethodCall) {
    actions += GroovyQuickFixFactory.getInstance().createGroovyStaticImportMethodFix(parent)
  }
  else {
    actions += generateCreateClassActions(expression)
    actions += generateAddImportActions(expression)
  }
  actions += generateReferenceExpressionFixes(expression)
  val registrar = object : QuickFixActionRegistrar {
    override fun register(action: IntentionAction) {
      actions += action
    }

    override fun register(fixRange: TextRange, action: IntentionAction, key: HighlightDisplayKey?) {
      actions += action
    }
  }
  UnresolvedReferenceQuickFixProvider.registerReferenceFixes(expression, registrar)
  QuickFixFactory.getInstance().registerOrderEntryFixes(registrar, expression)
  highlightSink.registerProblem(referenceNameElement, LIKE_UNKNOWN_SYMBOL, message("cannot.resolve", referenceName), actions)
}
