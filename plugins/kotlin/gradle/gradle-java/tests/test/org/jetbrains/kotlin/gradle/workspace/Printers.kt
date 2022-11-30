// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.workspace

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.utils.Printer
import java.io.File

class WorkspaceModelPrinter(
    private val moduleContributors: List<ModulePrinterContributor>
) {
    fun print(project: Project, projectRoot: File): String {
        val printer = Printer(StringBuilder())
        val context = PrinterContext(printer, project, projectRoot)
        context.processModules()

        return printer.toString()
    }

    private fun PrinterContext.processModules() {
        if (moduleContributors.isEmpty()) return

        printer.println("MODULES")
        val modules = runReadAction { ModuleManager.getInstance(project).modules }.toList()

        printer.indented {
            for (module in modules.sortedBy { it.name }) {
                printer.println(module.name)
                moduleContributors.forEach { with(it) { process(module) } }
            }
        }
    }
}

data class PrinterContext(
    val printer: Printer,
    val project: Project,
    val projectRoot: File,
)

internal fun Printer.indented(block: () -> Unit) {
    pushIndent()
    block()
    popIndent()
}
