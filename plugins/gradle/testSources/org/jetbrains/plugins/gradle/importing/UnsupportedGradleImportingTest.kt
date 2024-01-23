// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.util.lang.JavaVersion
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import org.jetbrains.plugins.gradle.tooling.VersionMatcherRule
import org.junit.Test
import org.junit.runners.Parameterized

class UnsupportedGradleImportingTest : BuildViewMessagesImportingTestCase() {

  @Test
  fun testSyncMessages() {
    importProject("")
    assertSyncViewTree {
      when {
        !GradleJvmSupportMatrix.isGradleSupportedByIdea(currentGradleVersion) ->
          assertNode("failed") {
            assertNode("Unsupported Gradle Version")
          }
        GradleJvmSupportMatrix.isGradleDeprecatedByIdea(currentGradleVersion) ->
          assertNode("finished") {
            assertNode("Deprecated Gradle Version")
          }
        else ->
          assertNode("finished")
      }
    }
  }

  override fun assumeTestJavaRuntime(javaRuntimeVersion: JavaVersion) {
    // run on all Java Runtime
  }

  companion object {
    private val OLD_GRADLE_VERSIONS = arrayOf(
      "0.9", /*..., */"0.9.2",
      "1.0", /*"1.1", "1.2", ..., */"1.12",
      "2.0", /*"2.1", "2.2", ..., */"2.5", "2.6", "2.14.1",
      "3.0", /*"3.1", "3.2", "3.3", "3.4",*/ "3.5",
      "4.0", /*"4.1", "4.2", "4.3",*/ "4.4"
    )

    /**
     * Run the test against very old not-supported Gradle versions also
     */
    @Parameterized.Parameters(name = "with Gradle-{0}")
    @JvmStatic
    fun tests(): Iterable<Any> {
      return (OLD_GRADLE_VERSIONS + VersionMatcherRule.SUPPORTED_GRADLE_VERSIONS).toList()
    }
  }
}
