// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.importing.BuildViewMessagesImportingTestCase.Companion.assertNodeWithDeprecatedGradleWarning
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.buildViewFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.projectFixture
import org.jetbrains.plugins.gradle.testFramework.projectInfo.buildFile
import org.jetbrains.plugins.gradle.testFramework.projectInfo.buildScriptName
import org.jetbrains.plugins.gradle.testFramework.projectInfo.file
import org.jetbrains.plugins.gradle.testFramework.projectInfo.gradleProjectInfo
import org.jetbrains.plugins.gradle.testFramework.projectInfo.gradleWrapper
import org.jetbrains.plugins.gradle.testFramework.projectInfo.initProject
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.function.Consumer

@TestApplication
@ParameterizedClass
@AllGradleVersionsSource
class GradleBuildIssuesMiscImportingTest(private val gradleVersion: GradleVersion) {

  private val testRootFixture = tempPathFixture()
  private val testRoot by testRootFixture

  private val gradleFixture = gradleFixture(gradleVersion)
  private val gradle by gradleFixture

  private val projectFixture = gradleFixture.projectFixture(testRootFixture, numProjectSyncs = 0)
  private val project by projectFixture

  private val buildView by buildViewFixture(projectFixture)

  @ParameterizedTest
  @EnumSource(GradleDsl::class, names = ["GROOVY"])
  fun `test out of memory build failures`(gradleDsl: GradleDsl): Unit = runBlocking {

    val projectInfo = gradleProjectInfo(gradleVersion, gradleDsl = gradleDsl) {
      gradleWrapper()
      file("gradle.properties", """
        |org.gradle.jvmargs=-Xmx100m
      """.trimMargin())
      buildFile {
        when (gradleDsl) {
          GradleDsl.GROOVY -> addPostfix("""
            |def list = new ArrayList<byte[]>()
            |while (true) {
            |   list.add(new byte[1024 * 1024])
            |}
          """.trimMargin())
          GradleDsl.KOTLIN -> addPostfix("""
            |val list = ArrayList<ByteArray>()
            |while (true) {
            |   list.add(ByteArray(1024 * 1024))
            |}
          """.trimMargin())
        }
      }
    }

    val projectRoot = projectInfo.initProject(testRoot)
    val buildScriptPath = projectRoot.resolve(projectInfo.rootModule.buildScriptName)

    gradle.linkProject(project, projectRoot)

    assertAnyOf({
      buildView.assertSyncViewTree {
        assertNode("(failed|finished)".toRegex()) {
          assertNodeWithDeprecatedGradleWarning(gradleVersion)
          assertNode("build.gradle") {
            assertNode("(Java heap space|GC overhead limit exceeded)".toRegex())
          }
        }
      }
    }, {
      buildView.assertSyncViewTree {
        assertNode("(failed|finished)".toRegex()) {
          assertNodeWithDeprecatedGradleWarning(gradleVersion)
          assertNode("(Java heap space|GC overhead limit exceeded)".toRegex())
        }
      }
    })

    buildView.assertSyncViewSelectedNode("(Java heap space|GC overhead limit exceeded)".toRegex()) { text ->
      assertThat(text).matches("""
        |(\* Where:
        |Build file '${Regex.escape(buildScriptPath.toString())}' line: \d
        |
        |)?\* What went wrong:
        |Out of memory. (Java heap space|GC overhead limit exceeded)
        |
        |Possible solution:
        | - Check the JVM memory arguments defined for the gradle process in:
        | {3}gradle.properties in project root directory
        |[\s\S]*
      """.trimMargin().toRegex().toPattern())
    }
  }

  private fun assertAnyOf(vararg assertions: () -> Unit) {
    assertThat(Unit)
      .describedAs("view matches one of accepted variants")
      .satisfiesAnyOf(*assertions.map { Consumer<Unit> { it() } }.toTypedArray())
  }
}
