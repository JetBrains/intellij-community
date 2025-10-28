// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.codeInspection.GradleTaskMissingGroupAndDescriptionInspection
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.junit.jupiter.params.ParameterizedTest

class KotlinTaskMissingGroupAndDescriptionInspectionTest : K2GradleCodeInsightTestCase() {

    private fun runTest(
        gradleVersion: GradleVersion,
        test: () -> Unit
    ) {
        test(gradleVersion, EMPTY_PROJECT_WITH_BUILD_FILE) {
            codeInsightFixture.enableInspections(GradleTaskMissingGroupAndDescriptionInspection::class.java)
            test()
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testMissingBoth(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                "tasks.<weak_warning descr=\"Task is missing a group and description\">register</weak_warning>(\"someTask\") {}"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testMissingBothNoConfigBlock(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                "tasks.<weak_warning descr=\"Task is missing a group and description\">register</weak_warning>(\"someTask\")"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testMissingBothDelegation(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                "val task by tasks.<weak_warning descr=\"Task is missing a group and description\">registering</weak_warning> {}"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testMissingBothDelegationNoConfigBlock(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                "val task by tasks.<weak_warning descr=\"Task is missing a group and description\">registering</weak_warning>"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
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
    @BaseGradleVersionSource
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
    @BaseGradleVersionSource
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
    @BaseGradleVersionSource
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
    @BaseGradleVersionSource
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
    @BaseGradleVersionSource
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
    @BaseGradleVersionSource
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
    @BaseGradleVersionSource
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
    @BaseGradleVersionSource
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
    @BaseGradleVersionSource
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
    @BaseGradleVersionSource
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
    @BaseGradleVersionSource
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
    @BaseGradleVersionSource
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
    @BaseGradleVersionSource
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
    @BaseGradleVersionSource
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
    @BaseGradleVersionSource
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
    @BaseGradleVersionSource
    fun testAddingBoth(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testIntention(
                """
                tasks.register<caret>("someTask") {}
                """.trimIndent(),
                """
                tasks.register("someTask") {
                    group = "example group"
                    description = "example description"
                }
                """.trimIndent(),
                "Add a group and description"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testAddingBothDelegation(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testIntention(
                """
                val task by tasks.registering<caret> {}
                """.trimIndent(),
                """
                val task by tasks.registering {
                    group = "example group"
                    description = "example description"
                }
                """.trimIndent(),
                "Add a group and description"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
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
                    group = "example group"
                    description = "existing description"
                }
                """.trimIndent(),
                "Add a group"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
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
                    group = "example group"
                    description = "existing description"
                }
                """.trimIndent(),
                "Add a group"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
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
                    description = "example description"
                    group = "existing group"
                }
                """.trimIndent(),
                "Add a description"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
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
                    description = "example description"
                    group = "existing group"
                }
                """.trimIndent(),
                "Add a description"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testAddingConfigBlock(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testIntention(
                """
                tasks.register<caret>("someTask")
                """.trimIndent(),
                """
                tasks.register("someTask") {
                    group = "example group"
                    description = "example description"
                }
                """.trimIndent(),
                "Add a group and description"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testAddingConfigBlockDelegation(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testIntention(
                """
                val task by tasks.registering<caret>
                """.trimIndent(),
                """
                val task by tasks.registering {
                    group = "example group"
                    description = "example description"
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
            withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {}
        }
    }
}