// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface StatisticsServiceScope {
  companion object {
    fun getScope(project: Project): CoroutineScope = project.service<StatisticsServiceProjectScope>().scope
    fun getScope(): CoroutineScope = service<StatisticsServiceApplicationScope>().scope
  }
}

@ApiStatus.Internal
@Service(Service.Level.APP)
class StatisticsServiceApplicationScope(val scope: CoroutineScope)

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class StatisticsServiceProjectScope(val scope: CoroutineScope)