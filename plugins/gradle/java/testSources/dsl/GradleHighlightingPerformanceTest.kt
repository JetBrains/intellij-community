// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl

import com.intellij.openapi.externalSystem.util.runInEdtAndWait
import com.intellij.openapi.externalSystem.util.text
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.junit.jupiter.params.ParameterizedTest

class GradleHighlightingPerformanceTest : GradleCodeInsightTestCase() {

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testPerformance(gradleVersion: GradleVersion) {
    test(gradleVersion, FIXTURE_BUILDER) {
      val file = getFile("build.gradle")
      val pos = file.text.indexOf("a.json")
      runInEdtAndWait {
        fixture.openFileInEditor(file)
        fixture.editor.caretModel.moveToOffset(pos + 1)
        fixture.checkHighlighting()

        PlatformTestUtil.startPerformanceTest("GradleHighlightingPerformanceTest.testPerformance", 6000) {
          fixture.psiManager.dropPsiCaches()
          repeat(4) {
            fixture.type('a')
            PsiDocumentManager.getInstance(fixture.project).commitAllDocuments()
            fixture.doHighlighting()
            fixture.completeBasic()
          }
        }.assertTiming()
      }
    }
  }

  companion object {
    private val FIXTURE_BUILDER = GradleTestFixtureBuilder.buildFile("GradleHighlightingPerformanceTest") {
      addBuildScriptRepository("mavenCentral()")
      addBuildScriptClasspath("io.github.http-builder-ng:http-builder-ng-apache:1.0.3")
      addImport("groovyx.net.http.HttpBuilder")
      withTask("bitbucketJenkinsTest") {
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
