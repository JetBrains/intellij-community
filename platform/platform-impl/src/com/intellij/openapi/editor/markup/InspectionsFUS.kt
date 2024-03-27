// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.markup

import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionRuleValidator
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

object InspectionsFUS : CounterUsagesCollector() {
  enum class Navigation {
    NEXT,
    PREVIOUS
  }

  enum class InspectionsEvent {
    TOGGLE_PROBLEMS_VIEW,
    SHOW_POPUP
  }

  private val eventLogGroup: EventLogGroup = EventLogGroup("new.inspections.widget", 1)

  private val actionIdField = EventFields.StringValidatedByCustomRule("action_id", ActionRuleValidator::class.java)
  private val startAction = eventLogGroup.registerEvent("start_action", EventFields.Int("tabId"), actionIdField)

  private val infoState = eventLogGroup.registerEvent("inspections_info_state", EventFields.Int("tabId"), EventFields.Enum(("type"), InspectionsState::class.java))
  private val hints = eventLogGroup.registerEvent("is.hints.enabled", EventFields.Int("tabId"), EventFields.Enabled)
  private val currentFileLevelChanged = eventLogGroup.registerEvent("inspections_current_file_level_changed", EventFields.Int("tabId"), EventFields.Enum(("level"), InspectionsLevel::class.java))

  private val event = eventLogGroup.registerEvent("inspections_event", EventFields.Int("tabId"), EventFields.Enum(("event"), InspectionsEvent::class.java))


  fun infoStateDetected(project: Project?, id: Int, state: InspectionsState) {
    infoState.log(project, id, state)
  }

  fun setInlayHintsEnabled(project: Project?, id: Int, enabled: Boolean) {
    hints.log(project, id, enabled)
  }

  fun signal(project: Project?, id: Int, value: InspectionsEvent) {
    event.log(project, id, value)
  }

  fun performAction(project: Project?, id: Int, actionId: String) {
    startAction.log(project, id, actionId)
  }

  fun currentFileLevelChanged(project: Project?, id: Int, level: InspectionsLevel) {
    currentFileLevelChanged.log(project, id, level)
  }

  override fun getGroup(): EventLogGroup {
    return eventLogGroup
  }
}