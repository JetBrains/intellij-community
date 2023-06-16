// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl.versionCatalogs

import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.testFramework.utils.vfs.getPsiFile
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest

class GradleVersionCatalogsRenameTest : GradleCodeInsightTestCase() {

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testRenameLibrary(gradleVersion: GradleVersion) {
    testEmptyProject(gradleVersion) {
      writeTextAndCommit("build.gradle", "libs.aaa.bbb.ccc")
      writeTextAndCommit("gradle/libs.versions.toml", """
        [libraries]
        aaa-b<caret>bb_ccc = "a:b:10"
      """.trimIndent())
      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(getFile("gradle/libs.versions.toml"))
        TestDialogManager.setTestDialog(TestDialog.OK)
        codeInsightFixture.renameElementAtCaret("eee-fff_ggg")
        val uncommittedFile = getFile("build.gradle").getPsiFile(project)
        Assertions.assertEquals("libs.eee.fff.ggg", uncommittedFile.text)
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testRenameVersionRef(gradleVersion: GradleVersion) {
    testEmptyProject(gradleVersion) {
      writeTextAndCommit("build.gradle", "")
      writeTextAndCommit("gradle/libs.versions.toml", """
        [versions]
        aa<caret>a = "13"
        [libraries]
        aaa-bbb_ccc = { group = "org.apache.groovy", name = "groovy", version.ref = "aaa" }
      """.trimIndent())
      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(getFile("gradle/libs.versions.toml"))
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