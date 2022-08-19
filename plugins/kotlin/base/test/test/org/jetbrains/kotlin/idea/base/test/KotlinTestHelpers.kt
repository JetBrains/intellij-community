// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.test

import com.intellij.openapi.util.text.StringUtil
import com.intellij.rt.execution.junit.FileComparisonFailure
import com.intellij.util.io.exists
import com.intellij.util.io.readText
import junit.framework.TestCase
import org.jetbrains.kotlin.test.util.trimTrailingWhitespacesAndAddNewlineAtEOF
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.writeText

object KotlinTestHelpers {
    fun assertEqualsToPath(expectedPath: Path, actual: String) {
        assertEqualsToPath(expectedPath, actual, { it }, { "Actual data differs from file content" })
    }

    fun assertEqualsToPath(expectedPath: Path, actual: String, sanitizer: (String) -> String, message: () -> String) {
        if (!expectedPath.exists()) {
            expectedPath.writeText(actual)
            TestCase.fail("File didn't exist. New file was created (${expectedPath.absolutePathString()}).")
        }

        fun process(output: String): String {
            return output
                .trim()
                .let(StringUtil::convertLineSeparators)
                .trimTrailingWhitespacesAndAddNewlineAtEOF()
                .let(sanitizer)
        }

        val processedExpected = process(expectedPath.readText())
        val processedActual = process(actual)

        if (processedExpected != processedActual) {
            throw FileComparisonFailure(message(), processedExpected, processedActual, expectedPath.absolutePathString())
        }
    }
}