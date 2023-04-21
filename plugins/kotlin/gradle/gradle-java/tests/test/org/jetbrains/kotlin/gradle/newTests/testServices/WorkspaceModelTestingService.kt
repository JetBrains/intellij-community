// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testServices

import org.jetbrains.kotlin.gradle.workspace.WorkspacePrintingMode
import org.jetbrains.kotlin.gradle.newTests.AbstractKotlinMppGradleImportingTest
import org.jetbrains.kotlin.gradle.newTests.TestConfiguration
import org.jetbrains.kotlin.gradle.workspace.WorkspacePrintingMode.*
import org.jetbrains.kotlin.gradle.workspace.checkWorkspaceModel
import org.junit.runner.Description

annotation class WorkspaceChecks(vararg val modes: WorkspacePrintingMode)

annotation class FullCheck

class WorkspaceModelTestingService : KotlinBeforeAfterTestRuleWithDescription {
    private var currentModes: List<WorkspacePrintingMode>? = null

    override fun before(description: Description) {
        val checksAnnotation = description.getAnnotation(WorkspaceChecks::class.java)
        val fullCheck = description.getAnnotation(FullCheck::class.java)

        if (checksAnnotation != null && fullCheck != null) error("@FullCheck and @WorkspaceChecks can't be used together")

        currentModes = when {
            fullCheck != null -> listOf(SOURCE_ROOTS, MODULE_DEPENDENCIES, MODULE_FACETS, TEST_TASKS)
            checksAnnotation != null -> checksAnnotation.modes.asList()
            else -> return
        }
    }

    fun checkWorkspaceModel(configuration: TestConfiguration, testInstance: AbstractKotlinMppGradleImportingTest) {
        val project = testInstance.importedProject
        val expectedTestDataDir = testInstance.testDataDirectoryService.testDataDirectory()
        val actualTestProjectDir = testInstance.importedProjectRoot.toNioPath().toFile()
        val kotlinGradlePluginVersion = testInstance.kotlinTestPropertiesService.kotlinGradlePluginVersion
        val gradleVersion = testInstance.kotlinTestPropertiesService.gradleVersion.version
        val modes = requireNotNull(currentModes) {
            "@WorkspaceChecks were not set. Please, annotate the test method with @WorkspaceChecks\n" +
                    "Available modes: ${WorkspacePrintingMode.values().joinToString()}"
        }

        checkWorkspaceModel(
            project,
            expectedTestDataDir,
            actualTestProjectDir,
            kotlinGradlePluginVersion,
            gradleVersion,
            modes,
            configuration
        )
    }
}
