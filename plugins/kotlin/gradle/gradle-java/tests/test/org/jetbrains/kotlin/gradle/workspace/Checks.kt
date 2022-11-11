// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.workspace

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.codeInsight.gradle.MultiplePluginVersionGradleImportingTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import java.io.File

fun MultiplePluginVersionGradleImportingTestCase.checkWorkspaceModel(project: Project, testDataDir: File) {
    checkWorkspaceModel(project, testDataDir, kotlinPluginVersion, gradleVersion)
}

fun checkWorkspaceModel(project: Project, testDataDir: File, kotlinPluginVersion: KotlinToolingVersion, gradleVersion: String) {
    val kotlinClassifier = with(kotlinPluginVersion) { "$major.$minor.$patch" }
    val gradleClassifier = gradleVersion
    val matchingFiles = findMatchingFiles(testDataDir, kotlinClassifier, gradleClassifier)

    check(matchingFiles.isNotEmpty()) {
        """No expected files found for workspace model checks (KGP ${kotlinClassifier}, Gradle: ${gradleClassifier}).
           |Expected at least one file with name '<mode>[-<kotlinPluginVersion>][-GradleVersion].txt' in '${testDataDir.absoluteFile}'.
           |Where <mode> is one of the following:
           |${ WorkspacePrintingMode.values().joinToString(System.lineSeparator()) { "'${it.filePrefix}': ${it.description}" } }
        """.trimMargin()
    }

    for ((expectedFile, mode) in matchingFiles) {
        val actualWorkspaceModelText = mode.printer().print(project)

        KotlinTestUtils.assertEqualsToFile(
            expectedFile,
            actualWorkspaceModelText
        ) { sanitizeExpectedFile(it, kotlinPluginVersion) }
    }
}

private fun findMostSpecificExistingFile(
    testDataDir: File,
    kotlinClassifier: String,
    gradleClassifier: String,
    mode: WorkspacePrintingMode,
): File? {
    val prioritisedClassifyingParts = sequenceOf(
        listOf(kotlinClassifier, gradleClassifier),
        listOf(kotlinClassifier),
        listOf(gradleClassifier),
        emptyList(),
    )

    return prioritisedClassifyingParts
        .map { classifierParts -> fileWithClassifyingParts(testDataDir, mode, classifierParts) }
        .firstNotNullOfOrNull { it.takeIf(File::exists) }
}

private fun findMatchingFiles(
    testDataDir: File,
    kotlinClassifier: String,
    gradleClassifier: String,
): List<Pair<File, WorkspacePrintingMode>> = WorkspacePrintingMode.values().mapNotNull { mode ->
    findMostSpecificExistingFile(testDataDir, kotlinClassifier, gradleClassifier, mode)?.let { file ->
        file to mode
    }
}

enum class WorkspacePrintingMode(
    val filePrefix: String,
    val description: String,
    val printer: () -> WorkspaceModelPrinter,
) {
    FULL(
        filePrefix = "workspace",
        description = "List of all modules with Kotlin Facets and dependencies, list of all libraries and list all SDKs",
        printer = WorkspaceModelPrinters::fullWorkspacePrinter,
    ),
    MODULES(
        filePrefix = "modules",
        description = "List of all modules in a project",
        printer = WorkspaceModelPrinters::moduleNamesPrinter,
    ),
    MODULE_DEPENDENCIES(
        filePrefix = "dependencies",
        description = "List of all modules in a project with their dependencies",
        printer = WorkspaceModelPrinters::moduleDependenciesPrinter,
    ),
    MODULE_FACETS(
        filePrefix = "facets",
        description = "List of all modules in a project with their Kotlin Facet settings",
        printer = WorkspaceModelPrinters::moduleKotlinFacetSettingsPrinter
    ),
    LIBRARIES(
        filePrefix = "libraries",
        description = "List of all libraries in a project",
        printer = WorkspaceModelPrinters::libraryNamesPrinter,
    ),
    SDKS(
        filePrefix = "sdks",
        description = "List of all SDKs in a project",
        printer = WorkspaceModelPrinters::sdkNamesPrinter,
    ),
    // TODO: module roots
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
