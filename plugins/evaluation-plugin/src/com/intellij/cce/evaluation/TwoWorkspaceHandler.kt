// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluation

import com.intellij.cce.util.Progress
import com.intellij.cce.workspace.EvaluationWorkspace

interface TwoWorkspaceHandler {
  fun invoke(workspace1: EvaluationWorkspace, workspace2: EvaluationWorkspace, indicator: Progress)
}