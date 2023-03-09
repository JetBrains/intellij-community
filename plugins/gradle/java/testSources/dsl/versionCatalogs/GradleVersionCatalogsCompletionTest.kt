// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl.versionCatalogs

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.junit.jupiter.params.ParameterizedTest

class GradleVersionCatalogsCompletionTest : GradleCodeInsightTestCase() {

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testCompletionForVersionCatalogProperty(gradleVersion: GradleVersion) {
    test(gradleVersion, BASE_VERSION_CATALOG_FIXTURE) {
      testCompletion("li<caret>", "libs")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testCompletionForDependencies(gradleVersion: GradleVersion) {
    test(gradleVersion, BASE_VERSION_CATALOG_FIXTURE) {
      testCompletion("libs.<caret>", "commons", "groovy")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testCompletionForPlugins(gradleVersion: GradleVersion) {
    test(gradleVersion, BASE_VERSION_CATALOG_FIXTURE) {
      testCompletion("libs.plugins.<caret>", "jmh")
    }
  }

  //@ParameterizedTest
  @BaseGradleVersionSource
  fun testCompletionForBundles(gradleVersion: GradleVersion) {
    test(gradleVersion, BASE_VERSION_CATALOG_FIXTURE) {
      testCompletion("libs.bundles.<caret>", "groovy")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testCompletionForVersions(gradleVersion: GradleVersion) {
    test(gradleVersion, BASE_VERSION_CATALOG_FIXTURE) {
      testCompletion("libs.versions.<caret>", "checkstyle", "groovy")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testCompletionLong(gradleVersion: GradleVersion) {
    test(gradleVersion, JAVA_VERSION_CATALOG_FIXTURE) {
      testCompletion("""
        dependencies {
          implementation(libs.<caret>)
        }""".trimIndent(), "groovy.core", "groovy.json.foo")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testCompletionLong2(gradleVersion: GradleVersion) {
    test(gradleVersion, JAVA_VERSION_CATALOG_FIXTURE) {
      testCompletion("""
        dependencies {
          implementation(libs.groovy.<caret>)
        }""".trimIndent(), "core", "json.foo")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testCompletionLong3(gradleVersion: GradleVersion) {
    test(gradleVersion, JAVA_VERSION_CATALOG_FIXTURE) {
      testCompletion("""
        plugins {
          alias(libs.<caret>)
        }""".trimIndent(), "plugins.jmh.aa.bb")
    }
  }

  companion object {
    private val BASE_VERSION_CATALOG_FIXTURE = GradleTestFixtureBuilder
      .create("GradleVersionCatalogs-completion") {
        withSettingsFile {
          setProjectName("GradleVersionCatalogs-completion")
          enableFeaturePreview("VERSION_CATALOGS")
        }
        withFile("gradle/libs.versions.toml", /* language=TOML */ """
      [versions]
      groovy = "3.0.5"
      checkstyle = "8.37"

      [libraries]
      groovy-core = { module = "org.codehaus.groovy:groovy", version.ref = "groovy" }
      groovy-json = { module = "org.codehaus.groovy:groovy-json", version.ref = "groovy" }
      groovy-nio = { module = "org.codehaus.groovy:groovy-nio", version.ref = "groovy" }
      commons-lang3 = { group = "org.apache.commons", name = "commons-lang3", version = { strictly = "[3.8, 4.0[", prefer="3.9" } }

      [bundles]
      groovy = ["groovy-core", "groovy-json", "groovy-nio"]

      [plugins]
      jmh = { id = "me.champeau.jmh", version = "0.6.5" }
      """.trimIndent())
      }

    private val JAVA_VERSION_CATALOG_FIXTURE = GradleTestFixtureBuilder
      .create("GradleVersionCatalogs-completion-java") { gradleVersion ->
        withSettingsFile {
          setProjectName("GradleVersionCatalogs-completion-java")
          enableFeaturePreview("VERSION_CATALOGS")
        }
        withBuildFile(gradleVersion) {
          withJavaPlugin()
        }
        withFile("gradle/libs.versions.toml", /* language=TOML */ """
      [versions]
      groovy = "3.0.5"

      [libraries]
      groovy-core = { module = "org.codehaus.groovy:groovy", version.ref = "groovy" }
      groovy-json-foo = { module = "org.codehaus.groovy:groovy-json", version.ref = "groovy" }

      [bundles]
      groovy-aa-bb = ["groovy-core", "groovy-json-foo"]

      [plugins]
      jmh-aa-bb = { id = "me.champeau.jmh", version = "0.6.5" }
      """.trimIndent())
      }

  }

}