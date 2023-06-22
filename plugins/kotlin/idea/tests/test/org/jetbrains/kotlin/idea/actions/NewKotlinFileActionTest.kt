// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.actions

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class NewKotlinFileActionTest : LightJavaCodeInsightFixtureTestCase() {
    companion object {
        private const val EMPTY_PARTS_ERROR = "Name can't have empty parts"
        private const val EMPTY_ERROR = "Name can't be empty"
    }

    fun testEmptyName() {
        validateName("", EMPTY_ERROR)
    }

    fun testSpaces() {
        validateName("    ", EMPTY_ERROR)
    }

    fun testEmptyEnd() {
        validateName("Foo/", EMPTY_PARTS_ERROR)
    }

    fun testEmptyPartInQualified() {
        validateName("a..b", EMPTY_PARTS_ERROR)
    }

    fun testFileWithKt() {
        validateName("test.kt", null)
    }

    fun testFileWithUnixPathKt() {
        validateName("a/b/test.kt", null)
    }

    fun testFileWithWinPathKt() {
        validateName("a\\b\\test.kt", null)
    }

    fun testSimpleFile() {
        validateName("some", null)
    }

    fun testSimpleFileWithPath() {
        validateName("a/bb\\some", null)
    }

    private fun validateName(name: String, errorMessage: String?) {
        val actualError = NewKotlinFileNameValidator.getErrorText(name)
        assertEquals("Invalid error message", errorMessage, actualError)
    }
}