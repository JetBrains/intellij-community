// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.scripting.k2.K2GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.codeInspection.GradleTaskMissingDescriptionInspection
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatGradleIsAtLeast
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.junit.jupiter.params.ParameterizedTest

class KotlinTaskMissingDescriptionInspectionTest : K2GradleCodeInsightTestCase() {

    private fun runTest(
        gradleVersion: GradleVersion,
        test: () -> Unit
    ) {
        assumeThatGradleIsAtLeast(gradleVersion, "9.0.0") { "Best practice added in Gradle 9.0.0" }
        test(gradleVersion, EMPTY_PROJECT_WITH_BUILD_FILE) {
            codeInsightFixture.enableInspections(GradleTaskMissingDescriptionInspection::class.java)
            (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)
            test()
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testMissing(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                "tasks.<weak_warning descr=\"Task is missing a description\">register</weak_warning>(\"someTask\") {}"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testMissingNoConfigBlock(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                "tasks.<weak_warning descr=\"Task is missing a description\">register</weak_warning>(\"someTask\")"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testMissingDelegation(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                "val task by tasks.<weak_warning descr=\"Task is missing a description\">registering</weak_warning> {}"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testMissingDelegationNoConfigBlock(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                "val task by tasks.<weak_warning descr=\"Task is missing a description\">registering</weak_warning>"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testNotMissing(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                tasks.register("someTask") {
                    description = "some description"
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testNotMissingDelegation(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                val task by tasks.registering {
                    description = "some description"
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testNotMissingSetter(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                tasks.register("someTask") {
                    setDescription("some description")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testNotMissingSetterDelegation(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                val task by tasks.registering {
                    setDescription("some description")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testNestedDescriptionAssignment(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                var cond = true
                tasks.register("someTask") {
                    if (cond) {
                        description = "some description"
                    } else {
                        description = "some other description"
                    }
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testNestedDescriptionAssignmentDelegation(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                var cond = true
                val task by tasks.registering {
                    if (cond) {
                        description = "some description"
                    } else {
                        description = "some other description"
                    }
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testNestedDescriptionSetters(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                var cond = true
                tasks.register("someTask") {
                    if (cond) {
                        setDescription("some description")
                    } else {
                        setDescription("some other description")
                    }
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testNestedDescriptionSettersDelegation(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                var cond = true
                val task by tasks.registering {
                    if (cond) {
                        setDescription("some description")
                    } else {
                        setDescription("some other description")
                    }
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testInsideTasksBlock(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                tasks {
                    <weak_warning>register</weak_warning>("someTask")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testInsideTasksBlockDelegation(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                tasks {
                    val someTask by <weak_warning>registering</weak_warning> {}
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testInsideTasksBlockDelegationNoConfigBlock(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                tasks {
                    val someTask by <weak_warning>registering</weak_warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testInsideTasksBlockNotMissing(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                tasks {
                    register("someTask") {
                        description = "some description"
                    }
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testInsideTasksBlockDelegationNotMissing(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                tasks {
                    val someTask by registering {
                        description = "some description"
                    }
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testAdding(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testIntention(
                """
                tasks.register<caret>("someTask") {}
                """.trimIndent(),
                """
                tasks.register("someTask") {
                    description = "<caret>"
                }
                """.trimIndent(),
                "Add a description"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testAddingDelegation(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testIntention(
                """
                val task by tasks.registering<caret> {}
                """.trimIndent(),
                """
                val task by tasks.registering {
                    description = "<caret>"
                }
                """.trimIndent(),
                "Add a description"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testAddingBeforeAnyElement(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testIntention(
                """
                tasks.register<caret>("someTask") {
                    group = "existing group"
                }
                """.trimIndent(),
                """
                tasks.register("someTask") {
                    description = "<caret>"
                    group = "existing group"
                }
                """.trimIndent(),
                "Add a description"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testAddingBeforeAnyElementOnlyDelegation(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testIntention(
                """
                val task by tasks.registering<caret> {
                    group = "existing group"
                }
                """.trimIndent(),
                """
                val task by tasks.registering {
                    description = "<caret>"
                    group = "existing group"
                }
                """.trimIndent(),
                "Add a description"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testAddingConfigBlock(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testIntention(
                """
                tasks.register<caret>("someTask")
                """.trimIndent(),
                """
                tasks.register("someTask") {
                    description = "<caret>"
                }
                """.trimIndent(),
                "Add a description"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testAddingConfigBlockDelegation(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testIntention(
                """
                val task by tasks.registering<caret>
                """.trimIndent(),
                """
                val task by tasks.registering {
                    description = "<caret>"
                }
                """.trimIndent(),
                "Add a description"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testAddingInsideTasksBlock(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testIntention(
                """
                tasks {
                    register<caret>("someTask")
                }
                """.trimIndent(),
                """
                tasks {
                    register("someTask") {
                        description = "<caret>"
                    }
                }
                """.trimIndent(),
                "Add a description"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testAddingInsideTasksBlockDelegation(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testIntention(
                """
                tasks {
                    val someTask by registering<caret> {}
                }
                """.trimIndent(),
                """
                tasks {
                    val someTask by registering {
                        description = "<caret>"
                    }
                }
                """.trimIndent(),
                "Add a description"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testAddingConfigBlockInsideTasksBlockDelegation(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testIntention(
                """
                tasks {
                    val someTask by registering<caret>
                }
                """.trimIndent(),
                """
                tasks {
                    val someTask by registering {
                        description = "<caret>"
                    }
                }
                """.trimIndent(),
                "Add a description"
            )
        }
    }

    companion object {
        private val EMPTY_PROJECT_WITH_BUILD_FILE = GradleTestFixtureBuilder.create("empty-project-with-build-file") { gradleVersion ->
            withSettingsFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                setProjectName("empty-project-with-build-file")
            }
            withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {}
        }
    }
}