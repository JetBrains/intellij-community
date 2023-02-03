package org.jetbrains.kotlin.gradle.newTests.testFeatures

import org.jetbrains.kotlin.gradle.newTests.KotlinMppTestsContext
import org.jetbrains.kotlin.gradle.newTests.TestFeature
import org.jetbrains.kotlin.gradle.workspace.WorkspacePrintingMode
import org.junit.runner.Description

annotation class WorkspaceChecks(vararg val modes: WorkspacePrintingMode)

annotation class FullCheck

object WorkspaceModelChecksFeature : TestFeature<Unit> {
    override fun createDefaultConfiguration() = Unit

    override fun KotlinMppTestsContext.afterImport() {
        val modes = computeChecksModes(description)

        checkWorkspaceModel(modes)
    }

    // TODO: here goes Mode <-> Feature rewriting
    private fun computeChecksModes(description: Description): List<WorkspacePrintingMode> {
        val checksAnnotation = description.getAnnotation(WorkspaceChecks::class.java)
        val fullCheck = description.getAnnotation(FullCheck::class.java)

        if (checksAnnotation != null && fullCheck != null) error("@FullCheck and @WorkspaceChecks can't be used together")

        return when {
            fullCheck != null -> listOf(
                WorkspacePrintingMode.SOURCE_ROOTS,
                WorkspacePrintingMode.MODULE_DEPENDENCIES,
                WorkspacePrintingMode.MODULE_FACETS,
                WorkspacePrintingMode.TEST_TASKS
            )
            checksAnnotation != null -> checksAnnotation.modes.asList()
            else -> error(
                "@WorkspaceChecks were not set. Please, annotate the test method with @WorkspaceChecks\n" +
                        "Available modes: ${WorkspacePrintingMode.values().joinToString()}"
            )
        }
    }

    fun KotlinMppTestsContext.checkWorkspaceModel(modes: List<WorkspacePrintingMode>) {
        val agpVersionShort = testPropertiesService.agpVersion.let { if ("-" in it) it.substringBefore("-") else it }

        org.jetbrains.kotlin.gradle.workspace.checkWorkspaceModel(
          project = testProject,
          expectedTestDataDir = testDataDirectoryProvider.testDataDirectory(),
          actualTestProjectRoot = testProjectRoot,
          kotlinPluginVersion = testPropertiesService.kotlinGradlePluginVersion,
          gradleVersion = testPropertiesService.gradleVersion.version,
          checkModes = modes,
          testConfiguration = testConfiguration,
          agpClassifier = "agp$agpVersionShort"
        )
    }
}
