// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.service.fus.collectors

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields


class UIEventLogger : CounterUsagesCollector() {
  companion object {
    private val group = EventLogGroup("ui.event", 15)

    @JvmField val NavBarShowPopup = group.registerEvent("NavBarShowPopup")
    @JvmField val NavBarNavigate = group.registerEvent("NavBarNavigate")
    @JvmField val LookupShowElementActions = group.registerEvent("LookupShowElementActions")
    @JvmField val LookupExecuteElementAction = group.registerEvent("LookupExecuteElementAction")
    @JvmField val DaemonEditorPopupInvoked = group.registerEvent("DaemonEditorPopupInvoked")
    @JvmField val HectorPopupDisplayed = group.registerEvent("HectorPopupDisplayed")
    @JvmField val ProgressPaused = group.registerEvent("ProgressPaused")
    @JvmField val ProgressResumed = group.registerEvent("ProgressResumed")
    @JvmField val BreadcrumbShowTooltip = group.registerEvent("BreadcrumbShowTooltip", EventFields.Language)
    @JvmField val BreadcrumbNavigate = group.registerEvent("BreadcrumbNavigate", EventFields.Language, EventFields.Boolean("with_selection"))
    @JvmField val DumbModeBalloonWasNotNeeded = group.registerEvent("DumbModeBalloonWasNotNeeded")
    @JvmField val DumbModeBalloonRequested = group.registerEvent("DumbModeBalloonRequested")
    @JvmField val DumbModeBalloonShown = group.registerEvent("DumbModeBalloonShown")
    @JvmField val DumbModeBalloonCancelled = group.registerEvent("DumbModeBalloonCancelled")
    @JvmField val DumbModeBalloonProceededToActions = group.registerEvent("DumbModeBalloonProceededToActions", EventFields.Long("duration_ms"))
    @JvmField val IncrementalSearchActivated = group.registerEvent("IncrementalSearchActivated", EventFields.Class("class"))
    @JvmField val IncrementalSearchKeyTyped = group.registerEvent("IncrementalSearchKeyTyped", EventFields.Class("class"))
    @JvmField val IncrementalSearchCancelled = group.registerEvent("IncrementalSearchCancelled", EventFields.Class("class"))
    @JvmField val IncrementalSearchNextPrevItemSelected = group.registerEvent("IncrementalSearchNextPrevItemSelected", EventFields.Class("class"))
    @JvmField val ShowUsagesPopupShowSettings = group.registerEvent("ShowUsagesPopupShowSettings")
    @JvmField val ToolWindowsWidgetPopupShown = group.registerEvent("ToolWindowsWidgetPopupShown")
    @JvmField val ToolWindowsWidgetPopupClicked = group.registerEvent("ToolWindowsWidgetPopupClicked")
    @JvmField val ImplementationViewComboBoxSelected = group.registerEvent("ImplementationViewComboBoxSelected")
    @JvmField val ImplementationViewToolWindowOpened = group.registerEvent("ImplementationViewToolWindowOpened")
    @JvmField val EditorFoldingIconClicked = group.registerEvent("EditorFoldingIconClicked", EventFields.Boolean("expand"), EventFields.Boolean("recursive"))
    @JvmField val QuickNavigateInfoPopupShown = group.registerEvent("QuickNavigateInfoPopupShown", EventFields.Language)
    @JvmField val EditorAnnotationClicked = group.registerEvent("EditorAnnotationClicked", EventFields.Class("class"))
    @JvmField val StatusBarWidgetClicked = group.registerEvent("StatusBarWidgetClicked", EventFields.Class("class"))
    @JvmField val StatusBarPopupShown = group.registerEvent("StatusBarPopupShown", EventFields.Class("class"))
  }

  override fun getGroup(): EventLogGroup = Companion.group
}
