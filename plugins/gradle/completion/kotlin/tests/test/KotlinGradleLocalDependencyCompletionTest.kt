// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.kotlin.tests.integration

import com.intellij.gradle.codeInsight.kotlin.testFramework.checkGradleScriptAutoCompletion
import com.intellij.gradle.codeInsight.kotlin.testFramework.checkGradleScriptCompletion
import com.intellij.gradle.codeInsight.kotlin.testFramework.fixtures.customConfigurationsGradleCodeInsightFixture
import com.intellij.gradle.completion.indexer.GradleLocalRepositoryIndexer
import com.intellij.gradle.completion.indexer.GradleLocalRepositoryIndexerTestImpl
import com.intellij.gradle.testFramework.annotations.BaseGradleVersionSource
import com.intellij.gradle.testFramework.fixtures.application.GradleImmutableProjectTestApplication
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass

@GradleImmutableProjectTestApplication
@ParameterizedClass
@BaseGradleVersionSource
class KotlinGradleLocalDependencyCompletionTest(gradleVersion: GradleVersion) {

  private val codeInsightFixture by customConfigurationsGradleCodeInsightFixture(gradleVersion)

  @AfterEach
  fun tearDown() {
    KotlinSdkType.removeKotlinSdkInTests()
  }

  private fun configureLocalIndex(vararg gavArgs: String) {
    application.replaceService(
      GradleLocalRepositoryIndexer::class.java,
      GradleLocalRepositoryIndexerTestImpl(LocalEelDescriptor, gavArgs.toList()),
      codeInsightFixture.testRootDisposable
    )
  }

