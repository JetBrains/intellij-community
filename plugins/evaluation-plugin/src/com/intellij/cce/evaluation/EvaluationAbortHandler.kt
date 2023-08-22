// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluation

interface EvaluationAbortHandler {
  fun onError(error: Throwable, stage: String)
  fun onCancel(stage: String)
}