// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("UIEventLogger")
package com.intellij.internal.statistic.service.fus.collectors

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
  ProgressResumed
}

fun logUIEvent(eventId: UIEventId) {
  FUCounterUsageLogger.getInstance().logEvent("ui.event", eventId.name)
}
