// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.workspace

abstract class WorkspaceModelReportNode {
    val commentLines: List<String> = mutableListOf()

    fun addComments(comments: List<String>) {
        require(commentLines.isEmpty()) { "Comments already exist" }
        (commentLines as MutableList<String>) += comments
    }
}

class ProjectReport(
    val moduleReports: List<ModuleReport>,
    val testConfigurationDescription: List<String>
) : WorkspaceModelReportNode()

class ModuleReport(val moduleName: String, val reportData: List<ModuleReportData>) : WorkspaceModelReportNode()

/**
 * [presentation] is required to be a single line of text (line breaks are not allowed).
 * Such requirement is imposed because it simplifies parsing greatly
 */
class ModuleReportData(val presentation: String) : WorkspaceModelReportNode() {
    init {
        require(presentation.lines().size <= 1) {
            "Presentation of ModuleReportData isn't allowed to be multi-line. " +
                    "Please return multiple ModuleReportData if you have multiple lines of report"
        }
    }
}
