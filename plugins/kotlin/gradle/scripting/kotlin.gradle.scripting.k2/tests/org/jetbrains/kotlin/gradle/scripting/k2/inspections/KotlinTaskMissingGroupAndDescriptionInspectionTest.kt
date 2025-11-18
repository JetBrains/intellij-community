// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.codeInspection.GradleTaskMissingGroupAndDescriptionInspection
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatGradleIsAtLeast
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.junit.jupiter.params.ParameterizedTest

class KotlinTaskMissingGroupAndDescriptionInspectionTest : K2GradleCodeInsightTestCase() {

    private fun runTest(
        gradleVersion: GradleVersion,
        test: () -> Unit
    ) {
        assumeThatGradleIsAtLeast(gradleVersion, "9.0.0") { "Best practice added in Gradle 9.0.0" }
        test(gradleVersion, EMPTY_PROJECT_WITH_BUILD_FILE) {
            codeInsightFixture.enableInspections(GradleTaskMissingGroupAndDescriptionInspection::class.java)
            (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)
            test()
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testMissingBoth(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                "tasks.<weak_warning descr=\"Task is missing a group and description\">register</weak_warning>(\"someTask\") {}"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testMissingBothNoConfigBlock(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                "tasks.<weak_warning descr=\"Task is missing a group and description\">register</weak_warning>(\"someTask\")"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testMissingBothDelegation(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                "val task by tasks.<weak_warning descr=\"Task is missing a group and description\">registering</weak_warning> {}"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testMissingBothDelegationNoConfigBlock(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                "val task by tasks.<weak_warning descr=\"Task is missing a group and description\">registering</weak_warning>"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testMissingOnlyGroup(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                tasks.<weak_warning descr="Task is missing a group">register</weak_warning>("someTask") {
                    description = "some description"
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testMissingOnlyGroupDelegation(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                val task by tasks.<weak_warning descr="Task is missing a group">registering</weak_warning> {
                    description = "some description"
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testMissingOnlyDescription(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                tasks.<weak_warning descr="Task is missing a description">register</weak_warning>("someTask") {
                    group = "some group"
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testMissingOnlyDescriptionDelegation(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                val task by tasks.<weak_warning descr="Task is missing a description">registering</weak_warning> {
                    group = "some group"
                }
                """.trimIndent()
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
                    group = "some group"
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
                    group = "some group"
                    description = "some description"
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testWithSetGroup(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                tasks.<weak_warning descr="Task is missing a description">register</weak_warning>("someTask") {
                    setGroup("some group")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testWithSetGroupDelegation(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                val task by tasks.<weak_warning descr="Task is missing a description">registering</weak_warning> {
                    setGroup("some group")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testWithSetDescription(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                tasks.<weak_warning descr="Task is missing a group">register</weak_warning>("someTask") {
                    setDescription("some description")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testWithSetDescriptionDelegation(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                val task by tasks.<weak_warning descr="Task is missing a group">registering</weak_warning> {
                    setDescription("some description")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testWithSetBoth(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                tasks.register("someTask") {
                    setGroup("some group")
                    setDescription("some description")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testWithSetBothDelegation(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                val task by tasks.registering {
                    setGroup("some group")
                    setDescription("some description")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testNestedGroupAndDescriptionAssignment(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                var cond = true
                tasks.register("someTask") {
                    if (cond) {
                        group = "some group"
                        description = "some description"
                    } else {
                        group = "some other group"
                        description = "some other description"
                    }
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testNestedGroupAndDescriptionAssignmentDelegation(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                var cond = true
                val task by tasks.registering {
                    if (cond) {
                        group = "some group"
                        description = "some description"
                    } else {
                        group = "some other group"
                        description = "some other description"
                    }
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testNestedGroupAndDescriptionSetters(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                var cond = true
                tasks.register("someTask") {
                    if (cond) {
                        setGroup("some group")
                        setDescription("some description")
                    } else {
                        setGroup("some other group")
                        setDescription("some other description")
                    }
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testNestedGroupAndDescriptionSettersDelegation(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                var cond = true
                val task by tasks.registering {
                    if (cond) {
                        setGroup("some group")
                        setDescription("some description")
                    } else {
                        setGroup("some other group")
                        setDescription("some other description")
                    }
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testAddingBoth(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testIntention(
                """
                tasks.register<caret>("someTask") {}
                """.trimIndent(),
                """
                tasks.register("someTask") {
                    group = "AGroup"
                    description = ""
                }
                """.trimIndent(),
                "Add a group and description"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testAddingBothDelegation(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testIntention(
                """
                val task by tasks.registering<caret> {}
                """.trimIndent(),
                """
                val task by tasks.registering {
                    group = "AGroup"
                    description = ""
                }
                """.trimIndent(),
                "Add a group and description"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testAddingGroupOnly(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testIntention(
                """
                tasks.register<caret>("someTask") {
                    description = "existing description"
                }
                """.trimIndent(),
                """
                tasks.register("someTask") {
                    group = "AGroup"
                    description = "existing description"
                }
                """.trimIndent(),
                "Add a group"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testAddingGroupOnlyDelegation(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testIntention(
                """
                val task by tasks.registering<caret> {
                    description = "existing description"
                }
                """.trimIndent(),
                """
                val task by tasks.registering {
                    group = "AGroup"
                    description = "existing description"
                }
                """.trimIndent(),
                "Add a group"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testAddingDescriptionOnly(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testIntention(
                """
                tasks.register<caret>("someTask") {
                    group = "existing group"
                }
                """.trimIndent(),
                """
                tasks.register("someTask") {
                    description = ""
                    group = "existing group"
                }
                """.trimIndent(),
                "Add a description"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testAddingDescriptionOnlyDelegation(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testIntention(
                """
                val task by tasks.registering<caret> {
                    group = "existing group"
                }
                """.trimIndent(),
                """
                val task by tasks.registering {
                    description = ""
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
                    group = "AGroup"
                    description = ""
                }
                """.trimIndent(),
                "Add a group and description"
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
                    group = "AGroup"
                    description = ""
                }
                """.trimIndent(),
                "Add a group and description"
            )
        }
    }

    companion object {
        private val EMPTY_PROJECT_WITH_BUILD_FILE = GradleTestFixtureBuilder.create("empty-project-with-build-file") { gradleVersion ->
            withSettingsFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                setProjectName("empty-project-with-build-file")
            }
            withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                registerTask("aTask") {
                    assign("group", "AGroup")
                }
            }
        }
    }
}