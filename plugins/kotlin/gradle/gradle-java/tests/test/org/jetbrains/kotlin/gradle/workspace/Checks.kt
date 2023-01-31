// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.workspace

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.gradle.newTests.TestConfiguration
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import java.io.File

fun checkWorkspaceModel(
    project: Project,
    expectedTestDataDir: File,
    actualTestProjectRoot: File, // root of [project]
    kotlinPluginVersion: KotlinToolingVersion,
    gradleVersion: String,
    checkModes: List<WorkspacePrintingMode>,
    testConfiguration: TestConfiguration,
    testClassifier: String? = null,
    agpClassifier: String? = null,
) {
    val kotlinClassifier = with(kotlinPluginVersion) { "$major.$minor.$patch" }
    val filesWithExpectedTestData = findExpectedTestDataFiles(
        expectedTestDataDir,
        kotlinClassifier,
        gradleVersion,
        agpClassifier,
        checkModes,
        testClassifier
    )

    for ((expectedFile, mode) in filesWithExpectedTestData) {
        // Temporary mute TEST_TASKS checks due to issues with hosts on CI. See KT-56332
        if (mode == WorkspacePrintingMode.TEST_TASKS) continue
        val actualWorkspaceModelText = mode.printer.build().print(project, actualTestProjectRoot, testConfiguration, kotlinPluginVersion)

        // NB: KotlinTestUtils handle non-existent expectedFile fine
        KotlinTestUtils.assertEqualsToFile(
            expectedFile,
            actualWorkspaceModelText
        ) { sanitizeExpectedFile(it) }
    }
}

private fun findMostSpecificExistingFileOrNewDefault(
    testDataDir: File,
    kotlinClassifier: String,
    gradleClassifier: String,
    agpClassifier: String?,
    mode: WorkspacePrintingMode,
    testClassifier: String?
): File {
    val prioritisedClassifyingParts = sequenceOf(
        listOfNotNull(testClassifier),
        listOfNotNull(kotlinClassifier, gradleClassifier, agpClassifier),
        listOfNotNull(kotlinClassifier, gradleClassifier),
        listOfNotNull(kotlinClassifier, agpClassifier),
        listOfNotNull(gradleClassifier, agpClassifier),
        listOfNotNull(kotlinClassifier),
        listOfNotNull(gradleClassifier),
        listOfNotNull(agpClassifier),
    )

    return prioritisedClassifyingParts
        .filter { it.isNotEmpty() }
        .map { classifierParts -> fileWithClassifyingParts(testDataDir, mode, classifierParts) }
        .firstNotNullOfOrNull { it.takeIf(File::exists) }
        ?: fileWithClassifyingParts(testDataDir, mode, classifyingParts = emptyList()) // Non-existent file
}

private fun findExpectedTestDataFiles(
    testDataDir: File,
    kotlinClassifier: String,
    gradleClassifier: String,
    agpClassifier: String?,
    checkModes: List<WorkspacePrintingMode>,
    testClassifier: String?,
): List<Pair<File, WorkspacePrintingMode>> = checkModes.map { mode ->
    findMostSpecificExistingFileOrNewDefault(testDataDir, kotlinClassifier, gradleClassifier, agpClassifier, mode, testClassifier) to mode
}

enum class WorkspacePrintingMode(
    val filePrefix: String,
    val description: String,
    val printer: WorkspaceModelPrinterFactory,
) {
    FULL(
        filePrefix = "workspace",
        description = "List of all modules with Kotlin Facets and dependencies, list of all libraries and list all SDKs",
        printer = WorkspaceModelPrinterFactory {
            addContributor(KotlinFacetSettingsPrinterContributor())
            addContributor(OrderEntryPrinterContributor())
        }
    ),
    MODULES(
        filePrefix = "modules",
        description = "List of all modules in a project",
        printer = WorkspaceModelPrinterFactory {
            addContributor(NoopModulePrinterContributor())
        }
    ),
    MODULE_DEPENDENCIES(
        filePrefix = "dependencies",
        description = "List of all modules in a project with their dependencies",
        printer = WorkspaceModelPrinterFactory {
            addContributor(OrderEntryPrinterContributor())
        }

    ),
    MODULE_FACETS(
        filePrefix = "facets",
        description = "List of all modules in a project with their Kotlin Facet settings",
        printer = WorkspaceModelPrinterFactory {
            addContributor(KotlinFacetSettingsPrinterContributor())
        }
    ),
    SOURCE_ROOTS(
        filePrefix = "source-roots",
        description = "List of all modules in a project with their source roots",
        printer = WorkspaceModelPrinterFactory {
            addContributor(ContentRootsContributor())
        }
    ),
    TEST_TASKS(
        filePrefix = "test-tasks",
        description = "List of all modules in a project with imported ExternalSystem test tasks",
        printer = WorkspaceModelPrinterFactory {
            addContributor(TestTasksContributor())
        }
    )
}

private fun fileWithClassifyingParts(testDataDir: File, mode: WorkspacePrintingMode, classifyingParts: List<String>): File {
    val allParts = buildList {
        add(mode.filePrefix)
        addAll(classifyingParts)
    }
    val fileName = allParts.joinToString(separator = "-", postfix = ".$EXT")
    return testDataDir.resolve(fileName)
}

private const val EXT = "txt"
