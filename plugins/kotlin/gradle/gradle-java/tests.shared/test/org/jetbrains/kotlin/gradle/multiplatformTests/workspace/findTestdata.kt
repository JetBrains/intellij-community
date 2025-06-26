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
): File {
    val testClassifier: String? = testConfiguration.getConfiguration(GeneralWorkspaceChecks).testClassifier

    val hostType = when {
        HostManager.hostIsMac -> "macos"
        HostManager.hostIsLinux -> "linux"
        HostManager.hostIsMingw -> "mingw"
        else -> null
    }

    val hostName = HostManager.host.name

    val classifiersFromTestProperties: Sequence<List<String>> =
        kotlinTestProperties.getTestDataClassifiersFromMostSpecific() + emptyList() // Ensure that we check "no classifiers" case

    val prioritisedClassifyingParts: Sequence<List<String>> = classifiersFromTestProperties.map {
        // NB: test classifier always come first and always present (if it is passed)
        listOfNotNull(testClassifier) + it
    }.flatMap { parts ->
        sequenceOf(parts, parts + listOfNotNull(hostType), parts + hostName)
    }

    return prioritisedClassifyingParts
        .filter { it.isNotEmpty() }
        .map { classifierParts -> fileWithClassifyingParts(testDataDir, checkerClassifier, classifierParts) }
        .firstNotNullOfOrNull { it.takeIf(File::exists) }
        ?: fileWithClassifyingParts(testDataDir, checkerClassifier, additionalClassifiers = emptyList()) // Non-existent file
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
