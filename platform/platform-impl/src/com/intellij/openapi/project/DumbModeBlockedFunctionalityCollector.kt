// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionRuleValidator
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventFields.StringValidatedByCustomRule
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbModeBlockedFunctionalityCollector.logActionBlocked

/**
 * Reports cases when some functionality is reported unavailable for user due to dumb mode.
 *
 * Borderline case when not DumbAware actions are called with hotkey during indexing
 * (and could be executed after it finished, see IDEA-227118), are collected (see [logActionBlocked]).
 *
 * Another borderline case is when functionality is run in a limited way (completion),
 * or explicitly asks the user if it should proceed in a limited way or cancel (copying, moving, see IDEA-192489, IDEA-240078, IDEA-240078).
 * This case is not handled by this collector.
 */
object DumbModeBlockedFunctionalityCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("dumb.mode.blocked.functionality", 1)

  private val ACTION_ID = StringValidatedByCustomRule("action_id", ActionRuleValidator::class.java)
  private val FUNCTIONALITY_SOURCE = EventFields.Enum("functionality", DumbModeBlockedFunctionality::class.java)
  private val EXECUTED_WHEN_SMART = EventFields.Boolean("executed_when_smart")
  private val FUNCTIONALITY_BLOCKED = GROUP.registerVarargEvent("functionality.blocked",
                                                                ACTION_ID,
                                                                FUNCTIONALITY_SOURCE,
                                                                EXECUTED_WHEN_SMART)

  fun logActionBlocked(project: Project, actionId: String) {
    FUNCTIONALITY_BLOCKED.log(project,
                              ACTION_ID.with(actionId),
                              FUNCTIONALITY_SOURCE.with(DumbModeBlockedFunctionality.Action))
  }

  fun logActionsBlocked(project: Project, actionIds: List<String>, executedAfterBlock: Boolean) {
    if (actionIds.size == 1) {
      logActionBlocked(project, actionIds[0])
    }
    else {
      FUNCTIONALITY_BLOCKED.log(project,
                                FUNCTIONALITY_SOURCE.with(DumbModeBlockedFunctionality.MultipleActionIds),
                                EXECUTED_WHEN_SMART.with(executedAfterBlock))
    }
  }

  fun logFunctionalityBlocked(project: Project, functionality: DumbModeBlockedFunctionality) {
    if (functionality == DumbModeBlockedFunctionality.Action) {
      thisLogger().error("Use DumbModeBlockingFunctionalityCollector.Companion.logActionBlocked instead")
    }
    FUNCTIONALITY_BLOCKED.log(project,
                              FUNCTIONALITY_SOURCE.with(functionality))
  }

  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}