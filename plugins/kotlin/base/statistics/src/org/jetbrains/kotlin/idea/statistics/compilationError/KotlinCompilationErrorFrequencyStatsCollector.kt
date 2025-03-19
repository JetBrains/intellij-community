// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.statistics.compilationError

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.kotlin.psi.KtFile

object KotlinCompilationErrorFrequencyStatsCollector : CounterUsagesCollector() {
    override fun getGroup(): EventLogGroup = group

    private const val CODE_IS_TOTALLY_BROKEN_NUMBER_OF_COMPILATION_ERRORS_IN_FILE_LOWER_BOUND = 21

    private val group = EventLogGroup("kotlin.compilation.error", 2)

    private val compilationErrorIdField =
        EventFields.StringValidatedByCustomRule("error_id", KotlinCompilationErrorIdValidationRule::class.java)

    private val event = group.registerEvent("error.happened", compilationErrorIdField)

    fun recordCompilationErrorsHappened(diagnosticsFactoryNames: Sequence<String>, psiFile: KtFile) {
        if (!isEnabled) return
        if (!psiFile.isWritable) return // We are interested only in compilation errors users make themselves
        val collected =
            diagnosticsFactoryNames.take(CODE_IS_TOTALLY_BROKEN_NUMBER_OF_COMPILATION_ERRORS_IN_FILE_LOWER_BOUND).toList()
        if (collected.size >= CODE_IS_TOTALLY_BROKEN_NUMBER_OF_COMPILATION_ERRORS_IN_FILE_LOWER_BOUND) return
        KotlinCompilationErrorProcessedFilesTimeStampRecorder.getInstance(psiFile.project)
            .keepOnlyIfHourPassedAndRecordTimestamps(psiFile.virtualFile, collected)
            .forEach(event::log)
    }
}

private val isEnabled: Boolean
    get() = ApplicationManager.getApplication().run {
        !isUnitTestMode && !isHeadlessEnvironment && StatisticsUploadAssistant.isCollectAllowedOrForced()
    }
