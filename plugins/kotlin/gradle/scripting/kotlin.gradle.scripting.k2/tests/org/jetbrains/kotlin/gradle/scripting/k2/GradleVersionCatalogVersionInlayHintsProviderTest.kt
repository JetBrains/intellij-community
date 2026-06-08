// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2

import com.intellij.codeInsight.hints.declarative.InlayProviderPassInfo
import com.intellij.codeInsight.hints.declarative.impl.DeclarativeInlayHintsPass
import com.intellij.codeInsight.hints.declarative.impl.util.DeclarativeHintsDumpUtil
import com.intellij.codeInsight.hints.declarative.impl.views.TextInlayPresentationEntry
import com.intellij.codeInsight.multiverse.codeInsightContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.runInEdtAndWait
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.AbstractGradleCodeInsightTest
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest


@GradleProjectTestApplication
internal class GradleVersionCatalogVersionInlayHintsProviderTest : AbstractGradleCodeInsightTest() {

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test inlay hint shown for catalog library dependency`(gradleVersion: GradleVersion) {
    test(gradleVersion, GRADLE_VERSION_CATALOGS_FIXTURE) {
      writeTextAndCommit("gradle/libs.versions.toml", """
        [libraries]
        my-lib = "com.example:my-lib:1.0.0"
      """.trimIndent())

      val buildFileContent = """
        dependencies {
            implementation(libs.my.lib)
        }
      """.trimIndent()
      val buildFile = writeTextAndCommit("build.gradle.kts", buildFileContent)

      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(buildFile)
        val dump = collectVersionCatalogHints(buildFileContent)
        assertEquals("""
          dependencies {
              implementation(libs.my.lib)/*<# com.example:my-lib:1.0.0 #>*/
          }
        """.trimIndent(), dump)
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test inlay hint shown for multi-segment catalog accessor`(gradleVersion: GradleVersion) {
    test(gradleVersion, GRADLE_VERSION_CATALOGS_FIXTURE) {
      writeTextAndCommit("gradle/libs.versions.toml", """
        [libraries]
        my-multi-segment = "io.ktor:server-html-builder:3.5.1"
      """.trimIndent())

      val buildFileContent = """
        dependencies {
            implementation(libs.my.multi.segment)
        }
      """.trimIndent()
      val buildFile = writeTextAndCommit("build.gradle.kts", buildFileContent)

      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(buildFile)
        val dump = collectVersionCatalogHints(buildFileContent)
        assertEquals("""
          dependencies {
              implementation(libs.my.multi.segment)/*<# io.ktor:server-html-builder:3.5.1 #>*/
          }
        """.trimIndent(), dump)
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test no inlay hint for plain string dependency`(gradleVersion: GradleVersion) {
    test(gradleVersion, GRADLE_VERSION_CATALOGS_FIXTURE) {
      val buildFileContent = """
        dependencies {
            implementation("com.example:my-lib:1.0.0")
        }
      """.trimIndent()
      val buildFile = writeTextAndCommit("build.gradle.kts", buildFileContent)

      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(buildFile)
        val dump = collectVersionCatalogHints(buildFileContent)
        assertEquals(buildFileContent, dump)
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test inlay hint shown for catalog plugin`(gradleVersion: GradleVersion) {
    test(gradleVersion, GRADLE_VERSION_CATALOGS_FIXTURE) {
      writeTextAndCommit("gradle/libs.versions.toml", """
        [plugins]
        my-plugin = { id = "com.example.plugin", version = "1.0" }
      """.trimIndent())

      val buildFileContent = """
        plugins {
            alias(libs.plugins.my.plugin)
        }
      """.trimIndent()
      val buildFile = writeTextAndCommit("build.gradle.kts", buildFileContent)

      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(buildFile)
        val dump = collectVersionCatalogHints(buildFileContent)
        assertEquals("""
          plugins {
              alias(libs.plugins.my.plugin)/*<# com.example.plugin:1.0 #>*/
          }
        """.trimIndent(), dump)
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test inlay hint shown for library without version`(gradleVersion: GradleVersion) {
    test(gradleVersion, GRADLE_VERSION_CATALOGS_FIXTURE) {
      writeTextAndCommit("gradle/libs.versions.toml", """
        [libraries]
        my-bom = { group = "com.example", name = "my-bom" }
      """.trimIndent())

      val buildFileContent = """
        dependencies {
            implementation(platform(libs.my.bom))
        }
      """.trimIndent()
      val buildFile = writeTextAndCommit("build.gradle.kts", buildFileContent)

      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(buildFile)
        val dump = collectVersionCatalogHints(buildFileContent)
        assertEquals("""
          dependencies {
              implementation(platform(libs.my.bom))/*<# com.example:my-bom #>*/
          }
        """.trimIndent(), dump)
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test multiple inlay hints in same file`(gradleVersion: GradleVersion) {
    test(gradleVersion, GRADLE_VERSION_CATALOGS_FIXTURE) {
      writeTextAndCommit("gradle/libs.versions.toml", """
        [libraries]
        first-lib = "com.example:first:1.0.0"
        second-lib = "com.example:second:2.0.0"
      """.trimIndent())

      val buildFileContent = """
        dependencies {
            implementation(libs.first.lib)
            testImplementation(libs.second.lib)
        }
      """.trimIndent()
      val buildFile = writeTextAndCommit("build.gradle.kts", buildFileContent)

      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(buildFile)
        val dump = collectVersionCatalogHints(buildFileContent)
        assertEquals("""
          dependencies {
              implementation(libs.first.lib)/*<# com.example:first:1.0.0 #>*/
              testImplementation(libs.second.lib)/*<# com.example:second:2.0.0 #>*/
          }
        """.trimIndent(), dump)
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test no inlay hint for missing catalog entry`(gradleVersion: GradleVersion) {
    test(gradleVersion, GRADLE_VERSION_CATALOGS_FIXTURE) {
      writeTextAndCommit("gradle/libs.versions.toml", """
        [libraries]
        existing-lib = "com.example:existing:1.0.0"
      """.trimIndent())

      val buildFileContent = """
        dependencies {
            implementation(libs.nonExistent)
        }
      """.trimIndent()
      val buildFile = writeTextAndCommit("build.gradle.kts", buildFileContent)

      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(buildFile)
        val dump = collectVersionCatalogHints(buildFileContent)
        assertEquals(buildFileContent, dump)
      }
    }
  }

  private fun collectVersionCatalogHints(sourceText: String): String {
    val file = codeInsightFixture.file
    val editor = codeInsightFixture.editor
    val provider = GradleVersionCatalogVersionInlayHintsProvider()
    val providerInfo = InlayProviderPassInfo(provider, "gradle.version.catalog.version", emptyMap())
    val pass = ActionUtil.underModalProgress(project, "") {
      DeclarativeInlayHintsPass(file, editor, listOf(providerInfo), isPreview = false)
    }
    pass.setContext(file.codeInsightContext)
    ActionUtil.underModalProgress(project, "") {
      pass.doCollectInformation(EmptyProgressIndicator())
    }
    pass.applyInformationToEditor()
    return DeclarativeHintsDumpUtil.dumpHints(sourceText, editor = editor) { presentationList ->
      presentationList.getEntries()
        .joinToString(separator = "") { (it as TextInlayPresentationEntry).text }
    }
  }
}
