// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.junit5

import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.kotlin.gradle.multiplatformTests.computeTestDataDirectoryPath
import org.junit.runner.Description
import java.nio.file.Path

/**
 * Resolved testdata directory for the currently running JUnit5 test method.
 * Derived from the class-level `@TestDataPath`, the chain of `@TestMetadata` annotations and the test method name —
 * see [computeTestDataDirectoryPath] for the lookup rules.
 */
interface KotlinTestData {
    val testDataDir: Path
}

fun kotlinTestDataFixture(): TestFixture<KotlinTestData> = testFixture { testFixtureContext ->
    val description = Description.createTestDescription(
        testFixtureContext.extensionContext.requiredTestClass,
        testFixtureContext.extensionContext.requiredTestMethod.name,
        *testFixtureContext.extensionContext.requiredTestMethod.annotations,
    )
    val resolvedTestDataDir = computeTestDataDirectoryPath(description, strict = false)
    val testData = object : KotlinTestData {
        override val testDataDir: Path = resolvedTestDataDir
    }
    initialized(testData) {}
}
