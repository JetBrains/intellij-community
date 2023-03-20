// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ui

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.EventFields.Class
import com.intellij.internal.statistic.eventLog.events.PrimitiveEventField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus.Internal

private val GROUP = EventLogGroup(
  id = "editor.notification.panel",
  version = 3,
)

private val PROVIDER_CLASS_FIELD = object : PrimitiveEventField<EditorNotificationProvider>() {

  private val delegate = Class("provider_class")

  override val name: String
    get() = delegate.name

  override val validationRule: List<String>
    get() = delegate.validationRule

  override fun addData(
    fuData: FeatureUsageData,
    value: EditorNotificationProvider,
  ) {
    delegate.addData(fuData, value.javaClass)
  }
}

private val NOTIFICATION_SHOWN_EVENT = GROUP.registerEvent(
  eventId = "notificationShown",
  eventField1 = PROVIDER_CLASS_FIELD,
)

private val HANDLER_INVOKED_EVENT = GROUP.registerEvent(
  eventId = "handlerInvoked",
  eventField1 = PROVIDER_CLASS_FIELD,
  eventField2 = Class("handler_class"),
)

@Internal
internal fun logNotificationShown(
  project: Project,
  provider: EditorNotificationProvider,
) {
  NOTIFICATION_SHOWN_EVENT.log(project, provider)
}

@Internal
internal fun logHandlerInvoked(
  project: Project,
  provider: EditorNotificationProvider,
  handlerClass: Class<*>,
) {
  HANDLER_INVOKED_EVENT.log(project, provider, handlerClass)
}

@Internal
internal class EditorNotificationUsagesCollector : CounterUsagesCollector() {

  override fun getGroup() = GROUP
}