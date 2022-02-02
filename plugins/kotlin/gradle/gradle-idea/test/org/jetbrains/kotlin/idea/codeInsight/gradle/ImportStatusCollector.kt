/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.MessageEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent

/**
 * Collects all events from external system (such as gradle) during import
 * these events usually appears in Build windows -> Build Output tab (by default on left side next to build logs)
 */
class ImportStatusCollector : ExternalSystemTaskNotificationListenerAdapter() {
    private val events: MutableList<ExternalSystemTaskNotificationEvent> = mutableListOf()

    override fun onStatusChange(event: ExternalSystemTaskNotificationEvent) {
        events.add(event)
    }

    val allEvents get() = events.toList()
    val buildEvents get() = events.filterIsInstance<ExternalSystemBuildEvent>()

    val buildErrors get() = buildEvents
        .mapNotNull { it.buildEvent as? BuildIssueEvent }
        .filter { it.kind == MessageEvent.Kind.ERROR }
}