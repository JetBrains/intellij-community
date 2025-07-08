// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl

import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.testFramework.utils.vfs.getPsiFile
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest

class GradleDslPropertiesFileTest : GradleCodeInsightTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test find usages of property`(gradleVersion: GradleVersion) {
    test(gradleVersion, PROPERTIES_FIXTURE) {
      runInEdtAndWait {
        writeTextAndCommit("build.gradle", "foo")
        val buildscript = getFile("build.gradle")
        val psiPropertiesFile = getFile("gradle.properties").getPsiFile(project) as PropertiesFile
        val prop = psiPropertiesFile.findPropertyByKey("foo")
        assertNotNull(prop, "Expected not-null prop")
        val buildscriptScope = GlobalSearchScope.fileScope(codeInsightFixture.project, buildscript)
        val usageRefs = ReferencesSearch.search(prop!!.psiElement, buildscriptScope).findAll()
        val usageRef = usageRefs.singleOrNull()
        assertNotNull(usageRef, "Expected not-null usage ref")
        assertTrue(usageRef!!.element.language == GroovyLanguage)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test completion in project`(gradleVersion: GradleVersion) {
    test(gradleVersion, PROPERTIES_FIXTURE) {
      testCompletion("project.<caret>", "foo", "foobar")
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test completion in ext`(gradleVersion: GradleVersion) {
    test(gradleVersion, PROPERTIES_FIXTURE) {
      testCompletion("project.ext.<caret>", "foo", "foobar")
    }
  }

  // the test is unstable on TeamCity
  // @ParameterizedTest
  // @AllGradleVersionsSource
  @Disabled("IDEA-375504")
  fun `test go to definition`(gradleVersion: GradleVersion) {
    test(gradleVersion, PROPERTIES_FIXTURE) {
      testGotoDefinition("fo<caret>o") {
        assertTrue(it.containingFile is PropertiesFile)
      }
    }
  }

  companion object {

    private val PROPERTIES_FIXTURE = GradleTestFixtureBuilder.create("GradlePropertiesFileTest") { gradleVersion ->
      withSettingsFile(gradleVersion) {
        setProjectName("GradlePropertiesFileTest")
      }
      withBuildFile(content = "")
      withFile("gradle.properties",  /* language=properties */  """
        foo=1
        foobar=2
        foo.bar=3
      """.trimIndent())
    }
  }
}