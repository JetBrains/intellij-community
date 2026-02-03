// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.gradle

import com.intellij.testFramework.TestDataPath
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.AbstractGradleCodeInsightTest
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
@TestDataPath("\$CONTENT_ROOT")
@TestMetadata("../../../idea/tests/testData/gradle/findUsages/")
class KotlinGradleCompositeBuildFindUsagesTest : AbstractGradleCodeInsightTest() {
    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("propertyFromCustomPluginUsageInBuildGradleKts.test")
    fun testPropertyFromCustomPluginUsageInBuildGradleKts(gradleVersion: GradleVersion) {
        verifyFindUsages(gradleVersion, GRADLE_COMPOSITE_BUILD_FIXTURE)
    }

    companion object {
        val GRADLE_COMPOSITE_BUILD_FIXTURE: GradleTestFixtureBuilder =
            GradleTestFixtureBuilder.create("GradleKotlinFixture") { gradleVersion ->
                withSettingsFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                    setProjectName("GradleKotlinFixture")
                    includeBuild("not-build-src")
                }
                withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                    withPlugin("some-custom-plugin")
                }
                withFile(
                    "not-build-src/src/main/kotlin/utils.kt",
                    """
                    import org.gradle.api.Plugin
                    import org.gradle.api.Project

                    class SomeCustomPlugin: Plugin<Project> {
                        override fun apply(target: Project) {
                            // no-op
                        }
                    }

                    const val kotlinStdLib = "..."
                """.trimIndent()
                )
                withSettingsFile(gradleVersion, "not-build-src", gradleDsl = GradleDsl.KOTLIN) {
                    addCode(
                        """
                    pluginManagement {
                        repositories {
                            gradlePluginPortal()
                        }
                    }
                """.trimIndent()
                    )
                }
                withBuildFile(gradleVersion, "not-build-src", gradleDsl = GradleDsl.KOTLIN) {
                    withPrefix {
                        code(
                            """
                        plugins {
                            id("java-gradle-plugin")
                            `kotlin-dsl`
                        }
                    """.trimIndent()
                        )
                    }
                    withMavenCentral()
                    withPostfix {
                        code(
                            """
                        gradlePlugin {
                            plugins.register("some-custom-plugin") {
                                id = "some-custom-plugin"
                                implementationClass = "SomeCustomPlugin"
                            }
                        }
                    """.trimIndent()
                        )
                    }
                }
            }
    }
}
