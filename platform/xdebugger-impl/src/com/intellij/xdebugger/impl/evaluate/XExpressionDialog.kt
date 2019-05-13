/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.xdebugger.impl.evaluate

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import javax.swing.JComponent

/**
 * @author egor
 */
open class XExpressionDialog(project: Project,
                        editorsProvider: XDebuggerEditorsProvider,
                        historyId: String,
                        title: String,
                        sourcePosition: XSourcePosition?,
                        expression: XExpression?) : DialogWrapper(project) {
  private val myInputComponent: EvaluationInputComponent

  init {
    myInputComponent = ExpressionInputComponent(project, editorsProvider, historyId, sourcePosition, expression, myDisposable, false)
    setTitle(title)
    init()
  }

  override fun createCenterPanel(): JComponent? {
    return myInputComponent.mainComponent
  }

  override fun getPreferredFocusedComponent(): JComponent? {
    return myInputComponent.inputEditor.preferredFocusedComponent
  }

  fun getExpression(): XExpression {
    val editor = myInputComponent.inputEditor
    editor.saveTextInHistory()
    return editor.expression
  }

  override fun getDimensionServiceKey(): String? {
    return "#debugger.expression.dialog"
  }
}
