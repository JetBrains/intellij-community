// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.test

import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.kotlin.test.Assertions
import org.junit.Assert
import java.io.File

object JUnit4Assertions : Assertions() {
    override fun assertEqualsToFile(expectedFile: File, actual: String, sanitizer: (String) -> String, message: () -> String) {
        val expectedPath = expectedFile.toPath()
        KotlinTestHelpers.assertEqualsToPath(expectedPath, actual, sanitizer, message)
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

    override fun assertAll(vararg blocks: () -> Unit) {
        blocks.forEach { it.invoke() }
    }

    override fun fail(message: () -> String): Nothing {
        throw AssertionError(message.invoke())
    }
}
