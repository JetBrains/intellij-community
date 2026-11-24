// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.kotlin.tests.integration

import com.intellij.gradle.codeInsight.kotlin.testFramework.completeGradleScript
import com.intellij.gradle.codeInsight.kotlin.testFramework.fixtures.versionCatalogsGradleCodeInsightFixture
import com.intellij.gradle.testFramework.annotations.BaseGradleVersionSource
import com.intellij.gradle.testFramework.fixtures.application.GradleImmutableProjectTestApplication
import org.assertj.core.api.Assertions.assertThat
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass


@GradleImmutableProjectTestApplication
@ParameterizedClass
@BaseGradleVersionSource
internal class KotlinGradlePluginsCompletionTest(gradleVersion: GradleVersion) {

  private val codeInsightFixture by versionCatalogsGradleCodeInsightFixture(gradleVersion)

  @AfterEach
  fun tearDown() {
    KotlinSdkType.removeKotlinSdkInTests()
  }

  @Test
  fun `test completion for an empty input`() {
    // TODO IDEA-384698 adjust sorting: catalog names should be on top
    codeInsightFixture.writeVersionCatalogs(
      libs = """
        [plugins]
        my-plugin-aaa = "my.plugin.aaa:1.0.0"
      """.trimIndent(),
      customLibs = """
        [plugins]
        my-plugin-bbb = "my.plugin.bbb:1.0.0"
      """.trimIndent(),
    )
    codeInsightFixture.completeGradleScript(
      """
      plugins {
          alias(<caret>)
      }
      """.trimIndent()
    ) {
      assertThat(suggestions()).startsWith("customLibs", "customLibs.plugins.my.plugin.bbb", "libs", "libs.plugins.my.plugin.aaa")
    }
  }

  @Test
  fun `test catalog name completion`() {
    // TODO IDEA-384698 adjust sorting: catalog names should be on top
    codeInsightFixture.writeVersionCatalogs(
      libs = """
        [plugins]
        my-plugin-aaa = "my.plugin.aaa:1.0.0"
      """.trimIndent(),
      customLibs = """
        [plugins]
        my-plugin-bbb = "my.plugin.bbb:1.0.0"
      """.trimIndent(),
    )
    codeInsightFixture.completeGradleScript(
      """
      plugins {
          alias(lib<caret>)
      }
      """.trimIndent()
    ) {
      assertThat(suggestions()).startsWith("libs", "libs.plugins.my.plugin.aaa", "customLibs", "customLibs.plugins.my.plugin.bbb")
      applySuggestion(2)
      checkResult(
        """
        plugins {
            alias(customLibs<caret>)
        }
        """.trimIndent()
      )
    }
  }

  @Test
  fun `test plugin entry completion`() {
    codeInsightFixture.writeVersionCatalogs(
      libs = """
        [plugins]
        my-plugin-aaa = "my.plugin.aaa:1.0.0"
        my-plugin-bbb = "my.plugin.bbb:1.0.0"
      """.trimIndent(),
    )
    codeInsightFixture.completeGradleScript(
      """
      plugins {
          alias(libs.plugins.m<caret>y.plug)
      }
      """.trimIndent()
    ) {
      assertThat(suggestions()).startsWith("my.plugin.aaa", "my.plugin.bbb")
      applySuggestion(1)
      checkResult(
        """
        plugins {
            alias(libs.plugins.my.plugin.bbb<caret>)
        }
        """.trimIndent()
      )
    }
  }

  @Test
  fun `test plugin entry completion for a custom catalog`() {
    codeInsightFixture.writeVersionCatalogs(
      customLibs = """
        [plugins]
        my-plugin-aaa = "my.plugin.aaa:1.0.0"
        my-plugin-bbb = "my.plugin.bbb:1.0.0"
      """.trimIndent(),
    )
    codeInsightFixture.completeGradleScript(
      """
      plugins {
          alias(customLibs.plugins.m<caret>y.plug)
      }
      """.trimIndent()
    ) {
      assertThat(suggestions()).startsWith("my.plugin.aaa", "my.plugin.bbb")
      applySuggestion(1)
      checkResult(
        """
        plugins {
            alias(customLibs.plugins.my.plugin.bbb<caret>)
        }
        """.trimIndent()
      )
    }
  }

  @Test
  fun `test plugin entry completion when plugins section is not specified`() {
    codeInsightFixture.writeVersionCatalogs(
      libs = """
        [plugins]
        my-plugin-aaa = "my.plugin.aaa:1.0.0"
        my-plugin-bbb = "my.plugin.bbb:1.0.0"
      """.trimIndent(),
    )
    codeInsightFixture.completeGradleScript(
      """
      plugins {
          alias(libs.m<caret>y.plu)
      }
      """.trimIndent()
    ) {
      assertThat(suggestions()).startsWith("plugins.my.plugin.aaa", "plugins.my.plugin.bbb")
      applySuggestion(1)
      checkResult(
        """
        plugins {
            alias(libs.plugins.my.plugin.bbb<caret>)
        }
        """.trimIndent()
      )
    }
  }

  @Test
  fun `test plugin entry completion from multiple catalogs when a catalog name is not specified`() {
    codeInsightFixture.writeVersionCatalogs(
      libs = """
        [plugins]
        my-plugin-aaa = "my.plugin.aaa:1.0.0"
      """.trimIndent(),
      customLibs = """
        [plugins]
        my-plugin-bbb = "my.plugin.bbb:1.0.0"
      """.trimIndent(),
    )
    codeInsightFixture.completeGradleScript(
      """
      plugins {
          alias(my<caret>.plug)
      }
      """.trimIndent()
    ) {
      assertThat(suggestions()).startsWith("customLibs.plugins.my.plugin.bbb", "libs.plugins.my.plugin.aaa")
      applySuggestion(0)
      checkResult(
        """
        plugins {
            alias(customLibs.plugins.my.plugin.bbb<caret>)
        }
        """.trimIndent()
      )
    }
  }

  @Test
  fun `test libraries, versions and bundles are not completed when the section is not specified`() {
    codeInsightFixture.writeVersionCatalogs(
      libs = """
        [versions]
        my-version = "1.0.0"

        [libraries]
        my-library = "com.example:my-library:1.0.0"

        [bundles]
        my-bundle = ["my-library"]
      """.trimIndent(),
    )
    codeInsightFixture.completeGradleScript(
      """
      plugins {
          alias(libs.my<caret>)
      }
      """.trimIndent()
    ) { lookupElements ->
      assertThat(lookupElements).isNullOrEmpty()
    }
  }

  @Test
  fun `test versions are completed when the section is specified`() {
    codeInsightFixture.writeVersionCatalogs(
      libs = """
        [versions]
        my-version-aaa = "1.0.0"
        my-version-bbb = "2.0.0"
      """.trimIndent(),
    )
    codeInsightFixture.completeGradleScript(
      """
      plugins {
          libs.versions.my<caret>
      }
      """.trimIndent()
    ) {
      assertThat(suggestions()).startsWith("my.version.aaa", "my.version.bbb")
    }
  }
}
