// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.multiplatformTests.workspace

import org.jetbrains.kotlin.gradle.multiplatformTests.KotlinSyncTestsContext
import org.jetbrains.kotlin.gradle.multiplatformTests.KotlinTestProperties
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfiguration
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.workspace.GeneralWorkspaceChecks
import org.jetbrains.kotlin.konan.target.HostManager
import java.io.File

internal fun KotlinSyncTestsContext.findMostSpecificExistingFileOrNewDefault(
    checkerClassifier: String
) = findMostSpecificExistingFileOrNewDefault(
    checkerClassifier = checkerClassifier,
    testDataDir = testDataDirectory,
    kotlinTestProperties = testProperties,
    testConfiguration = testConfiguration
)

internal fun findMostSpecificExistingFileOrNewDefault(
    checkerClassifier: String,
    testDataDir: File,
    kotlinTestProperties: KotlinTestProperties,
    testConfiguration: TestConfiguration
) = findMostSpecificExistingFileOrNewDefault(
    checkerClassifier, testDataDir,
    kotlinTestProperties.getTestDataClassifiersFromMostSpecific(),
    testConfiguration.getConfiguration(GeneralWorkspaceChecks).testClassifier,
    fileExists = { this.exists() }
)

internal fun findMostSpecificExistingFileOrNewDefault(
    checkerClassifier: String,
    testDataDir: File,
    testDataClassifiersFromMostSpecific: Sequence<List<String>>,
    testClassifier: String?,
    fileExists: File.() -> Boolean,
): File {
    val hostType = when {
        HostManager.hostIsMac -> "macos"
        HostManager.hostIsLinux -> "linux"
        HostManager.hostIsMingw -> "mingw"
        else -> null
    }

    val hostName = HostManager.host.name

    val classifiersFromTestProperties: Sequence<List<String>> =
        // get classifiers such
        testDataClassifiersFromMostSpecific + listOf(emptyList()) // Ensure that we check "no classifiers" case

    val prioritisedClassifyingParts: Sequence<List<String>> = classifiersFromTestProperties.map {
        // NB: test classifier, e.g. "legacy": source-roots -> source-roots-legacy
        listOfNotNull(testClassifier) + it
    }.flatMap { parts ->
        sequenceOf(parts + hostName, parts + listOfNotNull(hostType), parts)
    }

    val prioritizedTestData = prioritisedClassifyingParts
        .map { classifierParts ->
            fileWithClassifyingParts(testDataDir, checkerClassifier, classifierParts)
        }

    return prioritizedTestData.firstOrNull { it.fileExists() }
        // return the least classified test data for test data generation
        ?: prioritizedTestData.last()
}

private fun fileWithClassifyingParts(testDataDir: File, checkerClassifier: String, additionalClassifiers: List<String>): File {
    val allParts = buildList {
        add(checkerClassifier)
        addAll(additionalClassifiers)
    }
    val fileName = allParts.joinToString(separator = "-", postfix = ".$EXT")
    return testDataDir.resolve(fileName)
}

private const val EXT = "txt"
