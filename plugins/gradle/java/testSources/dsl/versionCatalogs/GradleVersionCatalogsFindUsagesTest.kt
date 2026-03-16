// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl.versionCatalogs

import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.testFramework.runInEdtAndWait
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.Unmodifiable
import org.jetbrains.plugins.gradle.dsl.versionCatalogs.GradleVersionCatalogFixtures.BASE_VERSION_CATALOG_FIXTURE
import org.jetbrains.plugins.gradle.dsl.versionCatalogs.GradleVersionCatalogFixtures.DYNAMICALLY_INCLUDED_SUBPROJECTS_FIXTURE
import org.jetbrains.plugins.gradle.dsl.versionCatalogs.GradleVersionCatalogFixtures.VERSION_CATALOG_COMPOSITE_BUILD_FIXTURE
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

  private fun testVersionCatalogFindUsages(
    version: GradleVersion,
    versionCatalogText: String,
    buildGradleText: String,
    buildScriptPath: String = "build.gradle",
    checker: (Collection<PsiReference>) -> Unit,
  ) {
    checkCaret(versionCatalogText)
    test(version, BASE_VERSION_CATALOG_FIXTURE) {
      writeTextAndCommit("gradle/libs.versions.toml", versionCatalogText)
      writeTextAndCommit(buildScriptPath, buildGradleText)
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
    testVersionCatalogFindUsages(
      gradleVersion,
      versionCatalogText = """
        [libraries]
        aaa-b<caret>bb = { group = "org.apache.groovy", name = "groovy", version = "4.0.2" }""".trimIndent(),
      buildGradleText = "libs.aaa.bbb",
      buildScriptPath = "subproject1/build.gradle"
    ) { usages ->
      runInEdtAndWait {
        assertContainsUsagesInFiles(usages, "subproject1/build.gradle")
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testNestedProjectOfIncludedBuild(gradleVersion: GradleVersion) {
    test(gradleVersion, VERSION_CATALOG_COMPOSITE_BUILD_FIXTURE) {
      writeTextAndCommit("includedBuild1/gradle/libs.versions.toml", /* language=TOML */ """
        [libraries]
        apache-gro<caret>ovy = { module = "org.apache.groovy:groovy", version = "4.0.0" }
        """.trimIndent()
      )
      writeTextAndCommit("includedBuild1/subproject1/build.gradle", "libs.apache.groovy")
      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(getFile("includedBuild1/gradle/libs.versions.toml"))
        val usages = ReferencesSearch.search(codeInsightFixture.elementAtCaret).findAll()
        assertContainsUsagesInFiles(usages, "includedBuild1/subproject1/build.gradle")
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