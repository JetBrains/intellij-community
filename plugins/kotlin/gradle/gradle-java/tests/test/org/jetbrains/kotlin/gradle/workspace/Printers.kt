// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.workspace

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.utils.Printer

class WorkspaceModelPrinter(
    private val moduleContributors: List<ModulePrinterContributor>
) {
    private val printer = Printer(StringBuilder())

    fun print(project: Project): String {
        processModules(project)

        return printer.toString()
    }

    private fun processModules(project: Project) {
        if (moduleContributors.isEmpty()) return

        printer.println("MODULES")
        val modules = runReadAction { ModuleManager.getInstance(project).modules }.toList()

        printer.indented {
            for (module in modules.sortedBy { it.name }) {
                printer.println(module.name)
                moduleContributors.forEach { it.process(module, printer) }
            }
        }
    }
}

internal fun Printer.indented(block: () -> Unit) {
    pushIndent()
    block()
    popIndent()
}
