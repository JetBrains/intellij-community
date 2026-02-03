// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfiguration
import org.jetbrains.kotlin.gradle.multiplatformTests.workspace.ModuleReportData
import org.jetbrains.kotlin.gradle.multiplatformTests.workspace.PrinterContext
import org.jetbrains.kotlin.gradle.multiplatformTests.workspace.WorkspaceModelChecker
import org.jetbrains.kotlin.idea.base.facet.externalSystemTestRunTasks

object TestTasksChecker : WorkspaceModelChecker<Unit>(respectOrder = false) {
    override fun createDefaultConfiguration() = Unit

    override val classifier: String = "test-tasks"

    override fun renderTestConfigurationDescription(testConfiguration: TestConfiguration): List<String> = emptyList()

    override fun PrinterContext.buildReportDataForModule(module: Module): List<ModuleReportData> {
        val tasks = runReadAction { module.externalSystemTestRunTasks() }
        if (tasks.isEmpty()) return emptyList()

        return tasks.map { ModuleReportData(it.toStringRepresentation()) }
    }
}
