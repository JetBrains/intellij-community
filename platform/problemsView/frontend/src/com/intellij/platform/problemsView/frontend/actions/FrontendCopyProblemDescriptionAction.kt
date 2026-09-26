// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.frontend.actions

import com.intellij.analysis.problemsView.Problem
import com.intellij.analysis.problemsView.toolWindow.NodeAction
import com.intellij.analysis.problemsView.toolWindow.ProblemNodeI
import com.intellij.analysis.problemsView.toolWindow.copyProblemDescription

internal class FrontendCopyProblemDescriptionAction : NodeAction<Problem>() {
  override fun getData(node: Any?): Problem? = (node as? ProblemNodeI)?.problem
  override fun actionPerformed(data: List<Problem>) {
    copyProblemDescription(data)
  }
}

