// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("UIEventLogger")
package com.intellij.internal.statistic.service.fus.collectors

import com.intellij.internal.statistic.eventLog.FeatureUsageData

/**
 * @author yole
 */
enum class UIEventId {
  NavBarShowPopup,
  NavBarNavigate,
  LookupShowElementActions,
  LookupExecuteElementAction,
  DaemonEditorPopupInvoked,
  HectorPopupDisplayed,
  ProgressPaused,
  ProgressResumed,
  BreadcrumbShowTooltip,
  BreadcrumbNavigate,
  DumbModeBalloonWasNotNeeded,
  DumbModeBalloonRequested,
  DumbModeBalloonShown,
  DumbModeBalloonCancelled,
  DumbModeBalloonProceededToActions,
  IncrementalSearchActivated,
  IncrementalSearchKeyTyped,
  IncrementalSearchCancelled,
  IncrementalSearchNextPrevItemSelected,
  ShowUsagesPopupShowSettings
}

fun logUIEvent(eventId: UIEventId) {
  FUCounterUsageLogger.getInstance().logEvent("ui.event", eventId.name)
}

fun logUIEvent(eventId: UIEventId, data: FeatureUsageData) {
  FUCounterUsageLogger.getInstance().logEvent("ui.event", eventId.name, data)
}
