// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtFile

object KotlinFailureCollector : CounterUsagesCollector() {
    override fun getGroup(): EventLogGroup = group

    private val group = EventLogGroup("kotlin.failures", 1)

    private val highlightingFailureEvent: EventId = group.registerEvent("Highlighting")
    private val descriptorNotFoundEvent: EventId = group.registerEvent("DescriptorNotFound")
    private val indexInconsistencyEvent = group.registerEvent("IndexInconsistency")

    fun recordHighlightingFailure(ktFile: KtFile) {
        if (!ktFile.isWritable) return
        recordFailure(highlightingFailureEvent, ktFile.project)
    }

    fun recordDescriptorNotFoundEvent(project: Project) {
        recordFailure(descriptorNotFoundEvent, project)
    }

    fun recordIndexInconsistency(project: Project) {
        recordFailure(indexInconsistencyEvent, project)
    }

    private fun recordFailure(event: EventId, project: Project) {
        if (ApplicationManager.getApplication().run { isUnitTestMode || isHeadlessEnvironment }) return

        event.log(project)
    }
}