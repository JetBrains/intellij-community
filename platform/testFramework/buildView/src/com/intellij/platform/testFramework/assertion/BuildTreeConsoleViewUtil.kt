// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.assertion

import com.intellij.build.BuildTreeConsoleView
import com.intellij.build.ExecutionNode
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.unwrapDelegate
import com.intellij.platform.testFramework.assertion.treeAssertion.userObject
import com.intellij.util.concurrency.annotations.RequiresEdt
import javax.swing.tree.TreePath

@RequiresEdt
fun BuildTreeConsoleView.getExecutionNode(path: TreePath): ExecutionNode {
  return findNode(path.userObject) ?: throw AssertionError(
    "Cannot find ExecutionNode by TreePath: $path"
  )
}

@RequiresEdt
fun BuildTreeConsoleView.getSelectionText(): String {
  val selectionPath = tree.selectionPath ?: throw AssertionError(
    "Nothing is selected"
  )
  val selectionNode = getExecutionNode(selectionPath)
  val selectionConsole = resolveNodeConsole(selectionNode)
  val consoleView = selectionConsole.unwrapDelegate() as ConsoleViewImpl
  val editor = consoleView.editor ?: throw AssertionError(
    "No console editor by TreePath: $selectionPath"
  )
  return editor.caretModel.allCarets.joinToString("") { it.selectedText ?: "" }
}
