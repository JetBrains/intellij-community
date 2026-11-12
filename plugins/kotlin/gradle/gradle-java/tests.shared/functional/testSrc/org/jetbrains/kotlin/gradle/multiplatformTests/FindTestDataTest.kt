// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests

import org.jetbrains.kotlin.gradle.multiplatformTests.workspace.findMostSpecificExistingFileOrNewDefault
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

internal class FindTestDataTest {

    @Test
    fun test() {
        assertEquals(
            File("/foo/bar/checker-latest.txt"),
            findMostSpecificExistingFileOrNewDefault(
                checkerClassifier = "checker",
                testDataDir = File("/foo/bar"),
                testDataClassifiersFromMostSpecific = sequenceOf<List<String>>(
                    listOf("LATEST", "1"),
                    listOf("1"),
                ),
                testClassifier = "latest",
                fileExists = { false }
            ),
        )

        assertEquals(
            File("/foo/bar/checker.txt"),
            findMostSpecificExistingFileOrNewDefault(
                checkerClassifier = "checker",
                testDataDir = File("/foo/bar"),
                testDataClassifiersFromMostSpecific = sequenceOf<List<String>>(
                    listOf("LATEST", "1"),
                    listOf("1"),
                ),
                testClassifier = null,
                fileExists = { false }
            ),
        )

        val existingFile = File("/foo/bar/checker-latest-1.txt")
        assertEquals(
            existingFile,
            findMostSpecificExistingFileOrNewDefault(
                checkerClassifier = "checker",
                testDataDir = File("/foo/bar"),
                testDataClassifiersFromMostSpecific = sequenceOf<List<String>>(
                    listOf("LATEST", "1"),
                    listOf("1"),
                ),
                testClassifier = "latest",
                fileExists = { this == existingFile }
            ),
        )

        val moreSpecificFile = File("/foo/bar/checker-latest-LATEST-1.txt")
        val existingFiles = setOf<File>(
            existingFile,
            moreSpecificFile,
        )
        assertEquals(
            moreSpecificFile,
            findMostSpecificExistingFileOrNewDefault(
                checkerClassifier = "checker",
                testDataDir = File("/foo/bar"),
                testDataClassifiersFromMostSpecific = sequenceOf<List<String>>(
                    listOf("LATEST", "1"),
                    listOf("1"),
                ),
                testClassifier = "latest",
                fileExists = { this in existingFiles }
            ),
        )
    }
}
