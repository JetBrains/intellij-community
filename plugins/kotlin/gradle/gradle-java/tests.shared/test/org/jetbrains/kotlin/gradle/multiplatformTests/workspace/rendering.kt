// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.workspace

import org.jetbrains.kotlin.utils.Printer

fun ProjectReport.render(respectOrder: Boolean, printer: Printer = Printer(StringBuilder())): String {
    renderCommentsIfAny(printer)
    printer.println(WorkspaceModelTestReportParser.PROJECT_REPORT_START)

    for (moduleReport in moduleReports.sortedBy { it.moduleName }) {
        moduleReport.render(respectOrder, printer)
        printer.println()
    }

    if (testConfigurationDescription.isNotEmpty()) {
        printer.println()
        printer.println(WorkspaceModelTestReportParser.TEST_CONFIGURATION_DESCRIPTION_START)
        printer.println(testConfigurationDescription.joinToString(separator = "\n") { "- $it" })
    }

    return printer.toString()
}

fun ModuleReport.render(respectOrder: Boolean, printer: Printer = Printer(StringBuilder())) {
    renderCommentsIfAny(printer)
    printer.println(moduleName)
    val dataSortedIfNecessary = if (respectOrder) reportData else reportData.sortedBy { it.presentation }
    printer.indented {
        dataSortedIfNecessary.forEach { it.render(printer) }
    }
}

private fun ModuleReportData.render(printer: Printer) {
    renderCommentsIfAny(printer)
    printer.println(presentation)
}

private fun WorkspaceModelReportNode.renderCommentsIfAny(printer: Printer) {
    commentLines.forEach { printer.println(it) }
}
