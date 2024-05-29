// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import org.jetbrains.kotlin.gradle.multiplatformTests.AbstractTestChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.KotlinMppTestsContext
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfiguration
import org.jetbrains.kotlin.gradle.multiplatformTests.workspace.findMostSpecificExistingFileOrNewDefault
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import java.io.File
import kotlin.collections.flatMap
import kotlin.test.assertEquals

object AggregatedExternalLibrariesChecker : AbstractTestChecker<AggregatedExternalLibrariesConfiguration>() {

    override fun createDefaultConfiguration(): AggregatedExternalLibrariesConfiguration = AggregatedExternalLibrariesConfiguration()

    override fun KotlinMppTestsContext.check() {
        checkLibraryDependencies(
            testProject,
            testDataDirectory,
            kgpVersion,
            gradleVersion.version,
            testConfiguration,
            agpVersion
        )
    }

    private fun checkLibraryDependencies(
        project: Project,
        expectedTestDataDir: File,
        kotlinPluginVersion: KotlinToolingVersion,
        gradleVersion: String,
        testConfiguration: TestConfiguration,
        agpClassifier: String?,
    ) {
        val expectedFile = findMostSpecificExistingFileOrNewDefault(
            "aggregatedExternalLibraries",
            expectedTestDataDir,
            kotlinPluginVersion,
            gradleVersion,
            agpClassifier,
            testConfiguration
        )

        val expectedContent = expectedFile.takeIf { it.exists() }
            ?.readText()
            ?: error("Expected file does not exist")

        val projectLibraries = runReadAction {
            ModuleManager.getInstance(project).modules.flatMap { module ->
                ModuleRootManager.getInstance(module).orderEntries
                    .filterIsInstance<LibraryOrderEntry>()
                    .map { it.presentableName }
            }
        }

        val filteredLibraries = projectLibraries
            .filterNot { it.contains("org.jetbrains") }
            .distinct()
            .sorted()
            .joinToString("\n")

        assertEquals(expectedContent, filteredLibraries, "Library dependencies do not match the expected content!")
    }
}

class AggregatedExternalLibrariesConfiguration