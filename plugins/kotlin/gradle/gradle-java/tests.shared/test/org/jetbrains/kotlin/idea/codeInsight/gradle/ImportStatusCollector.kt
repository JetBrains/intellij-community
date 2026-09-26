// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.MessageEvent
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent

/**
 * Collects all events from external system (such as gradle) during import
 * these events usually appears in Build windows -> Build Output tab (by default on left side next to build logs)
 */
class ImportStatusCollector : ExternalSystemTaskNotificationListener {
    private val events: MutableList<ExternalSystemTaskNotificationEvent> = mutableListOf()
    var isBuildSuccessful = true

    override fun onStatusChange(event: ExternalSystemTaskNotificationEvent) {
        events.add(event)
    }

    override fun onTaskOutput(id: ExternalSystemTaskId, text: String, processOutputType: ProcessOutputType) {
        if (text.contains("BUILD FAILED")) isBuildSuccessful = false
        super.onTaskOutput(id, text, processOutputType)
    }

    val buildEvents get() = events.filterIsInstance<ExternalSystemBuildEvent>()

    val buildErrors get() = buildEvents
        .mapNotNull { it.buildEvent as? BuildIssueEvent }
        .filter { it.kind == MessageEvent.Kind.ERROR }
}