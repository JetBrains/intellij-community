// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl.versionCatalogs

import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.refactoring.rename.RenameInputValidatorRegistry.getInputValidator
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.testFramework.utils.vfs.getPsiFile
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.dsl.versionCatalogs.GradleVersionCatalogFixtures.BASE_VERSION_CATALOG_FIXTURE
import org.jetbrains.plugins.gradle.dsl.versionCatalogs.GradleVersionCatalogFixtures.DYNAMICALLY_INCLUDED_SUBPROJECTS_FIXTURE
import org.jetbrains.plugins.gradle.dsl.versionCatalogs.GradleVersionCatalogFixtures.VERSION_CATALOG_COMPOSITE_BUILD_FIXTURE
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.params.ParameterizedTest

class GradleVersionCatalogsRenameTest : GradleCodeInsightTestCase() {

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test renaming a library in a TOML`(gradleVersion: GradleVersion) {
    test(gradleVersion, BASE_VERSION_CATALOG_FIXTURE) {
      writeTextAndCommit("build.gradle", "libs.aaa.bbb.ccc")
      writeTextAndCommit("gradle/libs.versions.toml", """
        [libraries]
        aaa-b<caret>bb_ccc = "a:b:10"
      """.trimIndent())
      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(getFile("gradle/libs.versions.toml"))
        val newName = "eee-fff_ggg"
        assertRenamingInputIsValid(newName)

        TestDialogManager.setTestDialog(TestDialog.OK)
        codeInsightFixture.renameElementAtCaret(newName)
        val uncommittedFile = getFile("build.gradle").getPsiFile(project)
        Assertions.assertEquals("libs.eee.fff.ggg", uncommittedFile.text)
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test renaming a version ref in a TOML`(gradleVersion: GradleVersion) {
    test(gradleVersion, BASE_VERSION_CATALOG_FIXTURE) {
      writeTextAndCommit("build.gradle", "")
      writeTextAndCommit("gradle/libs.versions.toml", """
        [versions]
        aa<caret>a = "13"
        [libraries]
        aaa-bbb_ccc = { group = "org.apache.groovy", name = "groovy", version.ref = "aaa" }
      """.trimIndent())
      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(getFile("gradle/libs.versions.toml"))
        val newName = "eee-fff_ggg"
        assertRenamingInputIsValid(newName)

        TestDialogManager.setTestDialog(TestDialog.OK)
        codeInsightFixture.renameElementAtCaret(newName)
        codeInsightFixture.checkResult("""
        [versions]
        eee-fff_ggg = "13"
        [libraries]
        aaa-bbb_ccc = { group = "org.apache.groovy", name = "groovy", version.ref = "eee-fff_ggg" }
      """.trimIndent())
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test renaming a library in a custom TOML, for dynamically added subproject`(gradleVersion: GradleVersion) {
    test(gradleVersion, DYNAMICALLY_INCLUDED_SUBPROJECTS_FIXTURE) {
      writeTextAndCommit("customPath/custom.toml", """
        [libraries]
        apache-gro<caret>ovy = { module = "org.apache.groovy:groovy", version = "4.0.0"
        """.trimIndent()
      )
      writeTextAndCommit("subprojectsDir/subproject1/build.gradle", "customLibs.apache.groovy")
      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(getFile("customPath/custom.toml"))
        val newName = "renamed-apache-groovy"
        assertRenamingInputIsValid(newName)

        TestDialogManager.setTestDialog(TestDialog.OK)
        codeInsightFixture.renameElementAtCaret(newName)
        val fileWithUsage = getFile("subprojectsDir/subproject1/build.gradle").getPsiFile(project)
        Assertions.assertEquals("customLibs.renamed.apache.groovy", fileWithUsage.text)
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test renaming a library in a TOML catalog of an included build`(gradleVersion: GradleVersion) {
    test(gradleVersion, VERSION_CATALOG_COMPOSITE_BUILD_FIXTURE) {
      writeTextAndCommit("includedBuild1/gradle/libs.versions.toml", """
        [libraries]
        apache-gro<caret>ovy = { module = "org.apache.groovy:groovy", version = "4.0.0"
        """.trimIndent()
      )
      writeTextAndCommit("includedBuild1/build.gradle", "libs.apache.groovy")
      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(getFile("includedBuild1/gradle/libs.versions.toml"))
        val newName = "renamed-apache-groovy"
        assertRenamingInputIsValid(newName)

        TestDialogManager.setTestDialog(TestDialog.OK)
        codeInsightFixture.renameElementAtCaret(newName)
        val fileWithUsage = getFile("includedBuild1/build.gradle").getPsiFile(project)
        Assertions.assertEquals("libs.renamed.apache.groovy", fileWithUsage.text)
      }
    }
  }

  private fun assertRenamingInputIsValid(newName: String) {
    val element = codeInsightFixture.elementAtCaret
    val inputValidator = getInputValidator(element).also {
      assertNotNull(it) { "Input validator for $element is not found." }
    }
    assertTrue(inputValidator!!.value(newName)) {
      "The validation for the input '$newName' should be passed."
    }
  }
}