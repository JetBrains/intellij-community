// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.quarantine.dsl

import com.intellij.model.psi.impl.targetSymbols
import com.intellij.testFramework.runInEdtAndWait
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.resolve.GradleSubprojectSymbol
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.junit.jupiter.params.ParameterizedTest

class GradleProjectReferenceTest : GradleCodeInsightTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test rename child`(gradleVersion: GradleVersion) {
    test(gradleVersion, FIXTURE_BUILDER) {
      testRename("""
        // :child:foo:bar
        project(':child')
        project(':child:foo')
        project(':<caret>child:foo:bar')
        project(':child:foo:baz')
        project(':child:bar')
        project(':child:bar:foo')
        // :child:foo:bar
        // :child:foox:bar
        // :childx:foo:bar
        println ":child:foo:bar"
        println ":child:foox:bar"
        println ":xchild:foox:bar"
      """, """
        // :xxx:foo:bar
        project(':xxx')
        project(':xxx:foo')
        project(':<caret>xxx:foo:bar')
        project(':xxx:foo:baz')
        project(':xxx:bar')
        project(':xxx:bar:foo')
        // :xxx:foo:bar
        // :xxx:foox:bar
        // :xxxx:foo:bar
        println ":xxx:foo:bar"
        println ":xxx:foox:bar"
        println ":xchild:foox:bar"
      """)
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test rename grand child`(gradleVersion: GradleVersion) {
    test(gradleVersion, FIXTURE_BUILDER) {
      testRename("""
        // :child:foo:bar
        project(':child')
        project(':child:<caret>foo')
        project(':child:foo:bar')
        project(':child:foo:baz')
        project(':child:bar')
        project(':child:bar:foo')
        // :child:foo:bar
        // :child:foox:bar
        // :childx:foo:bar
        println ":child:foo:bar"
        println ":child:foox:bar"
        println ":xchild:foo:bar"
      """, """
        // :child:xxx:bar
        project(':child')
        project(':child:<caret>xxx')
        project(':child:xxx:bar')
        project(':child:xxx:baz')
        project(':child:bar')
        project(':child:bar:foo')
        // :child:xxx:bar
        // :child:xxxx:bar
        // :childx:foo:bar
        println ":child:xxx:bar"
        println ":child:xxxx:bar"
        println ":xchild:foo:bar"
      """)
    }
  }

  private fun testRename(before: String, after: String) {
    runInEdtAndWait {
      updateProjectFile(before)
      val symbols = targetSymbols(fixture.file, fixture.caretOffset)
      val symbol = symbols.toList()[0] as GradleSubprojectSymbol
      fixture.renameTarget(symbol, "xxx")
      fixture.checkResult(after)
    }
  }

  companion object {

    private val FIXTURE_BUILDER = GradleTestFixtureBuilder.create("GradleProjectReferenceTest") { gradleVersion ->
      withDirectory("child/foo")
      withDirectory("child/foo/bar")
      withDirectory("child/foo/baz")
      withDirectory("child/bar")
      withDirectory("child/bar/foo")

      withSettingsFile(gradleVersion) {
        setProjectName("GradleProjectReferenceTest")
        include("child")
        include("child:foo")
        include("child:foo:bar")
        include("child:foo:baz")
        include("child:bar")
        include("child:bar:foo")
      }
    }
  }
}