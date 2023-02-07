// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.workspace

import java.io.File

internal fun findMostSpecificExistingFileOrNewDefault(
    checkerClassifier: String,
    testDataDir: File,
    kotlinClassifier: String,
    gradleClassifier: String,
    agpClassifier: String?,
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
