package com.intellij.cce.workspace.info

import com.intellij.cce.metric.MetricInfo

data class FileEvaluationInfo(val sessionsInfo: FileSessionsInfo, val metrics: List<MetricInfo>, val evaluationType: String)