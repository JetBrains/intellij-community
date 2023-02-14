package com.intellij.cce.evaluation

import com.intellij.cce.util.Progress
import com.intellij.cce.workspace.EvaluationWorkspace

interface TwoWorkspaceHandler {
  fun invoke(workspace1: EvaluationWorkspace, workspace2: EvaluationWorkspace, indicator: Progress)
}