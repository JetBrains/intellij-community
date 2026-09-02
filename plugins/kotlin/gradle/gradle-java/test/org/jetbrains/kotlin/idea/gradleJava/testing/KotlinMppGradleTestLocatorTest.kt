// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.testing

import org.jetbrains.kotlin.idea.gradleJava.testing.KotlinMppGradleTestLocator.TestLocationPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import kotlin.test.Test

internal class KotlinMppGradleTestLocatorTest {

    @Test
    fun `parse supported source paths`() {
        val testCases = listOf(
            TestCase(
                sourcePath = "a.b.GreetingTest",
                isTestProtocol = false,
                expected = TestLocationPath("a.b.GreetingTest", null, null),
            ),
            TestCase(
                sourcePath = "a.b.GreetingTest.",
                isTestProtocol = false,
                expected = TestLocationPath("a.b.GreetingTest", null, null),
            ),
            TestCase(
                sourcePath = "a.b.GreetingTest/testGreeting",
                isTestProtocol = true,
                expected = TestLocationPath("a.b.GreetingTest", "testGreeting", null),
            ),
            TestCase(
                sourcePath = "a.b.GreetingTest/testGreeting",
                isTestProtocol = false,
                expected = TestLocationPath("a.b.GreetingTest", "testGreeting", null),
            ),
            TestCase(
                sourcePath = "a.b.GreetingTest.testGreeting",
                isTestProtocol = true,
                expected = TestLocationPath("a.b.GreetingTest", "testGreeting", null),
            ),
        )

        for ((sourcePath, isTestProtocol, expected) in testCases) {
            assertEquals(
                sourcePath,
                expected,
                TestLocationPath.parse(sourcePath, isTestProtocol),
            )
        }
    }

    @Test
    fun `parse method paths for Kotlin Multiplatform targets`() {
        val testCases = listOf(
            TargetTestCase("AndroidGreetingTest", "android"),
            TargetTestCase("JvmGreetingTest", "jvm"),
            TargetTestCase("IosGreetingTest", "iosArm64"),
            TargetTestCase("IosGreetingTest", "iosSimulatorArm64"),
            TargetTestCase("JsGreetingTest", "js, browser, ChromeHeadless"),
            TargetTestCase("WasmJsGreetingTest", "wasmJs, browser, ChromeHeadless"),
            TargetTestCase("WebGreetingTest", "js, browser, ChromeHeadless"),
            TargetTestCase("WebGreetingTest", "wasmJs, browser, ChromeHeadless"),
            TargetTestCase("CommonGreetingTest", "iosSimulatorArm64"),
        )

        for ((className, targetSuffix) in testCases) {
            val qualifiedClassName = "a.b.$className"
            val sourcePath = "$qualifiedClassName/testGreeting[$targetSuffix]"
            assertEquals(
                sourcePath,
                TestLocationPath(qualifiedClassName, "testGreeting", "[$targetSuffix]"),
                TestLocationPath.parse(sourcePath, isTestProtocol = true),
            )
        }
    }

    @Test
    fun `reject invalid test source paths`() {
        val invalidPaths = listOf(
            "",
            "GreetingTest",
            ".testGreeting",
            "a.b.GreetingTest.",
        )

        for (sourcePath in invalidPaths) {
            assertNull(sourcePath, TestLocationPath.parse(sourcePath, isTestProtocol = true))
        }
    }

    private data class TestCase(
        val sourcePath: String,
        val isTestProtocol: Boolean,
        val expected: TestLocationPath,
    )

    private data class TargetTestCase(
        val className: String,
        val targetSuffix: String,
    )
}
