// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsEventLogGroup
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbModeBlockedFunctionalityCollector.logActionBlocked
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicReference

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
@ApiStatus.Internal
object DumbModeBlockedFunctionalityCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("dumb.mode.blocked.functionality", 8)

  private val FUNCTIONALITY_SOURCE = EventFields.Enum("functionality", DumbModeBlockedFunctionality::class.java)
  private val EXECUTED_WHEN_SMART = EventFields.Boolean("executed_when_smart")
  private val FUNCTIONALITY_BLOCKED = GROUP.registerVarargEvent("functionality.blocked",
                                                                ActionsEventLogGroup.ACTION_ID,
                                                                FUNCTIONALITY_SOURCE,
                                                                EXECUTED_WHEN_SMART)

  fun logActionBlocked(project: Project, actionId: String) {
    lastEqualityObjectReference.set(null)
    FUNCTIONALITY_BLOCKED.log(project,
                              ActionsEventLogGroup.ACTION_ID.with(actionId),
                              FUNCTIONALITY_SOURCE.with(DumbModeBlockedFunctionality.Action))
  }

  fun logActionsBlocked(project: Project, actionIds: List<String>, executedAfterBlock: Boolean) {
    lastEqualityObjectReference.set(null)
    when (actionIds.size) {
      0 -> FUNCTIONALITY_BLOCKED.log(project,
                                     FUNCTIONALITY_SOURCE.with(DumbModeBlockedFunctionality.ActionWithoutId),
                                     EXECUTED_WHEN_SMART.with(executedAfterBlock))
      1 -> FUNCTIONALITY_BLOCKED.log(project,
                                     ActionsEventLogGroup.ACTION_ID.with(actionIds[0]),
                                     FUNCTIONALITY_SOURCE.with(DumbModeBlockedFunctionality.Action),
                                     EXECUTED_WHEN_SMART.with(executedAfterBlock))
      else -> FUNCTIONALITY_BLOCKED.log(project,
                                        FUNCTIONALITY_SOURCE.with(DumbModeBlockedFunctionality.MultipleActionIds),
                                        EXECUTED_WHEN_SMART.with(executedAfterBlock))
    }
  }

  private val lastEqualityObjectReference = AtomicReference<Any?>(null)

  fun logFunctionalityBlocked(project: Project, functionality: DumbModeBlockedFunctionality) {
    doLogFunctionalityBlocked(project, functionality, null)
  }

  fun logFunctionalityBlockedWithCoalescing(project: Project, functionality: DumbModeBlockedFunctionality, equalityObject: Any) {
    doLogFunctionalityBlocked(project, functionality, equalityObject)
  }

  private fun doLogFunctionalityBlocked(project: Project, functionality: DumbModeBlockedFunctionality, equalityObject: Any? = null) {
    if (functionality == DumbModeBlockedFunctionality.Action) {
      thisLogger().error("Use DumbModeBlockingFunctionalityCollector.Companion.logActionBlocked instead")
    }
    if (equalityObject == null) {
      lastEqualityObjectReference.set(null)
    }
    else if (equalityObject == lastEqualityObjectReference.getAndSet(equalityObject)) {
      return
    }
    FUNCTIONALITY_BLOCKED.log(project,
                              FUNCTIONALITY_SOURCE.with(functionality))
  }

  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}
