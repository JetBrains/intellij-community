// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.featuresSuggester.listeners

import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XEvaluationListener
import com.intellij.xdebugger.XExpression
import training.featuresSuggester.SuggestingUtils.handleAction
import training.featuresSuggester.actions.InlineEvaluatorInvokedAction

private class EvaluationListener : XEvaluationListener {
  override fun inlineEvaluatorInvoked(project: Project, expression: XExpression) {
    handleAction(project, InlineEvaluatorInvokedAction(project, expression, System.currentTimeMillis()))
  }
}
