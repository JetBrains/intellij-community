// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.BooleanEventField
import com.intellij.internal.statistic.eventLog.events.EventId
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtFile

object KotlinFailureCollector : CounterUsagesCollector() {
    override fun getGroup(): EventLogGroup = group

    private val group = EventLogGroup("kotlin.failures", 2)

    private val generalFrontEndFailureEvent: EventId1<Boolean> = group.registerEvent("GeneralFrontEndFailure", BooleanEventField("script"))
    private val highlightingFailureEvent: EventId1<Boolean> = group.registerEvent("Highlighting", BooleanEventField("script"))
    private val descriptorNotFoundEvent: EventId = group.registerEvent("DescriptorNotFound")
    private val indexInconsistencyEvent: EventId = group.registerEvent("IndexInconsistency")

    fun recordGeneralFrontEndFailureEvent(ktFile: KtFile) {
        ktFile.record { generalFrontEndFailureEvent.log(ktFile.project, ktFile.isScriptFast()) }
    }

    fun recordHighlightingFailure(ktFile: KtFile) {
        ktFile.record { highlightingFailureEvent.log(ktFile.project, ktFile.isScriptFast()) }
    }

    fun recordDescriptorNotFoundEvent(project: Project) {
        project.record { descriptorNotFoundEvent.log() }
    }

    fun recordIndexInconsistency(project: Project) {
        project.record { indexInconsistencyEvent.log() }
    }

    private fun KtFile.record(action: () -> Unit) {
        if (!isWritable) return
        project.record(action)
    }

    private fun Project.record(action: () -> Unit) {
        if (ApplicationManager.getApplication().run { isUnitTestMode || isHeadlessEnvironment }) return

        action()
    }

    private fun KtFile.isScriptFast(): Boolean = virtualFile?.nameSequence?.endsWith("kts") == true
}