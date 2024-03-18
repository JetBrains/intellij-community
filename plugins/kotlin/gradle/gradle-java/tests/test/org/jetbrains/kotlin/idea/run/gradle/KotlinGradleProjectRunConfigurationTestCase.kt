// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.run.gradle

import com.intellij.openapi.application.runReadAction
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.run.getConfiguration
import org.jetbrains.kotlin.idea.test.util.checkPluginIsCorrect
import org.jetbrains.kotlin.idea.testFramework.gradle.KotlinGradleProjectTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.junit.jupiter.params.ParameterizedTest
import kotlin.test.assertEquals

abstract class KotlinGradleProjectRunConfigurationTestCase : KotlinGradleProjectTestCase() {

    open fun isFirPlugin(): Boolean = false

    override fun setUp() {
        super.setUp()
        checkPluginIsCorrect(isFirPlugin())
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testInternalTest(gradleVersion: GradleVersion) {
        testKotlinProject(gradleVersion) {
            val file = writeText("src/test/kotlin/org/example/TestCase.kt", """
                |package org.example
                |
                |import org.junit.jupiter.api.Test
                |
                |class TestCase {
                |    @Test
                |    internal fun test() = Unit
                |}
            """.trimMargin())
            runReadAction {
                val methodConfiguration = getConfiguration(file, project, "test")
                assertEquals("TestCase.test\$kotlin_plugin_project", methodConfiguration.configuration.name)
            }
        }
    }
}