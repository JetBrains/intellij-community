// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.gradle.navigation

import com.intellij.testFramework.TestDataPath
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.AbstractKotlinGradleNavigationTest
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.test.AssertKotlinPluginMode
import org.jetbrains.kotlin.idea.test.UseK2PluginMode
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.junit.jupiter.params.ParameterizedTest

@UseK2PluginMode
@GradleProjectTestApplication
@AssertKotlinPluginMode
@TestRoot("idea/tests/testData/")
@TestDataPath($$"$CONTENT_ROOT")
@TestMetadata("../../../idea/tests/testData/gradle/navigation/composite")
class K2GradleBuildLogicPluginNavigationTest : AbstractKotlinGradleNavigationTest() {
    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("testNavigationToSettingsPluginFromSettingsGradleKts.test")
    fun testNavigationToSettingsPluginFromSettingsGradleKts(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("testNavigationToSettingsPluginNotWorkingFromBuildGradleKts.test")
    fun testNavigationToSettingsPluginNotWorkingFromBuildLogicGradleKts(gradleVersion: GradleVersion) {
        verifyFileShouldStayTheSame(gradleVersion)
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("testNavigationToBacktickedSettingsPluginFromSettingsGradleKts.test")
    fun testNavigationToBacktickedSettingsPluginFromSettingsGradleKts(gradleVersion: GradleVersion) {
        verifyNavigationFromCaretToExpected(gradleVersion)
    }

    override val myFixture = FIXTURE_WITH_SETTINGS_PLUGIN

    companion object {
        private val FIXTURE_WITH_SETTINGS_PLUGIN: GradleTestFixtureBuilder =
            GradleTestFixtureBuilder.create("GradleKotlinFixture") { gradleVersion ->
                withBuildFile(gradleVersion, "buildSrc", gradleDsl = GradleDsl.KOTLIN) {
                    withKotlinDsl()
                }

                withFile(
                    "buildSrc/src/main/kotlin/my-conventions.gradle.kts", """
                    val fromBuildSrc = "my-conventions.gradle.kts"
                """.trimIndent()
                )

                withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                    withPlugin {
                        code("`my-conventions`")
                    }
                }

                withSettingsFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                    addCode("""
                        pluginManagement {
                            includeBuild("custom")
                        }
    
                        plugins {
                            id("custom.settings")
                        }
                    """.trimIndent())
                }

                withBuildFile(gradleVersion, "custom", gradleDsl = GradleDsl.KOTLIN) {
                    withKotlinDsl()
                }

                withFile(
                    "custom/src/main/kotlin/custom.settings.settings.gradle.kts", """
                    val fromCustomSettingsPlugin = "custom.settings.settings.gradle.kts"
                """.trimIndent()
                )

            }
    }
}