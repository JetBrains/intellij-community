// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.tooling.VersionMatcherRule
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Test
import org.junit.runners.Parameterized

class UnsupportedGradleImportingTest : BuildViewMessagesImportingTestCase() {

  @Test
  fun testSyncMessages() {
    importProject("")
    val expectedExecutionTree: String
    when {
      currentGradleVersion < GradleVersion.version(GradleConstants.MINIMAL_SUPPORTED_GRADLE_VERSION) -> expectedExecutionTree =
        "-\n" +
        " -failed\n" +
        "  Unsupported Gradle"
      // sample assertion for deprecated Gradle version.
      currentGradleVersion < GradleVersion.version(GradleConstants.MINIMAL_RECOMMENDED_GRADLE_VERSION) -> expectedExecutionTree =
        "-\n" +
        " -finished\n" +
        "  Gradle ${currentGradleVersion.version} support can be dropped in the next release"
      else -> expectedExecutionTree = "-\n" +
                                      " finished"
    }

    assertSyncViewTreeEquals(expectedExecutionTree)
  }

  override fun assumeTestJavaRuntime(javaRuntimeVersion: JavaVersion) {
    // run on all Java Runtime
  }

  companion object {
    private val OLD_GRADLE_VERSIONS = arrayOf(
      arrayOf("0.9"), /*..., */arrayOf("0.9.2"),
      arrayOf("1.0"), /*arrayOf("1.1"), arrayOf("1.2"), ..., */arrayOf("1.12"),
      arrayOf("2.0"), /*arrayOf("2.1"), arrayOf("2.2"), ..., */arrayOf("2.5"), arrayOf("2.6"), arrayOf("2.14.1"))

    /**
     * Run the test against very old not-supported Gradle versions also
     */
    @Parameterized.Parameters(name = "with Gradle-{0}")
    @JvmStatic
    fun tests(): Array<out Array<String>> {
      return OLD_GRADLE_VERSIONS + VersionMatcherRule.SUPPORTED_GRADLE_VERSIONS
    }
  }
}
