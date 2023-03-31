// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.workspace

fun ProjectReport.applyCommentsFrom(donor: ProjectReport): ProjectReport {
    val newProjectComments = donor.commentLines

    val donorModuleReportsByName = donor.moduleReports.associateBy { it.moduleName }
    val newModuleReports = moduleReports.map { recipientModule ->
        val donorModuleReport = donorModuleReportsByName[recipientModule.moduleName] ?: return@map recipientModule
        recipientModule.applyCommentsFrom(donorModuleReport)
    }

    return ProjectReport(newModuleReports, testConfigurationDescription).apply { addComments(newProjectComments) }
}

private fun ModuleReport.applyCommentsFrom(donor: ModuleReport): ModuleReport {
    val newModuleComments = donor.commentLines
    val donorDatasByPresentation = donor.reportData.associateBy { it.presentation }
    val newDatas = reportData.map { recipientData ->
        val donorData = donorDatasByPresentation[recipientData.presentation] ?: return@map recipientData
        recipientData.applyCommentsFrom(donorData)
    }
    return ModuleReport(moduleName, newDatas).apply { addComments(newModuleComments) }
}

private fun ModuleReportData.applyCommentsFrom(donor: ModuleReportData): ModuleReportData =
    ModuleReportData(presentation).apply { addComments(donor.commentLines) }
