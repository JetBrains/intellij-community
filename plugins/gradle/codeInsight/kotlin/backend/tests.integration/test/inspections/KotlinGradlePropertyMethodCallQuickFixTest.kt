// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.codeInsight.kotlin.backend.tests.integration.inspections

import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.gradle.codeInsight.kotlin.testFramework.configureGradleScript
import com.intellij.gradle.codeInsight.kotlin.testFramework.fixtures.customConfigurationsGradleCodeInsightFixture
import com.intellij.gradle.testFramework.annotations.BaseGradleVersionSource
import com.intellij.gradle.testFramework.fixtures.application.GradleImmutableProjectTestApplication
import com.intellij.gradle.testFramework.util.getProjectFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.core.script.modules.KotlinScriptEntityProvider
import org.jetbrains.plugins.gradle.util.GradleConstants.KOTLIN_DSL_SCRIPT_NAME
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass

@GradleImmutableProjectTestApplication
@ParameterizedClass
@BaseGradleVersionSource
class KotlinGradlePropertyMethodCallQuickFixTest(gradleVersion: GradleVersion) {

  private val codeInsightFixture by customConfigurationsGradleCodeInsightFixture(gradleVersion)

  @BeforeEach
  fun setUp() {
    VfsTestUtil.syncRefresh()
  }

  @Test
  fun testUnwrapPropertyMethodCallWithGet() {
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

  @Test
  fun testDoesNotOfferGetFixForListPropertyMethodCall() {
    testNoUnwrapQuickFix(
      sampleBuildScript(
        """
        val joined = sample.names.<caret>joinToString(",")
        """.trimIndent()
      ),
    )
  }

  @Test
  fun testDoesNotOfferGetFixForSetPropertyMethodCall() {
    testNoUnwrapQuickFix(
      sampleBuildScript(
        """
        val contains = sample.tags.<caret>containsAll(setOf("release"))
        """.trimIndent()
      ),
    )
  }

  @Test
  fun testDoesNotOfferGetFixForMapPropertyMethodCall() {
    testNoUnwrapQuickFix(
      sampleBuildScript(
        """
        val contains = sample.coordinates.<caret>containsKey("group")
        """.trimIndent()
      ),
    )
  }

  @Test
  fun testUnwrapProviderMethodCallWithGet() {
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

  @Test
  fun testUnwrapDirectoryPropertyFileMethodCallWithGetAsFile() {
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

  @Test
  fun testUnwrapRegularFilePropertyFileMethodCallWithGetAsFile() {
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

  @Test
  fun testUnwrapFileSystemLocationPropertyFileMethodCallWithGetAsFile() {
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

  @Test
  fun testDoesNotOfferGetAsFileFixWhenUnwrappedCallStillDoesNotResolve() {
    testNoFilePropertyUnwrapQuickFix(
      sampleBuildScript(
        """
        val files = sample.destinationDir.<caret>unknownFileMethod()
        """.trimIndent()
      ),
    )
  }

  @Test
  fun testDoesNotOfferFixForNonGradlePropertyReceiver() {
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

  @Test
  fun testDoesNotOfferFixForAlreadyUnwrappedProperty() {
    testNoUnwrapQuickFix(
      sampleBuildScript(
        """
        val stripped = sample.version.get().<caret>replace("-SNAPSHOT", "")
        """.trimIndent()
      )
    )
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
    codeInsightFixture.configureGradleScript(before)
    VfsTestUtil.syncRefresh()
    waitForBuildScriptKotlinEntity()
    runInEdtAndWait {
      codeInsightFixture.doHighlighting()
      action()
    }
  }

  private fun waitForBuildScriptKotlinEntity() {
    val project = codeInsightFixture.project
    val buildScript = project.getProjectFile(KOTLIN_DSL_SCRIPT_NAME)
    runInEdtAndWait {
      PlatformTestUtil.waitWithEventsDispatching(
        "Kotlin script entity was not created for $KOTLIN_DSL_SCRIPT_NAME",
        { KotlinScriptEntityProvider.findKotlinScriptEntity(project, buildScript) != null },
        10,
      )
    }
  }

  private companion object {
    const val UNWRAP_WITH_GET_INTENTION = "Unwrap with '.get()'"
    const val UNWRAP_WITH_GET_AS_FILE_INTENTION = "Unwrap with '.get().asFile'"
  }
}
