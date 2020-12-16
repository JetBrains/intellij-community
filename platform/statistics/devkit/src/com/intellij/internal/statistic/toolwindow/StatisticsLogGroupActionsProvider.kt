// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.toolwindow

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface StatisticsLogGroupActionsProvider {
  fun getActions(groupId: String, eventId: String, eventData: String): List<AnAction>

  companion object {
    val EP_NAME = ExtensionPointName<StatisticsLogGroupActionsProvider>("com.intellij.statisticsLogGroupActionsProvider")
  }
}