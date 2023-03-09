// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl

import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.testFramework.runInEdtAndWait
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest

class GradleFindUsagesTest: GradleCodeInsightTestCase() {

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testCompletionForVersionCatalogProperty(gradleVersion: GradleVersion) {
    test(gradleVersion, VERSION_CATALOG_FIXTURE) {
      val file = findOrCreateFile("build.gradle", """
        plugins {
          id 'java'
        }
        
        dependencies {
          implementation libs.groo<caret>vy.core
        }
      """.trimIndent())
      runInEdtAndWait {
        codeInsightFixture.configureFromExistingVirtualFile(file)
        val method = fixture.elementAtCaret // what
        val usages = ReferencesSearch.search(method).findAll()
        Assertions.assertTrue(usages.size == 1)
        Assertions.assertTrue(usages.single().element.containingFile.name == "build.gradle")
      }
    }
  }

  companion object {
    private val VERSION_CATALOG_FIXTURE = GradleTestFixtureBuilder
      .create("GradleVersionCatalogs-findUsages") {
        withSettingsFile {
          setProjectName("GradleVersionCatalogs-findUsages")
          enableFeaturePreview("VERSION_CATALOGS")
        }
        withFile("gradle/libs.versions.toml", /* language=TOML */ """
      [versions]
      groovy = "3.0.5"
 
      [libraries]
      groovy-core = { module = "org.codehaus.groovy:groovy", version.ref = "groovy" }
      """.trimIndent())
      }
  }
}