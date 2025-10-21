// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl.versionCatalogs

import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.testFramework.runInEdtAndWait
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.Unmodifiable
import org.jetbrains.plugins.gradle.dsl.versionCatalogs.GradleVersionCatalogFixtures.DYNAMICALLY_INCLUDED_SUBPROJECTS_FIXTURE
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest

/**
 * Currently, this test does not trigger Gradle sync. So, version catalogs are determined relying on settings.gradle parsing.
 * If Gradle sync would be done, version catalog locations would be determined by GradleVersionCatalogEntity, willed with data from sync.
*/
class GradleVersionCatalogsFindUsagesTest : GradleCodeInsightTestCase() {

  private fun testVersionCatalogFindUsages(version: GradleVersion, versionCatalogText: String, buildGradleText: String,
                                   checker: (Collection<PsiReference>) -> Unit) {
    checkCaret(versionCatalogText)
    testEmptyProject(version) {
      writeTextAndCommit("gradle/libs.versions.toml", versionCatalogText)
      writeTextAndCommit("build.gradle", buildGradleText)
      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(getFile("gradle/libs.versions.toml"))
        val elementAtCaret = codeInsightFixture.elementAtCaret
        assertNotNull(elementAtCaret)
        val usages = ReferencesSearch.search(elementAtCaret).findAll()
        checker(usages)
      }
    }

  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testHasUsages(gradleVersion: GradleVersion) {
    testVersionCatalogFindUsages(gradleVersion, """
      [libraries]
      groov<caret>y-core = "org.codehaus.groovy:groovy:2.7.3"
    """.trimIndent(), """
      libs.groovy.core
    """.trimIndent()) { usages ->
      assert(usages.isNotEmpty())
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testHasNoUsages(gradleVersion: GradleVersion) {
    testVersionCatalogFindUsages(gradleVersion, """
      [versions]
      foo = "4.7.6"
      [libraries]
      groov<caret>y-core = "org.codehaus.groovy:groovy:2.7.3"
      aaa-bbb = { group = "org.apache.groovy", name = "groovy", version.ref = "groovy" }
    """.trimIndent(), """
      libs.groovy
    """.trimIndent()) { usages ->
      assert(usages.isEmpty())
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testFindUsagesOfVersion(gradleVersion: GradleVersion) {
    testVersionCatalogFindUsages(gradleVersion, """
      [versions]
      fo<caret>o = "4.7.6"
      [libraries]
      aaa-bbb = { group = "org.apache.groovy", name = "groovy", version.ref = "foo" }
    """.trimIndent(), """
      libs.versions.foo
    """.trimIndent()) { usages ->
      assertContainsUsagesInFiles(usages, "build.gradle", "gradle/libs.versions.toml")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testNestedProject(gradleVersion: GradleVersion) {
    testEmptyProject(gradleVersion) {
      writeTextAndCommit("gradle/libs.versions.toml", """
        [libraries]
        aaa-b<caret>bb = { group = "org.apache.groovy", name = "groovy", version = "4.0.2" }
      """.trimIndent())
      writeTextAndCommit("settings.gradle", """
        rootProject.name = 'empty-project'
        include 'app'
      """.trimIndent())
      writeTextAndCommit("build.gradle", "")
      writeTextAndCommit("app/build.gradle", "libs.aaa.bbb")
      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(getFile("gradle/libs.versions.toml"))
        val usages = ReferencesSearch.search(codeInsightFixture.elementAtCaret).findAll()
        assertContainsUsagesInFiles(usages, "app/build.gradle")
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testDynamicallyAddedSubprojectAndCustomToml(gradleVersion: GradleVersion) {
    test(gradleVersion, DYNAMICALLY_INCLUDED_SUBPROJECTS_FIXTURE) {
      writeTextAndCommit("customPath/custom.toml", /* language=TOML */ """
        [libraries]
        apache-gro<caret>ovy = { module = "org.apache.groovy:groovy", version = "4.0.0" }
        """.trimIndent()
      )
      writeTextAndCommit("subprojectsDir/subproject1/build.gradle", "customLibs.apache.groovy")
      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(getFile("customPath/custom.toml"))
        val usages = ReferencesSearch.search(codeInsightFixture.elementAtCaret).findAll()
        assertContainsUsagesInFiles(usages, "subprojectsDir/subproject1/build.gradle")
      }
    }
  }

  private fun assertContainsUsagesInFiles(usages: @Unmodifiable Collection<PsiReference>, vararg usagePathEndings: String) {
    val usagesInFiles = usages.map { it.element.containingFile.virtualFile.toNioPath() }
    for (usagePathEnd in usagePathEndings) {
      assertTrue(usagesInFiles.any { it.endsWith(usagePathEnd) }) {
        "Expected usage in $usagePathEnd file is not found."
      }
    }
  }
}