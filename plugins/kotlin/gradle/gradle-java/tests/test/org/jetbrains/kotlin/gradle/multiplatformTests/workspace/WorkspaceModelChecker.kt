// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.multiplatformTests.workspace

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.gradle.multiplatformTests.AbstractTestChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.KotlinMppTestsContext
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfiguration
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.workspace.GeneralWorkspaceChecks
import org.jetbrains.kotlin.idea.base.facet.isTestModule
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import java.io.File

/**
 * A base class for [AbstractTestChecker]s that want to check the project' workspace model
 * and run assertions based on expected-testdata
 *
 * [check] is the entry point for external clients
 *
 * [classifier], [buildReportDataForModule] and [renderTestConfigurationDescription] are the abstract methods
 * to implement for inheritors
 *
 * [respectOrder] == true will tell infrastructure to respect order of [buildModulesReport] entries.
 * Note that in such case, a specific implementation should take care of making this order stable enough so
 * that tests do not fail with flaky reorderings of individual reportData's.
 * If [respectOrder] == false, then test infrastructure will sort data by [ModuleReportData.presentation]
 */
abstract class WorkspaceModelChecker<V : Any>(private val respectOrder: Boolean) : AbstractTestChecker<V>() {
    /**
     * Name of the file, without extension, with the expected testdata
     *
     * Example: `classifier = "myChecks` will look for `myChecks.txt` in the testdata
     * folder
     */
    abstract val classifier: String

    /**
     * Invoked for each [Module] in the project. Checker is expected to return [ModuleReportData],
     * representing test data for a given [Module], one per line of final rendered testdata
     */
    abstract fun PrinterContext.buildReportDataForModule(module: Module): List<ModuleReportData>

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

    final override fun KotlinMppTestsContext.check() {
        check(
            testProject,
            testDataDirectory,
            testProjectRoot,
            kgpVersion,
            gradleVersion.version,
            testConfiguration,
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
        agpClassifier: String?,
    ) {
        val expectedTestDataFile = findMostSpecificExistingFileOrNewDefault(
            classifier,
            expectedTestDataDir,
            kotlinPluginVersion,
            gradleVersion,
            agpClassifier,
            testConfiguration
        )

        val actualProjectReport = buildProjectReport(project, actualTestProjectRoot, testConfiguration, kotlinPluginVersion)

        val expectedProjectReport = if (expectedTestDataFile.exists())
            WorkspaceModelTestReportParser.parse(expectedTestDataFile.readText()) else null

        val correctedReport = if (expectedProjectReport != null)
            actualProjectReport.applyCommentsFrom(expectedProjectReport) else actualProjectReport

        val actualWorkspaceModelText = correctedReport.render(respectOrder)

        // NB: KotlinTestUtils handle non-existent expectedFile fine
        KotlinTestUtils.assertEqualsToFile(
            expectedTestDataFile,
            actualWorkspaceModelText
        ) { sanitizeExpectedFile(it) }
    }

    private fun buildProjectReport(
        project: Project,
        projectRoot: File,
        testConfiguration: TestConfiguration,
        kotlinGradlePluginVersion: KotlinToolingVersion
    ): ProjectReport {
        val context = PrinterContext(project, projectRoot, testConfiguration, kotlinGradlePluginVersion)

        val moduleReports = context.buildModulesReport()
        return ProjectReport(
            moduleReports,
            renderModuleFilteringConfiguration(testConfiguration) + renderTestConfigurationDescription(testConfiguration)
        )
    }

    private fun PrinterContext.buildModulesReport(): List<ModuleReport> {
        val modules = runReadAction { ModuleManager.getInstance(project).modules }.toList()
        val modulesFiltered = filterModules(modules)

        return modulesFiltered.map {
            val reportData = buildReportDataForModule(it)
            ModuleReport(it.name, reportData)
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

    private fun renderModuleFilteringConfiguration(testConfiguration: TestConfiguration): List<String> {
        val configuration = testConfiguration.getConfiguration(GeneralWorkspaceChecks)

        val hidden = buildList {
            if (configuration.hideTestModules) add("test source modules")
            if (configuration.hideProductionModules) add("production source modules")

            if (configuration.excludedModuleNames != null) add("source modules matching ${configuration.excludedModuleNames!!}")
        }

        val result = mutableListOf<String>()

        if (hidden.isNotEmpty()) result += "hiding ${hidden.joinToString()}"
        if (configuration.includedModuleNames != null)
            result += "showing only modules matching ${configuration.includedModuleNames!!}"

        return result
    }
}
