// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.ex.RangeMarkerEx
import com.intellij.openapi.vfs.readText
import com.intellij.psi.PsiDocumentManager
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import com.intellij.util.asSafely
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionUtil.disableSlowCompletionElements
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest

class GradleHighlightingPerformanceTest : GradleCodeInsightTestCase() {

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testPerformance(gradleVersion: GradleVersion) {
    test(gradleVersion, FIXTURE_BUILDER) {
      val file = getFile("build.gradle")
      val pos = file.readText().indexOf("a.json")
      invokeAndWaitIfNeeded {
        fixture.openFileInEditor(file)
        fixture.editor.caretModel.moveToOffset(pos + 1)
        fixture.checkHighlighting()

        Benchmark.newBenchmark("GradleHighlightingPerformanceTest.testPerformance") {
          fixture.psiManager.dropPsiCaches()
          repeat(4) {
            fixture.type('a')
            PsiDocumentManager.getInstance(fixture.project).commitAllDocuments()
            fixture.doHighlighting()
            fixture.completeBasic()
          }
        }.start(GradleHighlightingPerformanceTest::testPerformance)
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testCompletionPerformance(gradleVersion: GradleVersion) {
    test(gradleVersion, COMPLETION_FIXTURE) {
      val file = getFile("build.gradle")
      val pos = file.readText().indexOf("dependencies {") + "dependencies {".length
      invokeAndWaitIfNeeded {
        fixture.openFileInEditor(file)
        fixture.editor.caretModel.moveToOffset(pos)
        fixture.checkHighlighting()
        fixture.type('i')
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val document = PsiDocumentManager.getInstance(project).getDocument(fixture.file)
        disableSlowCompletionElements(fixture.testRootDisposable)
        val repeatSize = 10
        Benchmark.newBenchmark("GradleHighlightingPerformanceTest.testCompletion") {
          fixture.psiManager.dropResolveCaches()
          repeat(repeatSize) {
            val lookupElements = fixture.completeBasic()
            Assertions.assertTrue(lookupElements.any { it.lookupString == "implementation" })
          }
        }.setup {
          val rangeMarkers = ArrayList<RangeMarker>()
          document.asSafely<DocumentEx>()?.processRangeMarkers { rangeMarkers.add(it) }
          rangeMarkers.forEach { marker -> document.asSafely<DocumentEx>()?.removeRangeMarker(marker as RangeMarkerEx) }
        }.start(GradleHighlightingPerformanceTest::testCompletionPerformance)
      }
    }
  }

  companion object {

    private val FIXTURE_BUILDER = GradleTestFixtureBuilder.create("GradleHighlightingPerformanceTest") { gradleVersion ->
      withSettingsFile(gradleVersion) {
        setProjectName("GradleHighlightingPerformanceTest")
      }
      withBuildFile(gradleVersion) {
        withBuildScriptMavenCentral()
        addBuildScriptClasspath("io.github.http-builder-ng:http-builder-ng-apache:1.0.3")
        addImport("groovyx.net.http.HttpBuilder")
        withPostfix {
          call("tasks.create", "bitbucketJenkinsTest") {
            call("doLast") {
              property("bitbucket", call("HttpBuilder.configure") {
                assign("request.uri", "https://127.0.0.1")
                call("request.auth.basic", "", "")
              })
              call("bitbucket.post") {
                assign("request.uri.path", "/rest/api/")
                assign("request.contentType", "a.json")
              }
            }
          }
        }
      }
    }

    private val COMPLETION_FIXTURE = GradleTestFixtureBuilder.create("GradleCompletionPerformanceTest") { gradleVersion ->
      withSettingsFile(gradleVersion) {
        setProjectName("GradleCompletionPerformanceTest")
      }
      withBuildFile(gradleVersion) {
        withPlugin("java")
        withPlugin("groovy")
        withPlugin("scala")
        withMavenCentral()
        withPostfix {
          call("dependencies") {

          }
        }
      }
    }
  }
}
