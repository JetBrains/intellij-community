// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.search

import com.intellij.psi.search.SearchScope
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.search.toHumanReadableString

fun assertWithExpectedScope(actual: SearchScope, expected: String) {
    val actualText = actual.toHumanReadableString()
    val expectedLines = expected.lines()
    val actualLines = actualText.lines()
    try {
        TestCase.assertEquals(expectedLines.size, actualLines.size)
        for ((index, expectedLine) in expectedLines.withIndex()) {
            val actualLine = actualLines[index]
            val firstExpectedWord = stablePartOfLineRegex.find(expectedLine)
                ?: error("stable part is not found in expected '$expectedLine'")

            val firstActualWord = stablePartOfLineRegex.find(actualLine) ?: error("stable part is not found in actual '$actualLine'")
            TestCase.assertEquals(firstExpectedWord.value, firstActualWord.value)
        }
    } catch (e: AssertionError) {
        System.err.println(e.message)
        TestCase.assertEquals(expected, actualText)
    }
}

private val stablePartOfLineRegex = Regex("[^\\[]*")