// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.report

import com.intellij.cce.workspace.info.FileEvaluationInfo

interface ReportGenerator {
  val type: String
  fun generateFileReport(sessions: List<FileEvaluationInfo>)
}
