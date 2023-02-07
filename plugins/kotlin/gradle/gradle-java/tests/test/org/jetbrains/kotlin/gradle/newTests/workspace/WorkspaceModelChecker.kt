// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.newTests.workspace

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.gradle.newTests.AbstractTestChecker
import org.jetbrains.kotlin.gradle.newTests.KotlinMppTestsContext
import org.jetbrains.kotlin.gradle.newTests.TestConfiguration
import org.jetbrains.kotlin.gradle.newTests.testFeatures.checkers.workspace.GeneralWorkspaceChecks
import org.jetbrains.kotlin.idea.base.facet.isTestModule
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.utils.Printer
import java.io.File

abstract class WorkspaceModelChecker<V : Any> : AbstractTestChecker<V>() {
    abstract val classifier: String

    abstract fun PrinterContext.process(module: Module)

    /**
     * Renders human-readable description of test feature' configuration. It will be
     * rendered in the testdata
     *
     * General guidelines:
     * - return one or more strings. Each string will be rendered as a separate line, so it's
     *   a good idea go group related information together, and separate less related ones
     * - try to keep it short and informative, akin to commit message titles: 'hide stdlib', 'show order entries scopes'
     */
    abstract fun renderTestConfigurationDescription(testConfiguration: TestConfiguration): List<String>

    final override fun KotlinMppTestsContext.check(additionalTestClassifier: String?) {
        check(
            testProject,
            testDataDirectory,
            testProjectRoot,
            kgpVersion,
            gradleVersion.version,
            testConfiguration,
            additionalTestClassifier,
            agpVersion
        )
    }

    fun check(
        project: Project,
        expectedTestDataDir: File,
        actualTestProjectRoot: File, // root of [project]
        kotlinPluginVersion: KotlinToolingVersion,
        gradleVersion: String,
        testConfiguration: TestConfiguration,
        testClassifier: String?,
        agpClassifier: String?,
    ) {
        val kotlinClassifier = with(kotlinPluginVersion) { "$major.$minor.$patch" }
        val expectedTestDataFile = findMostSpecificExistingFileOrNewDefault(
            classifier,
            expectedTestDataDir,
            kotlinClassifier,
            gradleVersion,
            agpClassifier,
            testClassifier
        )

        val actualWorkspaceModelText = printModel(project, actualTestProjectRoot, testConfiguration, kotlinPluginVersion)

        // NB: KotlinTestUtils handle non-existent expectedFile fine
        KotlinTestUtils.assertEqualsToFile(
            expectedTestDataFile,
            actualWorkspaceModelText
        ) { sanitizeExpectedFile(it) }
    }

    private fun printModel(
        project: Project,
        projectRoot: File,
        testConfiguration: TestConfiguration,
        kotlinGradlePluginVersion: KotlinToolingVersion
    ): String {
        val printer = Printer(StringBuilder())
        val context = PrinterContext(printer, project, projectRoot, testConfiguration, kotlinGradlePluginVersion)

        context.printModules()

        context.printTestConfiguration()

        return printer.toString()
    }

    private fun PrinterContext.printTestConfiguration() {
        val testConfigurationDescription = renderTestConfigurationDescription(testConfiguration)
        if (testConfigurationDescription.isNotEmpty()) {
            printer.println()
            printer.println("Test configuration:")
            renderModuleFilteringConfiguration()
            printer.println(testConfigurationDescription.joinToString(separator = "\n") { "- $it" })
        }
    }

    private fun PrinterContext.printModules() {
        printer.println("MODULES")
        val modules = runReadAction { ModuleManager.getInstance(project).modules }.toList()
        val modulesFiltered = filterModules(modules)

        for (module in modulesFiltered.sortedBy { it.name }) {
            printer.println(module.name)
            process(module)
            printer.println()
        }
    }

    private fun PrinterContext.filterModules(modules: List<Module>): List<Module> {
        val config = testConfiguration.getConfiguration(GeneralWorkspaceChecks)

        fun Module.shouldRemoveModule(): Boolean {
            return config.includedModuleNames != null && !config.includedModuleNames!!.matches(name) ||
                    config.excludedModuleNames != null && config.excludedModuleNames!!.matches(name) ||
                    config.hideTestModules && isTestModule ||
                    config.hideProductionModules && !isTestModule
        }

        return modules.filterNot { it.shouldRemoveModule() }
    }

    private fun PrinterContext.renderModuleFilteringConfiguration() {
        val configuration = testConfiguration.getConfiguration(GeneralWorkspaceChecks)

        val hidden = buildList {
            if (configuration.hideTestModules) add("test source modules")
            if (configuration.hideProductionModules) add("production source modules")

            if (configuration.excludedModuleNames != null) add("source modules matching ${configuration.excludedModuleNames!!}")
        }

        if (hidden.isNotEmpty()) printer.println("- hiding ${hidden.joinToString()}")
        if (configuration.includedModuleNames != null)
            printer.println("- showing only modules matching ${configuration.includedModuleNames!!}")
    }
}
