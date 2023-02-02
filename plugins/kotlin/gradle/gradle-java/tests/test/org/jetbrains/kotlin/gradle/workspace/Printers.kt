// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.workspace

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.gradle.newTests.TestConfiguration
import org.jetbrains.kotlin.gradle.newTests.testFeatures.FilterModulesTestFeature
import org.jetbrains.kotlin.idea.base.facet.isTestModule
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.utils.Printer
import java.io.File

class WorkspaceModelPrinter(
    private val moduleContributors: List<ModulePrinterContributor>
) {
    fun print(
        project: Project,
        projectRoot: File,
        testConfiguration: TestConfiguration,
        kotlinGradlePluginVersion: KotlinToolingVersion
    ): String {
        val printer = Printer(StringBuilder())
        val context = PrinterContext(printer, project, projectRoot, testConfiguration, kotlinGradlePluginVersion)
        context.processModules()

        printer.printTestConfiguration(testConfiguration)

        return printer.toString()
    }

    private fun Printer.printTestConfiguration(
        testConfiguration: TestConfiguration
    ) {
        val testConfigurationDescription = testConfiguration.renderHumanReadableFeaturesConfigurations()
        if (testConfigurationDescription.isNotBlank()) {
            println()
            println("Test configuration:")
            println(testConfigurationDescription)
        }
    }

    private fun PrinterContext.processModules() {
        if (moduleContributors.isEmpty()) return

        printer.println("MODULES")
        val modules = runReadAction { ModuleManager.getInstance(project).modules }.toList()
        val modulesFiltered = filterModules(modules)

        for (module in modulesFiltered.sortedBy { it.name }) {
            printer.println(module.name)
            moduleContributors.forEach { with(it) { process(module) } }
            printer.println()
        }
    }

    private fun PrinterContext.filterModules(modules: List<Module>): List<Module> {
        val config = testConfiguration.getConfiguration(FilterModulesTestFeature)

        fun Module.shouldRemoveModule(): Boolean {
            return config.includedModuleNames != null && !config.includedModuleNames!!.matches(name) ||
                    config.excludedModuleNames != null && config.excludedModuleNames!!.matches(name) ||
                    config.hideTestModules && isTestModule ||
                    config.hideProductionModules && !isTestModule
        }

        return modules.filterNot { it.shouldRemoveModule() }
    }
}

data class PrinterContext(
    val printer: Printer,
    val project: Project,
    val projectRoot: File,
    val testConfiguration: TestConfiguration,
    val kotlinGradlePluginVersion: KotlinToolingVersion,
)

internal fun Printer.indented(block: () -> Unit) {
    pushIndent()
    block()
    popIndent()
}
