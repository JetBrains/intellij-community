// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.test

import com.intellij.openapi.util.text.StringUtil
import com.intellij.rt.execution.junit.FileComparisonFailure
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.io.exists
import com.intellij.util.io.readText
import junit.framework.TestCase
import org.jetbrains.kotlin.test.Assertions
import org.jetbrains.kotlin.test.util.trimTrailingWhitespacesAndAddNewlineAtEOF
import org.junit.Assert
import java.io.File
import kotlin.io.path.absolutePathString
import kotlin.io.path.writeText

object JUnit4Assertions : Assertions() {
    override fun assertEqualsToFile(expectedFile: File, actual: String, sanitizer: (String) -> String, message: () -> String) {
        val expectedPath = expectedFile.toPath()
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

    override fun assertEquals(expected: Any?, actual: Any?, message: (() -> String)?) {
        Assert.assertEquals(message?.invoke(), expected, actual)
    }

    override fun assertNotEquals(expected: Any?, actual: Any?, message: (() -> String)?) {
        Assert.assertNotEquals(message?.invoke(), expected, actual)
    }

    override fun assertTrue(value: Boolean, message: (() -> String)?) {
        Assert.assertTrue(message?.invoke(), value)
    }

    override fun assertFalse(value: Boolean, message: (() -> String)?) {
        Assert.assertFalse(message?.invoke(), value)
    }

    override fun assertNotNull(value: Any?, message: (() -> String)?) {
        Assert.assertNotNull(message?.invoke(), value)
    }

    override fun <T> assertSameElements(expected: Collection<T>, actual: Collection<T>, message: (() -> String)?) {
        UsefulTestCase.assertSameElements(message?.invoke() ?: "Collections are different", actual, expected)
    }

    override fun assertAll(exceptions: List<Throwable>) {
        exceptions.forEach { throw it }
    }

    override fun fail(message: () -> String): Nothing {
        throw AssertionError(message.invoke())
    }
}
