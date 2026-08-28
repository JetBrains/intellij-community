// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.codeInsight.kotlin.testFramework.fixtures

import com.intellij.gradle.testFramework.fixtures.gradleCodeInsightFixture
import com.intellij.gradle.testFramework.projectInfo.buildFile
import com.intellij.gradle.testFramework.projectInfo.file
import com.intellij.gradle.testFramework.projectInfo.simpleSettingsFile
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import org.gradle.util.GradleVersion


fun simpleGradleCodeInsightFixture(gradleVersion: GradleVersion): TestFixture<JavaCodeInsightTestFixture> =
  gradleCodeInsightFixture(gradleVersion, "kotlin-dsl-empty-project") {
    simpleSettingsFile()
    buildFile { }
  }

/**
 * Provides a code-insight fixture on a Kotlin JVM project with the custom configurations `customConf` and
 * `customExtendingImplementation`, and the custom source set `customSourceSet`.
 */
fun customConfigurationsGradleCodeInsightFixture(gradleVersion: GradleVersion): TestFixture<JavaCodeInsightTestFixture> =
  gradleCodeInsightFixture(gradleVersion, "with-custom-configurations") {
    simpleSettingsFile()
    buildFile {
      withKotlinJvmPlugin()
      withPrefix { code("val customConf by configurations.creating {}") }
      withPrefix { code("configurations.register(\"customExtendingImplementation\") { extendsFrom(configurations.implementation.get()) }") }
      withPrefix { code("val customSourceSet by sourceSets.creating {}") }
    }
  }

/**
 * Provides a code-insight fixture on a Kotlin JVM project with the custom configuration `customConf`, the custom
 * source set `customSourceSet`, and a version catalog with entries for the Kotlin plugin and the Kotlin stdlib.
 */
fun customConfigurationsAndVersionCatalogsGradleCodeInsightFixture(gradleVersion: GradleVersion): TestFixture<JavaCodeInsightTestFixture> =
  gradleCodeInsightFixture(gradleVersion, "with-cstm-configs-and-ver-catalogs") {
    simpleSettingsFile()
    buildFile {
      withKotlinJvmPlugin()
      withPrefix { code("val customConf by configurations.creating {}") }
      withPrefix { code("val customSourceSet by sourceSets.creating {}") }
    }
    file(
      "gradle/libs.versions.toml", /* language=TOML */ """
      [versions]
      kotlin = "2.2.0"
      [plugins]
      kotlinJvm = "org.jetbrains.kotlin.jvm:2.2.0"
      kotlinJvmFull = { id = "org.jetbrains.kotlin.jvm", version = "2.2.0" }
      kotlinJvmFullRef = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
      [libraries]
      kotlin-std-lib-simple = "org.jetbrains.kotlin:kotlin-stdlib:2.2.0"
      kotlin-std-lib-noVersion.module = "org.jetbrains.kotlin:kotlin-stdlib"
      kotlin-std-lib-moduleVersion = { module = "org.jetbrains.kotlin:kotlin-stdlib", version = "2.2.0" }
      kotlin-std-lib-moduleVersionRef = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }
      kotlin-std-lib-groupNameVersion = { group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "2.2.0" }
      kotlin-std-lib-groupNameVersionRef = { group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version.ref = "kotlin" }
      kotlin-std-lib-multiline = ""${'"'}org.jetbrains.kotlin
      :kotlin-stdlib
      :2.2.0
      ""${'"'}
      """.trimIndent()
    )
  }