  @Test
  fun `test no suggestions`() {
    configureLocalIndex(
      "org.jetbrains:annotations:13.0",
      "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      dependencies {
          implementation("org.jetbrains.kotlin:kotlin-something-weir<caret>")
      }
      """.trimIndent()
    )
  }

  @Test
  fun `test version suggestions after artifact`() {
    configureLocalIndex(
      "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.0.0",
      "com.google.guava:guava:33.5.0-jre",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      dependencies {
          implementation("org.jetbrains.kotlin:kotlin-reflect<caret>")
      }
      """.trimIndent(),
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.0.0",
    )
  }

  @Test
  fun `test version suggestions after a colon`() {
    configureLocalIndex(
      "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.0.0",
      "com.google.guava:guava:33.5.0-jre",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      dependencies {
          implementation("org.jetbrains.kotlin:kotlin-reflect:<caret>")
      }
      """.trimIndent(),
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.0.0",
    )
  }

  @Test
  fun `test version suggestions`() {
    configureLocalIndex(
      "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.0.0",
      "com.google.guava:guava:33.5.0-jre",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      dependencies {
          implementation("org.jetbrains.kotlin:kotlin-reflect:1<caret>")
      }
      """.trimIndent(),
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.0.0",
    )
  }

  @Test
  fun `test version suggestions after a dot`() {
    configureLocalIndex(
      "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.0.0",
      "com.google.guava:guava:33.5.0-jre",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      dependencies {
          implementation("org.jetbrains.kotlin:kotlin-reflect:1.<caret>")
      }
      """.trimIndent(),
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.0.0",
    )
  }

  @Test
  fun `test version auto completion`() {
    configureLocalIndex(
      "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.0.0",
      "com.google.guava:guava:33.5.0-jre",
    )
    codeInsightFixture.checkGradleScriptAutoCompletion(
      """
      dependencies {
          implementation("org.jetbrains.kotlin:kotlin-reflect:1.1<caret>")
      }
      """.trimIndent(),
      """
      dependencies {
          implementation("org.jetbrains.kotlin:kotlin-reflect:1.1.0")
      }
      """.trimIndent(),
    )
  }

  @Test
  fun `test custom configuration`() {
    configureLocalIndex(
      "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.0.0",
      "com.google.guava:guava:33.5.0-jre",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      dependencies {
          customConf("org.jetbrains.kotlin:kotlin-reflect:<caret>")
      }
      """.trimIndent(),
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.0.0",
    )
  }

  @Test
  fun `test custom string configuration`() {
    configureLocalIndex(
      "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.0.0",
      "com.google.guava:guava:33.5.0-jre",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      dependencies {
          "customConf"("org.jetbrains.kotlin:kotlin-reflect:<caret>")
      }
      """.trimIndent(),
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.0.0",
    )
  }

  @Test
  fun `test suggestions after org`() {
    configureLocalIndex(
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.0.0",
      "org.gradle:gradle-tooling-api:9.2.1",
      "org.gradle:gradle-tooling-api:9.2.0",
      "com.google.guava:guava:33.5.0-jre",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      dependencies {
          implementation("org.<caret>")
      }
      """.trimIndent(),
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.0.0",
      "org.gradle:gradle-tooling-api:9.2.1",
      "org.gradle:gradle-tooling-api:9.2.0",
    )
  }

  @Test
  fun `test extra artifact suggestions`() {
    configureLocalIndex(
      "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
      "org.jetbrains.kotlin:kotlin-stdlib:2.0.0",
      "org.jetbrains.kotlin:kotlin-stdlib:1.1.0",
      "org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.0",
      "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.1.0",
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "com.google.guava:guava:33.5.0-jre",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      dependencies {
          implementation("org.jetbrains.kotlin:kotlin-stdlib:2<caret>")
      }
      """.trimIndent(),
      "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
      "org.jetbrains.kotlin:kotlin-stdlib:2.0.0",
      "org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.0",
    )
  }

  @Test
  fun `test artifact suggestions caret after group`() {
    configureLocalIndex(
      "org.jetbrains:annotations:13.0",
      "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
      "org.jetbrains.kotlin:kotlin-stdlib:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
      "org.gradle:gradle-tooling-api:9.2.1",
      "com.google.guava:guava:33.5.0-jre",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      dependencies {
          implementation("org.jetbrains.kotlin<caret>")
      }
      """.trimIndent(),
      "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
      "org.jetbrains.kotlin:kotlin-stdlib:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
    )
  }

  @Test
  fun `test artifact suggestions caret after group colon`() {
    configureLocalIndex(
      "org.jetbrains:annotations:13.0",
      "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
      "org.jetbrains.kotlin:kotlin-stdlib:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
      "org.gradle:gradle-tooling-api:9.2.1",
      "com.google.guava:guava:33.5.0-jre",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      dependencies {
          implementation("org.jetbrains.kotlin:<caret>")
      }
      """.trimIndent(),
      "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
      "org.jetbrains.kotlin:kotlin-stdlib:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
    )
  }

  @Test
  fun `test artifact suggestions caret in artifact`() {
    configureLocalIndex(
      "org.jetbrains:annotations:13.0",
      "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
      "org.jetbrains.kotlin:kotlin-stdlib:2.0.0",
      "org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.0",
      "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.1.0",
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.gradle:gradle-tooling-api:9.2.1",
      "com.google.guava:guava:33.5.0-jre",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      dependencies {
          implementation("org.jetbrains.kotlin:kotlin-s<caret>")
      }
      """.trimIndent(),
      "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
      "org.jetbrains.kotlin:kotlin-stdlib:2.0.0",
      "org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.0",
      "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.1.0",
    )
  }

  @Test
  fun `test caret in empty string`() {
    configureLocalIndex(
      "org.jetbrains:annotations:13.0",
      "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
      "org.jetbrains.kotlin:kotlin-stdlib:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
      "org.gradle:gradle-tooling-api:9.2.1",
      "com.google.guava:guava:33.5.0-jre",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      dependencies {
          implementation("<caret>")
      }
      """.trimIndent(),
      "org.jetbrains:annotations:13.0",
      "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
      "org.jetbrains.kotlin:kotlin-stdlib:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
      "org.gradle:gradle-tooling-api:9.2.1",
      "com.google.guava:guava:33.5.0-jre",
    )
  }

  @Test
  fun `test single part being artifact`() {
    configureLocalIndex(
      "org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0",
      "org.jetbrains:gradle-plugin:2.3.0",
      "com.google.guava:guava:33.5.0-jre",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      dependencies {
          implementation("gradle<caret>")
      }
      """.trimIndent(),
      "org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0",
      "org.jetbrains:gradle-plugin:2.3.0",
    )
  }

  @Test
  fun `test single part being group`() {
    configureLocalIndex(
      "org.gradle:api:9.0.0",
      "gradle:artifact:version",
      "com.google.guava:guava:33.5.0-jre",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      dependencies {
          implementation("gradle<caret>")
      }
      """.trimIndent(),
      "org.gradle:api:9.0.0",
      "gradle:artifact:version",
    )
  }

  @Test
  fun `test single part being artifact or group`() {
    configureLocalIndex(
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.gradle:gradle-tooling-api:9.2.1",
      "org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0",
      "org.gradle:api:9.0.0",
      "com.google.guava:guava:33.5.0-jre",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      dependencies {
          implementation("gradle<caret>")
      }
      """.trimIndent(),
      "org.gradle:gradle-tooling-api:9.2.1",
      "org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0",
      "org.gradle:api:9.0.0",
    )
  }

  @Test
  fun `test each part uses contains`() {
    configureLocalIndex(
      "group:artifact:version",
      "prefix-groupsuffix:prefixartifact-suffix:prefix.version-suffix",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      dependencies {
          implementation("group:artifact:version<caret>")
      }
      """.trimIndent(),
      "group:artifact:version",
      "prefix-groupsuffix:prefixartifact-suffix:prefix.version-suffix",
    )
  }

  @Test
  fun `test no suggestions outside dependencies block`() {
    configureLocalIndex(
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      // no dependencies block here
      val x = "org.jetbrains.kotlin:kotlin-reflect:<caret>"
      """.trimIndent()
    )
  }

  @Test
  fun `test no suggestions in comments`() {
    configureLocalIndex(
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      dependencies {
          // implementation("org.jetbrains.kotlin:kotlin-reflect:<caret>")
      }
      """.trimIndent()
    )
  }

  @Test
  fun `test whitespace inside string is tolerated`() {
    configureLocalIndex(
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      dependencies {
          implementation( "org.jetbrains.kotlin:kotlin-reflect:<caret>" )
      }
      """.trimIndent(),
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
    )
  }

  @Test
  fun `test caret after a dash`() {
    configureLocalIndex(
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      dependencies {
          implementation("org.jetbrains.kotlin:kotlin-<caret>")
      }
      """.trimIndent(),
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
    )
  }

  @Test
  fun `test named dependencyNotation argument`() {
    configureLocalIndex(
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      dependencies {
          implementation(dependencyNotation = "org.jetbrains.kotlin:kotlin-r<caret>")
      }
      """.trimIndent(),
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
    )
  }

  // NAMED ARGUMENTS NOTATION

  @Test
  fun `test named arguments empty group with no name and no version`() {
    configureLocalIndex(
      "org.jetbrains:annotations:13.0",
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
      "org.gradle:gradle-tooling-api:9.2.1",
      "com.google.guava:guava:33.5.0-jre",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      dependencies {
          implementation(group = "<caret>")
      }
      """.trimIndent(),
      "org.jetbrains",
      "org.jetbrains.kotlin",
      "org.gradle",
      "com.google.guava",
    )
  }

  @Test
  fun `test named arguments partial group with no name and no version`() {
    configureLocalIndex(
      "org.jetbrains:annotations:13.0",
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
      "org.gradle:gradle-tooling-api:9.2.1",
      "com.google.guava:guava:33.5.0-jre",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      dependencies {
          implementation(group = "org.jetbrains<caret>")
      }
      """.trimIndent(),
      "org.jetbrains",
      "org.jetbrains.kotlin",
    )
  }

  @Test
  fun `test named arguments empty group with name and empty version`() {
    configureLocalIndex(
      "org.jetbrains:annotations:13.0",
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
      "org.gradle:gradle-tooling-api:9.2.1",
      "com.google.guava:guava:33.5.0-jre",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      dependencies {
          implementation(group = "<caret>", name = "kotlin-reflect", version = "")
      }
      """.trimIndent(),
      "org.jetbrains.kotlin",
    )
  }

  @Test
  fun `test named arguments partial group with name and empty version`() {
    configureLocalIndex(
      "org.jetbrains:annotations:13.0",
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
      "org.gradle:gradle-tooling-api:9.2.1",
      "com.google.guava:guava:33.5.0-jre",
    )
    codeInsightFixture.checkGradleScriptAutoCompletion(
      """
      dependencies {
          implementation(group = "org.jetbrains<caret>", name = "kotlin-reflect", version = "")
      }
      """.trimIndent(),
      """
      dependencies {
          implementation(group = "org.jetbrains.kotlin", name = "kotlin-reflect", version = "")
      }
      """.trimIndent(),
    )
  }

  @Test
  fun `test named arguments empty group with empty name and empty version`() {
    configureLocalIndex(
      "org.jetbrains:annotations:13.0",
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
      "org.gradle:gradle-tooling-api:9.2.1",
      "com.google.guava:guava:33.5.0-jre",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      dependencies {
          implementation(group = "<caret>", name = "", version = "")
      }
      """.trimIndent(),
      "org.jetbrains",
      "org.jetbrains.kotlin",
      "org.gradle",
      "com.google.guava",
    )
  }

  @Test
  fun `test named arguments empty group with name and no version`() {
    configureLocalIndex(
      "org.jetbrains:annotations:13.0",
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
      "com.google.guava:guava:33.5.0-jre",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      dependencies {
          implementation(group = "<caret>", name = "kotlin-reflect")
      }
      """.trimIndent(),
      "org.jetbrains.kotlin",
    )
  }

  @Test
  fun `test named arguments empty version with group and name`() {
    configureLocalIndex(
      "org.jetbrains:annotations:13.0",
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
      "com.google.guava:guava:33.5.0-jre",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      dependencies {
          implementation(group = "org.jetbrains.kotlin", name = "kotlin-reflect", version = "<caret>")
      }
      """.trimIndent(),
      "2.0.0",
      "1.1.0",
    )
  }

  @Test
  fun `test named arguments empty name with group and empty version`() {
    configureLocalIndex(
      "org.jetbrains:annotations:13.0",
      "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
      "com.google.guava:guava:33.5.0-jre",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      dependencies {
          implementation(group = "org.jetbrains.kotlin", name = "<caret>", version = "")
      }
      """.trimIndent(),
      "kotlin-stdlib",
      "kotlin-reflect",
    )
  }

  @Test
  fun `test named arguments partial name with group and empty version`() {
    configureLocalIndex(
      "org.jetbrains:annotations:13.0",
      "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
      "org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "com.google.guava:guava:33.5.0-jre",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      dependencies {
          implementation(group = "org.jetbrains.kotlin", name = "kotlin-s<caret>", version = "")
      }
      """.trimIndent(),
      "kotlin-stdlib",
      "kotlin-stdlib-jdk8",
    )
  }

  @Test
  fun `test out-of-order named arguments empty group with name and no version`() {
    configureLocalIndex(
      "org.jetbrains:annotations:13.0",
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
      "com.google.guava:guava:33.5.0-jre",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      dependencies {
          implementation(name = "kotlin-reflect", group = "<caret>")
      }
      """.trimIndent(),
      "org.jetbrains.kotlin",
    )
  }

  @Test
  fun `test out-of-order named arguments empty group with name and empty version`() {
    configureLocalIndex(
      "org.jetbrains:annotations:13.0",
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
      "com.google.guava:guava:33.5.0-jre",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      dependencies {
          implementation(name = "kotlin-reflect", group = "<caret>", version = "")
      }
      """.trimIndent(),
      "org.jetbrains.kotlin",
    )
  }

  @Test
  fun `test positional arguments empty group with name and empty version`() {
    configureLocalIndex(
      "org.jetbrains:annotations:13.0",
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
      "com.google.guava:guava:33.5.0-jre",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      dependencies {
          implementation("<caret>", "kotlin-reflect", "")
      }
      """.trimIndent(),
      "org.jetbrains.kotlin",
    )
  }

  @Test
  fun `test positional arguments empty name with group and no version`() {
    configureLocalIndex(
      "org.jetbrains:annotations:13.0",
      "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
      "com.google.guava:guava:33.5.0-jre",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      dependencies {
          implementation("org.jetbrains.kotlin", "<caret>")
      }
      """.trimIndent(),
      "kotlin-stdlib",
      "kotlin-reflect",
    )
  }

  @Test
  fun `test positional arguments empty name with group and empty version`() {
    configureLocalIndex(
      "org.jetbrains:annotations:13.0",
      "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
      "com.google.guava:guava:33.5.0-jre",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      dependencies {
          implementation("org.jetbrains.kotlin", "<caret>", "")
      }
      """.trimIndent(),
      "kotlin-stdlib",
      "kotlin-reflect",
    )
  }

  @Test
  fun `test positional arguments partial name with group and no version`() {
    configureLocalIndex(
      "org.jetbrains:annotations:13.0",
      "org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
      "org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
      "com.google.guava:guava:33.5.0-jre",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      dependencies {
          implementation("org.jetbrains.kotlin", "kotlin-s<caret>")
      }
      """.trimIndent(),
      "kotlin-stdlib",
      "kotlin-stdlib-jdk8",
    )
  }

  @Test
  fun `test positional arguments empty version with group and name`() {
    configureLocalIndex(
      "org.jetbrains:annotations:13.0",
      "org.jetbrains.kotlin:kotlin-reflect:2.0.0",
      "org.jetbrains.kotlin:kotlin-reflect:1.1.0",
      "com.google.guava:guava:33.5.0-jre",
    )
    codeInsightFixture.checkGradleScriptCompletion(
      """
      dependencies {
          implementation("org.jetbrains.kotlin", "kotlin-reflect", "<caret>")
      }
      """.trimIndent(),
      "2.0.0",
      "1.1.0",
    )
  }
}