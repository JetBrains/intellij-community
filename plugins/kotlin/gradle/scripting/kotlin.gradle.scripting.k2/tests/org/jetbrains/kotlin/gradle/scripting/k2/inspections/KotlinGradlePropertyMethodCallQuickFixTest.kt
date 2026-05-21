// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.testFramework.VfsTestUtil
import org.assertj.core.api.Assertions.assertThat
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntityProvider
import org.jetbrains.kotlin.gradle.K2GradleCodeInsightTestCase
import org.jetbrains.kotlin.idea.testFramework.gradle.KotlinGradleProjectTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.util.assertThatKotlinDslScriptsModelImportIsSupported
import org.junit.jupiter.params.ParameterizedTest

class KotlinGradlePropertyMethodCallQuickFixTest : K2GradleCodeInsightTestCase() {
    @ParameterizedTest
    @BaseGradleVersionSource
    fun testUnwrapPropertyMethodCallWithGet(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testUnwrapQuickFix(
                sampleBuildScript(
                    """
          val stripped = sample.version.<caret>replace("-SNAPSHOT", "")
          """.trimIndent()
                ),
                sampleBuildScript(
                    """
          val stripped = sample.version.get().replace("-SNAPSHOT", "")
          """.trimIndent()
                ),
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testUnwrapListPropertyMethodCallWithGet(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testUnwrapQuickFix(
                sampleBuildScript(
                    """
          val joined = sample.names.<caret>joinToString(",")
          """.trimIndent()
                ),
                sampleBuildScript(
                    """
          val joined = sample.names.get().joinToString(",")
          """.trimIndent()
                ),
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testUnwrapSetPropertyMethodCallWithGet(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testUnwrapQuickFix(
                sampleBuildScript(
                    """
          val contains = sample.tags.<caret>containsAll(setOf("release"))
          """.trimIndent()
                ),
                sampleBuildScript(
                    """
          val contains = sample.tags.get().containsAll(setOf("release"))
          """.trimIndent()
                ),
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testUnwrapMapPropertyMethodCallWithGet(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testUnwrapQuickFix(
                sampleBuildScript(
                    """
          val contains = sample.coordinates.<caret>containsKey("group")
          """.trimIndent()
                ),
                sampleBuildScript(
                    """
          val contains = sample.coordinates.get().containsKey("group")
          """.trimIndent()
                ),
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testUnwrapProviderMethodCallWithGet(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testUnwrapQuickFix(
                sampleBuildScript(
                    """
          val files = sample.destinationDir.asFile.<caret>walkTopDown()
          """.trimIndent()
                ),
                sampleBuildScript(
                    """
          val files = sample.destinationDir.asFile.get().walkTopDown()
          """.trimIndent()
                ),
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testUnwrapDirectoryPropertyFileMethodCallWithGetAsFile(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testUnwrapQuickFix(
                sampleBuildScript(
                    """
          val files = sample.destinationDir.<caret>walkTopDown()
          """.trimIndent()
                ),
                sampleBuildScript(
                    """
          val files = sample.destinationDir.get().asFile.walkTopDown()
          """.trimIndent()
                ),
                UNWRAP_WITH_GET_AS_FILE_INTENTION,
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testUnwrapRegularFilePropertyFileMethodCallWithGetAsFile(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testUnwrapQuickFix(
                sampleBuildScript(
                    """
          val text = sample.outputFile.<caret>readText()
          """.trimIndent()
                ),
                sampleBuildScript(
                    """
          val text = sample.outputFile.get().asFile.readText()
          """.trimIndent()
                ),
                UNWRAP_WITH_GET_AS_FILE_INTENTION,
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testUnwrapFileSystemLocationPropertyFileMethodCallWithGetAsFile(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testUnwrapQuickFix(
                sampleBuildScript(
                    """
          val location: FileSystemLocationProperty<*> = sample.destinationDir
          val exists = location.<caret>exists()
          """.trimIndent()
                ),
                sampleBuildScript(
                    """
          val location: FileSystemLocationProperty<*> = sample.destinationDir
          val exists = location.get().asFile.exists()
          """.trimIndent()
                ),
                UNWRAP_WITH_GET_AS_FILE_INTENTION,
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testDoesNotOfferGetAsFileFixWhenUnwrappedCallStillDoesNotResolve(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testNoFilePropertyUnwrapQuickFix(
                sampleBuildScript(
                    """
          val files = sample.destinationDir.<caret>unknownFileMethod()
          """.trimIndent()
                ),
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testDoesNotOfferFixForNonGradlePropertyReceiver(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testNoUnwrapQuickFix(
                sampleBuildScript(
                    """
          class Box(val value: String)
          val box = Box("1.0-SNAPSHOT")
          box.<caret>replace("-SNAPSHOT", "")
          """.trimIndent()
                )
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testDoesNotOfferFixForAlreadyUnwrappedProperty(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testNoUnwrapQuickFix(
                sampleBuildScript(
                    """
          val stripped = sample.version.get().<caret>replace("-SNAPSHOT", "")
          """.trimIndent()
                )
            )
        }
    }

    private fun sampleBuildScript(statement: String): String {
        return """
      import org.gradle.api.file.DirectoryProperty
      import org.gradle.api.file.FileSystemLocationProperty
      import org.gradle.api.file.RegularFileProperty
      import org.gradle.api.provider.ListProperty
      import org.gradle.api.provider.MapProperty
      import org.gradle.api.provider.Property
      import org.gradle.api.provider.SetProperty

      abstract class SampleExtension {
          abstract val version: Property<String>
          abstract val output: Property<String>
          abstract val names: ListProperty<String>
          abstract val tags: SetProperty<String>
          abstract val coordinates: MapProperty<String, String>
          abstract val destinationDir: DirectoryProperty
          abstract val outputFile: RegularFileProperty
      }

      val sample: SampleExtension = objects.newInstance(SampleExtension::class.java)
      $statement
    """.trimIndent()
    }

    private fun runTest(
        gradleVersion: GradleVersion,
        test: () -> Unit,
    ) {
        assertThatKotlinDslScriptsModelImportIsSupported(gradleVersion)
        test(gradleVersion, KotlinGradleProjectTestCase.KOTLIN_PROJECT) {
            VfsTestUtil.syncRefresh()
            test()
        }
    }

    private fun testUnwrapQuickFix(
        before: String,
        after: String,
        intentionName: String = UNWRAP_WITH_GET_INTENTION,
    ) {
        withConfiguredBuildScript(before) {
            val intentions = codeInsightFixture.filterAvailableIntentions(intentionName)
            assertThat(intentions).hasSize(1)
            val intention = intentions.single()
            assertThat((IntentionActionDelegate.unwrap(intention) as PriorityAction).priority).isEqualTo(PriorityAction.Priority.HIGH)
            codeInsightFixture.launchAction(intention)
            codeInsightFixture.checkResult(after)
        }
    }

    private fun testNoUnwrapQuickFix(
        before: String,
        intentionName: String = UNWRAP_WITH_GET_INTENTION,
    ) {
        withConfiguredBuildScript(before) {
            assertThat(codeInsightFixture.filterAvailableIntentions(intentionName))
                .isEmpty()
        }
    }

    private fun testNoFilePropertyUnwrapQuickFix(
        before: String,
    ) {
        withConfiguredBuildScript(before) {
            assertThat(codeInsightFixture.filterAvailableIntentions(UNWRAP_WITH_GET_AS_FILE_INTENTION))
                .isEmpty()
            assertThat(codeInsightFixture.filterAvailableIntentions(UNWRAP_WITH_GET_INTENTION))
                .isEmpty()
        }
    }

    private fun withConfiguredBuildScript(before: String, action: () -> Unit) {
        checkCaret(before)
        writeTextAndCommit("build.gradle.kts", before)
        try {
            runInEdtAndWait {
                codeInsightFixture.configureFromExistingVirtualFile(getFile("build.gradle.kts"))
            }
            VfsTestUtil.syncRefresh()
            waitForBuildScriptKotlinEntity()
            runInEdtAndWait {
                codeInsightFixture.doHighlighting()
                action()
            }
        } finally {
            runInEdtAndWait {
                gradleFixture.fileFixture.rollback("build.gradle.kts")
            }
            VfsTestUtil.syncRefresh()
        }
    }

    private fun waitForBuildScriptKotlinEntity() {
        runInEdtAndWait {
            PlatformTestUtil.waitWithEventsDispatching(
                "Kotlin script entity was not created for build.gradle.kts",
                { KotlinScriptEntityProvider.findKotlinScriptEntity(project, getFile("build.gradle.kts")) != null },
                10,
            )
        }
    }

    private companion object {
        const val UNWRAP_WITH_GET_INTENTION = "Unwrap with '.get()'"
        const val UNWRAP_WITH_GET_AS_FILE_INTENTION = "Unwrap with '.get().asFile'"
    }
}
