// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl.versionCatalogs

import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.testFramework.runInEdtAndWait
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.params.ParameterizedTest
import org.toml.lang.psi.TomlFile

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
    """.trimIndent()) {
      assert(it.isNotEmpty())
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
    """.trimIndent()) {
      assert(it.isEmpty())
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
    """.trimIndent()) { refs ->
      assertNotNull(refs.find { it.element.containingFile is GroovyFileBase })
      assertNotNull(refs.find { it.element.containingFile is TomlFile })
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
        val elementAtCaret = codeInsightFixture.elementAtCaret
        assertNotNull(elementAtCaret)
        val usages = ReferencesSearch.search(elementAtCaret).findAll()
        assertNotNull(usages.find { it.element.containingFile is GroovyFileBase })
      }
    }
  }
}