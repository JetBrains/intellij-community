// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue.quickfix

import com.intellij.lang.properties.psi.PropertiesElementFactory
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.issue.quickfix.GradleVersionQuickFix.Companion.replaceDistributionUrlVersion
import org.jetbrains.plugins.gradle.issue.quickfix.GradleVersionQuickFix.Companion.updateGradleWrapperVersion
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Unit tests for the helper methods in [GradleVersionQuickFix].
 */
class GradleVersionQuickFixUnitTest {

  private val targetVersion = GradleVersion.version("8.9")

  @ParameterizedTest(name = "[{index}] {0} -> {1}")
  @CsvSource(
    nullValues = ["null"],
    textBlock = """
      'https://services.gradle.org/distributions/gradle-8.5-bin.zip',        'https://services.gradle.org/distributions/gradle-8.9-bin.zip'
      'https://mirror.corp/dists/gradle-8.5-all.zip',                        'https://mirror.corp/dists/gradle-8.9-all.zip'
      'https\://services.gradle.org/distributions/gradle-8.5-bin.zip',       'https\://services.gradle.org/distributions/gradle-8.9-bin.zip'
      'https://services.gradle.org/distributions/gradle-8.5-rc-1-bin.zip',   'https://services.gradle.org/distributions/gradle-8.9-bin.zip'
      'https://services.gradle.org/distributions/gradle-bin.zip',            null
      'https://example.com/custom-gradle.zip',                               null"""
  )
  fun `test replacing distribution url version`(distributionUrl: String, expected: String?) {
    assertThat(replaceDistributionUrlVersion(distributionUrl, targetVersion)).isEqualTo(expected)
  }

  @Nested
  @TestApplication
  inner class UpdateGradleWrapperVersionTest {
    private val projectFixture = projectFixture()
    private val project by projectFixture

    @Test
    fun `bump replaces only the version segment of the distributionUrl`() {
      val updated = update("""distributionUrl=https\://services.gradle.org/distributions/gradle-8.5-bin.zip""")
      assertThat(updated).isEqualTo("""distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip""")
    }

    @Test
    fun `bump preserves the all distribution variant`() {
      val updated = update("""distributionUrl=https\://services.gradle.org/distributions/gradle-8.5-all.zip""")
      assertThat(updated).isEqualTo("""distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-all.zip""")
    }

    @Test
    fun `bump keeps surrounding lines and order intact`() {
      val input = """
        distributionBase=GRADLE_USER_HOME
        distributionPath=wrapper/dists
        distributionUrl=https\://services.gradle.org/distributions/gradle-8.5-bin.zip
        zipStoreBase=GRADLE_USER_HOME
        zipStorePath=wrapper/dists
      """.trimIndent()
      val expected = """
        distributionBase=GRADLE_USER_HOME
        distributionPath=wrapper/dists
        distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
        zipStoreBase=GRADLE_USER_HOME
        zipStorePath=wrapper/dists
      """.trimIndent()
      assertThat(update(input)).isEqualTo(expected)
    }

    @Test
    fun `bump appends a distributionUrl when none is present`() {
      val updated = update("distributionBase=GRADLE_USER_HOME\n")
      assertThat(updated).contains("distributionBase=GRADLE_USER_HOME")
      assertThat(updated).contains("distributionUrl=")
      assertThat(updated).contains("gradle-8.9-bin.zip")
    }

    @Test
    fun `bump leaves an unparseable distributionUrl untouched`() {
      val input = """distributionUrl=https\://example.com/custom-distribution.zip"""
      assertThat(update(input)).isEqualTo(input)
    }

    @Test
    fun `bump drops the distributionSha256Sum property`() {
      val input = """
        distributionUrl=https\://services.gradle.org/distributions/gradle-8.5-bin.zip
        distributionSha256Sum=abc123
        networkTimeout=10000
      """.trimIndent()
      val expected = """
        distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
        networkTimeout=10000
      """.trimIndent()
      assertThat(update(input)).isEqualTo(expected)
    }

    @Test
    fun `bump drops the distributionSha256Sum property regardless of whitespace around the key`() {
      val input = """
        distributionUrl=https\://services.gradle.org/distributions/gradle-8.5-bin.zip
        distributionSha256Sum = abc123
        networkTimeout=10000
      """.trimIndent()
      val expected = """
        distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
        networkTimeout=10000
      """.trimIndent()
      assertThat(update(input)).isEqualTo(expected)
    }

    @Test
    fun `bump keeps comments that mention a version-specific key`() {
      val input = """
        distributionUrl=https\://services.gradle.org/distributions/gradle-8.5-bin.zip
        # distributionSha256Sum is intentionally omitted
        networkTimeout=10000
      """.trimIndent()
      val expected = """
        distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
        # distributionSha256Sum is intentionally omitted
        networkTimeout=10000
      """.trimIndent()
      assertThat(update(input)).isEqualTo(expected)
    }

    /**
     * Applies [updateGradleWrapperVersion] to an in-memory `gradle-wrapper.properties` file with the given [content]
     * and returns the resulting file text.
     */
    private fun update(content: String, version: GradleVersion = targetVersion): String = invokeAndWaitIfNeeded {
      val propertiesFile = PropertiesElementFactory.createPropertiesFile(project, content)
      WriteCommandAction.runWriteCommandAction(project) {
        updateGradleWrapperVersion(propertiesFile, version)
      }
      propertiesFile.text
    }
  }
}
