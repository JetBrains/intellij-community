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
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.util.assertThatKotlinDslScriptsModelImportIsSupported
import org.junit.jupiter.params.ParameterizedTest

class KotlinGradlePropertyMethodCallQuickFixTest : K2GradleCodeInsightTestCase() {

  private fun runTest(
    gradleVersion: GradleVersion,
    test: () -> Unit,
  ) {
    assertThatKotlinDslScriptsModelImportIsSupported(gradleVersion)
    testKotlinDslEmptyProject(gradleVersion) {
      VfsTestUtil.syncRefresh()
      test()
    }
  }

  private fun testUnwrapQuickFix(before: String, after: String) {
    withConfiguredBuildScript(before) {
      val intentions = codeInsightFixture.filterAvailableIntentions(UNWRAP_INTENTION)
      assertThat(intentions).hasSize(1)
      val intention = intentions.single()
      assertThat((IntentionActionDelegate.unwrap(intention) as PriorityAction).priority).isEqualTo(PriorityAction.Priority.HIGH)
      codeInsightFixture.launchAction(intention)
      codeInsightFixture.checkResult(after)
    }
  }

  private fun testNoUnwrapQuickFix(before: String) {
    withConfiguredBuildScript(before) {
      assertThat(codeInsightFixture.filterAvailableIntentions(UNWRAP_INTENTION))
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
      waitForKotlinScriptEntity("build.gradle.kts")
      runInEdtAndWait {
        codeInsightFixture.doHighlighting()
        action()
      }
    }
    finally {
      runInEdtAndWait {
        gradleFixture.fileFixture.rollback("build.gradle.kts")
      }
      VfsTestUtil.syncRefresh()
    }
  }

  private fun waitForKotlinScriptEntity(fileName: String) {
    runInEdtAndWait {
      PlatformTestUtil.waitWithEventsDispatching(
        "Kotlin script entity was not created for $fileName",
        { KotlinScriptEntityProvider.findKotlinScriptEntity(project, getFile(fileName)) != null },
        10,
      )
    }
  }

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
          val contains = sample.tags.<caret>contains("release")
          """.trimIndent()
        ),
        sampleBuildScript(
          """
          val contains = sample.tags.get().contains("release")
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
  fun testDoesNotOfferFixWhenUnwrappedCallStillDoesNotResolve(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testNoUnwrapQuickFix(
        sampleBuildScript(
          """
          val files = sample.destinationDir.<caret>walkTopDown()
          """.trimIndent()
        )
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
      }

      val sample: SampleExtension = objects.newInstance(SampleExtension::class.java)
      $statement
    """.trimIndent()
  }

  private companion object {
    const val UNWRAP_INTENTION = "Unwrap with '.get()'"
  }
}
