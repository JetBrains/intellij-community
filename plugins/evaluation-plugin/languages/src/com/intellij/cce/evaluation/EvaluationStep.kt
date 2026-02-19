// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluation

import com.intellij.cce.workspace.EvaluationWorkspace

interface EvaluationStep {
  val name: String
  val description: String
}

interface ForegroundEvaluationStep : EvaluationStep {
  fun start(workspace: EvaluationWorkspace): EvaluationWorkspace?
}