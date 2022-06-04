// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file: JvmName("UIEventLogger")

package com.intellij.internal.statistic.service.fus.collectors

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.eventLog.events.EventId2
import com.intellij.lang.Language

private val uiEventGroup = EventLogGroup("ui.event", 16)

@JvmField
val NavBarShowPopup: EventId = uiEventGroup.registerEvent("NavBarShowPopup")

@JvmField
val NavBarNavigate: EventId = uiEventGroup.registerEvent("NavBarNavigate")

@JvmField
val LookupShowElementActions: EventId = uiEventGroup.registerEvent("LookupShowElementActions")

@JvmField
val LookupExecuteElementAction: EventId = uiEventGroup.registerEvent("LookupExecuteElementAction")

@JvmField
val DaemonEditorPopupInvoked: EventId = uiEventGroup.registerEvent("DaemonEditorPopupInvoked")

@JvmField
val HectorPopupDisplayed: EventId = uiEventGroup.registerEvent("HectorPopupDisplayed")

@JvmField
val ProgressPaused: EventId = uiEventGroup.registerEvent("ProgressPaused")

@JvmField
val ProgressResumed: EventId = uiEventGroup.registerEvent("ProgressResumed")

@JvmField
val BreadcrumbShowTooltip: EventId1<Language?> = uiEventGroup.registerEvent(
  "BreadcrumbShowTooltip",
  EventFields.Language,
)

@JvmField
val BreadcrumbNavigate: EventId2<Language?, Boolean> = uiEventGroup.registerEvent(
  "BreadcrumbNavigate",
  EventFields.Language,
  EventFields.Boolean("with_selection"),
)

@JvmField
val DumbModeBalloonWasNotNeeded: EventId = uiEventGroup.registerEvent("DumbModeBalloonWasNotNeeded")

@JvmField
val DumbModeBalloonRequested: EventId = uiEventGroup.registerEvent("DumbModeBalloonRequested")

@JvmField
val DumbModeBalloonShown: EventId = uiEventGroup.registerEvent("DumbModeBalloonShown")

@JvmField
val DumbModeBalloonCancelled: EventId = uiEventGroup.registerEvent("DumbModeBalloonCancelled")

@JvmField
val DumbModeBalloonProceededToActions: EventId1<Long> = uiEventGroup.registerEvent(
  "DumbModeBalloonProceededToActions",
  EventFields.Long("duration_ms"),
)

@JvmField
val IncrementalSearchActivated: EventId1<Class<*>> = uiEventGroup.registerEvent(
  "IncrementalSearchActivated",
  EventFields.Class("class"),
)

@JvmField
val IncrementalSearchKeyTyped: EventId1<Class<*>> = uiEventGroup.registerEvent(
  "IncrementalSearchKeyTyped",
  EventFields.Class("class"),
)

@JvmField
val IncrementalSearchCancelled: EventId1<Class<*>> = uiEventGroup.registerEvent(
  "IncrementalSearchCancelled",
  EventFields.Class("class"),
)

@JvmField
val IncrementalSearchNextPrevItemSelected: EventId1<Class<*>> = uiEventGroup.registerEvent(
  "IncrementalSearchNextPrevItemSelected",
  EventFields.Class("class"),
)

@JvmField
val ShowUsagesPopupShowSettings: EventId = uiEventGroup.registerEvent("ShowUsagesPopupShowSettings")

@JvmField
val ToolWindowsWidgetPopupShown: EventId = uiEventGroup.registerEvent("ToolWindowsWidgetPopupShown")

@JvmField
val ToolWindowsWidgetPopupClicked: EventId = uiEventGroup.registerEvent("ToolWindowsWidgetPopupClicked")

@JvmField
val ImplementationViewComboBoxSelected: EventId = uiEventGroup.registerEvent("ImplementationViewComboBoxSelected")

@JvmField
val ImplementationViewToolWindowOpened: EventId = uiEventGroup.registerEvent("ImplementationViewToolWindowOpened")

@JvmField
val EditorFoldingIconClicked: EventId2<Boolean, Boolean> = uiEventGroup.registerEvent(
  "EditorFoldingIconClicked",
  EventFields.Boolean("expand"),
  EventFields.Boolean("recursive"),
)

@JvmField
val QuickNavigateInfoPopupShown: EventId1<Language?> = uiEventGroup.registerEvent(
  "QuickNavigateInfoPopupShown",
  EventFields.Language,
)

@JvmField
val CtrlMouseHintShown: EventId1<Class<*>?> = uiEventGroup.registerEvent(
  "CtrlMouseHintShown",
  EventFields.Class("target_class"),
)

@JvmField
val EditorAnnotationClicked: EventId1<Class<*>> = uiEventGroup.registerEvent(
  "EditorAnnotationClicked",
  EventFields.Class("class"),
)

@JvmField
val StatusBarWidgetClicked: EventId1<Class<*>> = uiEventGroup.registerEvent(
  "StatusBarWidgetClicked",
  EventFields.Class("class"),
)

@JvmField
val StatusBarPopupShown: EventId1<Class<*>> = uiEventGroup.registerEvent(
  "StatusBarPopupShown",
  EventFields.Class("class"),
)

internal class UIEventLoggerC : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = uiEventGroup
}
