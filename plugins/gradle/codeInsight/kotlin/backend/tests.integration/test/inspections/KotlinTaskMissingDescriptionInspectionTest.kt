// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.codeInsight.kotlin.backend.tests.integration.inspections

import com.intellij.gradle.codeInsight.backend.inspections.declarations.GradleTaskMissingDescriptionInspection
import com.intellij.gradle.codeInsight.kotlin.testFramework.checkGradleScriptHighlighting
import com.intellij.gradle.codeInsight.kotlin.testFramework.checkGradleScriptIntention
import com.intellij.gradle.codeInsight.kotlin.testFramework.fixtures.simpleGradleCodeInsightFixture
import com.intellij.gradle.testFramework.annotations.AllGradleVersionsSource
import com.intellij.gradle.testFramework.annotations.TargetVersions
import com.intellij.gradle.testFramework.fixtures.application.GradleImmutableProjectTestApplication
import com.intellij.gradle.testFramework.util.KOTLIN_DSL_DELEGATING_PROPERTY_SUPPORTED_VERSIONS
import com.intellij.gradle.testFramework.util.KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS
import org.gradle.util.GradleVersion
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass

@GradleImmutableProjectTestApplication
class KotlinTaskMissingDescriptionInspectionTest {

  @Nested
  @ParameterizedClass
  @AllGradleVersionsSource
  @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
  inner class AllGradleVersions(gradleVersion: GradleVersion) {

    private val codeInsightFixture by simpleGradleCodeInsightFixture(gradleVersion)

    @BeforeEach
    fun enableInspection() {
      codeInsightFixture.enableInspections(GradleTaskMissingDescriptionInspection::class.java)
    }

    @Test
    fun testMissing() {
      codeInsightFixture.checkGradleScriptHighlighting(
        "tasks.<weak_warning descr=\"Task is missing a description\">register</weak_warning>(\"someTask\") {}"
      )
    }

    @Test
    fun testMissingNoConfigBlock() {
      codeInsightFixture.checkGradleScriptHighlighting(
        "tasks.<weak_warning descr=\"Task is missing a description\">register</weak_warning>(\"someTask\")"
      )
    }

    @Test
    fun testNotMissing() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        tasks.register("someTask") {
            description = "some description"
        }
        """.trimIndent()
      )
    }

    @Test
    fun testNotMissingSetter() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        tasks.register("someTask") {
            setDescription("some description")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testNestedDescriptionAssignment() {
      codeInsightFixture.checkGradleScriptHighlighting(
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

    @Test
    fun testNestedDescriptionSetters() {
      codeInsightFixture.checkGradleScriptHighlighting(
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

    @Test
    fun testInsideTasksBlock() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        tasks {
            <weak_warning>register</weak_warning>("someTask")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testInsideTasksBlockNotMissing() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        tasks {
            register("someTask") {
                description = "some description"
            }
        }
        """.trimIndent()
      )
    }

    @Test
    fun testNotMissingWithType() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        tasks.register<Copy>("someTask") {
            description = "some description"
        }
        """.trimIndent()
      )
    }

    @Test
    fun testNotMissingSetterWithType() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        tasks.register<Copy>("someTask") {
            setDescription("some description")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testInsideTasksBlockNotMissingWithType() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        tasks {
            register<Copy>("someTask") {
                description = "some description"
            }
        }
        """.trimIndent()
      )
    }

    @Test
    fun testMissingWithType() {
      codeInsightFixture.checkGradleScriptHighlighting(
        "tasks.<weak_warning>register</weak_warning><Copy>(\"someTask\") {}"
      )
    }

    @Test
    fun testMissingNoConfigBlockWithType() {
      codeInsightFixture.checkGradleScriptHighlighting(
        "tasks.<weak_warning>register</weak_warning><Copy>(\"someTask\")"
      )
    }

    @Test
    fun testInsideTasksBlockWithType() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        tasks {
            <weak_warning>register</weak_warning><Copy>("someTask")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testAdding() {
      codeInsightFixture.checkGradleScriptIntention(
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

    @Test
    fun testAddingBeforeAnyElement() {
      codeInsightFixture.checkGradleScriptIntention(
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

    @Test
    fun testAddingConfigBlock() {
      codeInsightFixture.checkGradleScriptIntention(
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

    @Test
    fun testAddingInsideTasksBlock() {
      codeInsightFixture.checkGradleScriptIntention(
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

  @Nested
  @ParameterizedClass
  @AllGradleVersionsSource
  @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS, KOTLIN_DSL_DELEGATING_PROPERTY_SUPPORTED_VERSIONS)
  inner class DelegatingPropertySupported(gradleVersion: GradleVersion) {

    private val codeInsightFixture by simpleGradleCodeInsightFixture(gradleVersion)

    @BeforeEach
    fun enableInspection() {
      codeInsightFixture.enableInspections(GradleTaskMissingDescriptionInspection::class.java)
    }

    @Test
    fun testMissingDelegation() {
      codeInsightFixture.checkGradleScriptHighlighting(
        "val task by tasks.<weak_warning descr=\"Task is missing a description\">registering</weak_warning> {}"
      )
    }

    @Test
    fun testMissingDelegationNoConfigBlock() {
      codeInsightFixture.checkGradleScriptHighlighting(
        "val task by tasks.<weak_warning descr=\"Task is missing a description\">registering</weak_warning>"
      )
    }

    @Test
    fun testNotMissingDelegation() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        val task by tasks.registering {
            description = "some description"
        }
        """.trimIndent()
      )
    }

    @Test
    fun testNotMissingSetterDelegation() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        val task by tasks.registering {
            setDescription("some description")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testNestedDescriptionAssignmentDelegation() {
      codeInsightFixture.checkGradleScriptHighlighting(
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

    @Test
    fun testNestedDescriptionSettersDelegation() {
      codeInsightFixture.checkGradleScriptHighlighting(
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

    @Test
    fun testInsideTasksBlockDelegation() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        tasks {
            val someTask by <weak_warning>registering</weak_warning> {}
        }
        """.trimIndent()
      )
    }

    @Test
    fun testInsideTasksBlockDelegationNoConfigBlock() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        tasks {
            val someTask by <weak_warning>registering</weak_warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testInsideTasksBlockDelegationNotMissing() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        tasks {
            val someTask by registering {
                description = "some description"
            }
        }
        """.trimIndent()
      )
    }

    @Test
    fun testNotMissingDelegationWithType() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        val task by tasks.registering(Copy::class) {
            description = "some description"
        }
        """.trimIndent()
      )
    }

    @Test
    fun testNotMissingSetterDelegationWithType() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        val task by tasks.registering(Copy::class) {
            setDescription("some description")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testInsideTasksBlockDelegationNotMissingWithType() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        tasks {
            val someTask by registering(Copy::class) {
                description = "some description"
            }
        }
        """.trimIndent()
      )
    }

    @Test
    fun testMissingDelegationWithType() {
      codeInsightFixture.checkGradleScriptHighlighting(
        "val task by tasks.<weak_warning>registering</weak_warning>(Copy::class) {}"
      )
    }

    @Test
    fun testMissingDelegationNoConfigBlockWithType() {
      codeInsightFixture.checkGradleScriptHighlighting(
        "val task by tasks.<weak_warning>registering</weak_warning>(Copy::class)"
      )
    }

    @Test
    fun testInsideTasksBlockDelegationWithType() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        tasks {
            val someTask by <weak_warning>registering</weak_warning>(Copy::class) {}
        }
        """.trimIndent()
      )
    }

    @Test
    fun testInsideTasksBlockDelegationNoConfigBlockWithType() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        tasks {
            val someTask by <weak_warning>registering</weak_warning>(Copy::class)
        }
        """.trimIndent()
      )
    }

    @Test
    fun testAddingDelegation() {
      codeInsightFixture.checkGradleScriptIntention(
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

    @Test
    fun testAddingBeforeAnyElementOnlyDelegation() {
      codeInsightFixture.checkGradleScriptIntention(
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

    @Test
    fun testAddingConfigBlockDelegation() {
      codeInsightFixture.checkGradleScriptIntention(
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

    @Test
    fun testAddingInsideTasksBlockDelegation() {
      codeInsightFixture.checkGradleScriptIntention(
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

    @Test
    fun testAddingConfigBlockInsideTasksBlockDelegation() {
      codeInsightFixture.checkGradleScriptIntention(
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
}
