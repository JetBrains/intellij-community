// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.workspace.info

import com.intellij.cce.metric.MetricInfo

data class FileEvaluationInfo(val sessionsInfo: FileSessionsInfo, val metrics: List<MetricInfo>, val evaluationType: String)