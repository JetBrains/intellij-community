// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl.versionCatalogs

import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.psi.PsiManager
import com.intellij.testFramework.runInEdtAndWait
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest

class GradleVersionCatalogsRenameTest : GradleCodeInsightTestCase() {

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testRenameLibrary(gradleVersion: GradleVersion) {
    test(gradleVersion, BASE_VERSION_CATALOG_FIXTURE) {
      val buildGradle = findOrCreateFile("build.gradle", "libs.aaa.bbb.ccc")
      val libsVersionsToml = findOrCreateFile("gradle/libs.versions.toml", """
        [libraries]
        aaa-b<caret>bb_ccc = "a:b:10"
      """.trimIndent())
      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(libsVersionsToml)
        TestDialogManager.setTestDialog(TestDialog.OK)
        codeInsightFixture.renameElementAtCaret("eee-fff_ggg")
        val uncommittedFile = PsiManager.getInstance(codeInsightFixture.project).findFile(buildGradle)!!
        Assertions.assertEquals("libs.eee.fff.ggg", uncommittedFile.text)
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testRenameVersionRef(gradleVersion: GradleVersion) {
    test(gradleVersion, BASE_VERSION_CATALOG_FIXTURE) {
      findOrCreateFile("build.gradle", "")
      val libsVersionsToml = findOrCreateFile("gradle/libs.versions.toml", """
        [versions]
        aa<caret>a = "13"
        [libraries]
        aaa-bbb_ccc = { group = "org.apache.groovy", name = "groovy", version.ref = "aaa" }
      """.trimIndent())
      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(libsVersionsToml)
        TestDialogManager.setTestDialog(TestDialog.OK)
        codeInsightFixture.renameElementAtCaret("eee-fff_ggg")
        codeInsightFixture.checkResult("""
        [versions]
        eee-fff_ggg = "13"
        [libraries]
        aaa-bbb_ccc = { group = "org.apache.groovy", name = "groovy", version.ref = "eee-fff_ggg" }
      """.trimIndent())
      }
    }
  }
}

private val BASE_VERSION_CATALOG_FIXTURE = GradleTestFixtureBuilder
  .create("GradleVersionCatalogs-refactoring") {
    withSettingsFile {
      setProjectName("GradleVersionCatalogs-refactoring")
      enableFeaturePreview("VERSION_CATALOGS")
    }
  }