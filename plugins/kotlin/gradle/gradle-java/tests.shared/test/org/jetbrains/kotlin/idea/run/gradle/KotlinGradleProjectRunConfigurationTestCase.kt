// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.run.gradle

import com.intellij.openapi.application.runReadActionBlocking
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.run.getConfiguration
import org.jetbrains.kotlin.idea.testFramework.gradle.KotlinGradleProjectTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.jupiter.params.ParameterizedTest
import kotlin.test.assertEquals

abstract class KotlinGradleProjectRunConfigurationTestCase : KotlinGradleProjectTestCase() {

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions("8.11+", reason = "As we want to run it against Kotlin 2.0+, see GradleBuildScriptBuilderUtil.getKotlinVersion")
    fun testInternalTest(gradleVersion: GradleVersion) {
        testKotlinProject(gradleVersion) {
            val file = writeText(
                "src/test/kotlin/org/example/TestCase.kt", """
                |package org.example
                |
                |import org.junit.jupiter.api.Test
                |
                |class TestCase {
                |    @Test
                |    internal fun test() = Unit
                |}
            """.trimMargin()
            )
            runReadActionBlocking {
                val methodConfiguration = getConfiguration(file, project, "test")
                assertEquals("TestCase.test\$kotlin_plugin_project_test", methodConfiguration.configuration.name)
            }
        }
    }
}