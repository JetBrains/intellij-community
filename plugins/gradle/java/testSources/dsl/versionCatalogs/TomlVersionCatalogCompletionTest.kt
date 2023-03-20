// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl.versionCatalogs

import com.intellij.testFramework.runInEdtAndWait
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest

class TomlVersionCatalogCompletionTest : GradleCodeInsightTestCase() {

  private fun testSuggestion(version: GradleVersion, versionCatalogText: String, vararg completionCandidates: String) {
    checkCaret(versionCatalogText)
    testEmptyProject(version) {
      writeTextAndCommit("gradle/libs.versions.toml", versionCatalogText)
      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(getFile("gradle/libs.versions.toml"))
        val suggestions = codeInsightFixture.completeBasic().map { it.lookupString }
        Assertions.assertArrayEquals(suggestions.toTypedArray(), completionCandidates)
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testCompletionInLibraries(gradleVersion: GradleVersion) {
    testSuggestion(gradleVersion, """
        [libraries]
        compat = { <caret>}""".trimIndent(), "group", "module", "name", "version")
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testCompletionInLibraries2(gradleVersion: GradleVersion) {
    testSuggestion(gradleVersion, """
        [libraries]
        compat = { module = "androidx.appcompat:appcompat", <caret> }
      """.trimIndent(), "version")
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testCompletionInPlugins(gradleVersion: GradleVersion) {
    testSuggestion(gradleVersion, """
        [plugins]
        plugin1 = {<caret>}
      """.trimIndent(), "id", "version")
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testCompletionInVersions(gradleVersion: GradleVersion) {
    testSuggestion(gradleVersion, """
        [versions]
        agp = {<caret>}

      """.trimIndent(),
                   "prefer", "reject", "rejectAll", "require", "strictly")
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testCompletionInLibraryVersions(gradleVersion: GradleVersion) {
    testSuggestion(gradleVersion, """
        [libraries]
        compat = { module = "androidx.appcompat:appcompat", version = {<caret>}
      """.trimIndent(), "prefer", "ref", "reject", "rejectAll", "require", "strictly")
  }


  @ParameterizedTest
  @BaseGradleVersionSource
  fun testCompletionInLibraryVersions2(gradleVersion: GradleVersion) {
    testSuggestion(gradleVersion, """
        [libraries]
        compat = { module = "androidx.appcompat:appcompat", version = { prefer ="1.2" <caret>}
      """.trimIndent(), "reject", "rejectAll", "require", "strictly")
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testCompletionInLibraryVersionsWithRequire(gradleVersion: GradleVersion) {
    testSuggestion(gradleVersion, """
        [libraries]
        compat = { module = "androidx.appcompat:appcompat", version = { require = "1.2" <caret>}
      """.trimIndent(), "prefer", "strictly")
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testCompletionVersionWithDot(gradleVersion: GradleVersion) {
    testSuggestion(gradleVersion, """
        [libraries]
        compat = { module = "androidx.appcompat:appcompat", version.<caret> }
      """.trimIndent(), "prefer", "ref", "reject", "rejectAll", "require", "strictly")
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testCompletionVersionWithDotAndOpenCurlyBraces(gradleVersion: GradleVersion) {
    testSuggestion(gradleVersion, """
        [libraries]
        compat = { module = "androidx.appcompat:appcompat", version.<caret>
      """.trimIndent(), "prefer", "ref", "reject", "rejectAll", "require", "strictly")
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testCompletionVersionWithDotComplexCase(gradleVersion: GradleVersion) {
    testSuggestion(gradleVersion, """
        [plugins]
        compat = { module = "androidx.appcompat:appcompat", version = { prefer = "3.9" }, version.strictly = "[3.8, 4.0[", version.<caret> }
      """.trimIndent(), "reject", "rejectAll", "require")
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testCompletionVersionWithDotAndSecondLevelDefined(gradleVersion: GradleVersion) {
    testSuggestion(gradleVersion, """
        [libraries]
        compat = { module = "androidx.appcompat:appcompat", version.ref.<caret> }
      """.trimIndent())
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testNoCompletionInFoo(gradleVersion: GradleVersion) {
    testSuggestion(gradleVersion, """
        [foo]
        compat = { module = "androidx.appcompat:appcompat", <caret> }
      """.trimIndent())
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testSectionName(gradleVersion: GradleVersion) {
    testSuggestion(gradleVersion, """
        [<caret>]
      """.trimIndent(), "bundles", "libraries", "plugins", "versions")
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testSectionNameWithFirstLetter(gradleVersion: GradleVersion) {
    testSuggestion(gradleVersion, """
        [u<caret>]
      """.trimIndent(), "bundles", "plugins")
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testSectionNameWithSomeExistingSections(gradleVersion: GradleVersion) {
    testSuggestion(gradleVersion, """
        [plugins]
        jmh = { id = "me.champeau.jmh", version = "0.6.5" }

        [<caret>]
      """.trimIndent(), "bundles", "libraries", "versions")
  }
}