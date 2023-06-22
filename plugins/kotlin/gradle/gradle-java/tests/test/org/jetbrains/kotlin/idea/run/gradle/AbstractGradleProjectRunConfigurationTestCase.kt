// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.run.gradle

import com.intellij.openapi.application.runReadAction
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.run.getConfiguration
import org.jetbrains.kotlin.idea.test.checkPluginIsCorrect
import org.jetbrains.plugins.gradle.testFramework.GradleProjectTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.jupiter.params.ParameterizedTest
import kotlin.test.assertEquals
import org.jetbrains.kotlin.idea.codeInsight.gradle.GradleTestFixtureBuilderProvider

abstract class AbstractGradleProjectRunConfigurationTestCase : GradleProjectTestCase() {
    open fun isFirPlugin(): Boolean = false

    override fun setUp() {
        super.setUp()
        checkPluginIsCorrect(isFirPlugin())
    }

    @ParameterizedTest
    @TargetVersions("5.6.2")
    @AllGradleVersionsSource
    fun testInternalTest(gradleVersion: GradleVersion) {
        test(gradleVersion, GradleTestFixtureBuilderProvider.KOTLIN_PROJECT) {
            val testFileRelativePath = "src/test/kotlin/org/example/TestCase.kt"
            val testFileText = """
                |package org.example
                |
                |import org.junit.jupiter.api.Test
                |
                |class TestCase {
                |    @Test
                |    internal fun test() = Unit
                |}
            """.trimMargin()

            writeText(testFileRelativePath, testFileText)
            runReadAction {
                val file = gradleFixture.fileFixture.root.findFileByRelativePath(testFileRelativePath)!!
                val methodConfiguration = getConfiguration(file, project, "test")
                assertEquals("TestCase.test\$kotlin_plugin_project", methodConfiguration.configuration.name)
            }
        }
    }
}