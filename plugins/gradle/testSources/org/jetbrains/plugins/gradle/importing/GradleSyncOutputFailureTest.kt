// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.asDisposable
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.isTaskConfigurationAvoidanceSupported
import org.jetbrains.plugins.gradle.importing.BuildViewMessagesImportingTestCase.Companion.assertNodeWithDeprecatedGradleWarning
import org.jetbrains.plugins.gradle.importing.syncAction.GradleProjectResolverTestCase.TestProjectResolverExtension
import org.jetbrains.plugins.gradle.importing.syncAction.registerProjectResolverExtension
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.buildViewFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.projectFixture
import org.jetbrains.plugins.gradle.testFramework.projectInfo.buildFile
import org.jetbrains.plugins.gradle.testFramework.projectInfo.gradleProjectInfo
import org.jetbrains.plugins.gradle.testFramework.projectInfo.initProject
import org.jetbrains.plugins.gradle.tooling.builder.FailingTestModelBuilder
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass

@TestApplication
@ParameterizedClass
@AllGradleVersionsSource("true,false")
@RegistryKey("gradle.use.resilient.model.fetch.unstable", true.toString())
class GradleSyncOutputFailureTest(
  private val gradleVersion: GradleVersion,
  private val showSuppressedFailures: Boolean,
) {

  private val testRootFixture = tempPathFixture()
  private val testRoot by testRootFixture

  private val gradleFixture = gradleFixture(gradleVersion)
  private val gradle by gradleFixture

  private val projectFixture = gradleFixture.projectFixture(testRootFixture, numProjectSyncs = 0)
  private val project by projectFixture

  private val buildView by buildViewFixture(projectFixture)

  @BeforeEach
  fun setUp(@TestDisposable disposable: Disposable) {
    Registry.get("gradle.show.suppressed.failure.events")
      .setValue(showSuppressedFailures, disposable)

    Assumptions.assumeFalse(
      GradleVersionUtil.isGradleAtLeast(gradleVersion, "4.8") &&
      GradleVersionUtil.isGradleOlderThan(gradleVersion, "5.1")
    ) {
      "The exact output is too unstable to assert reliably:" +
      " Gradle 4.8–5.x introduced Task Configuration Avoidance and a series of error-message reforms in a short window."
    }
  }

  @Test
  fun `test sync reports success`(): Unit = runBlocking {
    val projectInfo = gradleProjectInfo(gradleVersion) {
      buildFile { }
    }

    val projectRoot = projectInfo.initProject(testRoot)

    gradle.linkProject(project, projectRoot)

    buildView.assertSyncViewTree {
      assertNode("finished") {
        assertNodeWithDeprecatedGradleWarning(gradleVersion)
      }
    }
  }

  @Test
  fun `test sync reports task initialization failure`(): Unit = runBlocking {
    val projectInfo = gradleProjectInfo(gradleVersion) {
      buildFile {
        addPrefix("""
          |open class BrokenIdeaProjectTask : DefaultTask() {
          |  init {
          |    throw RuntimeException("Task initialization failure")
          |  }
          |}
        """.trimMargin())
        registerTask("brokenIdeaProjectTask", "BrokenIdeaProjectTask")
      }
    }

    val projectRoot = projectInfo.initProject(testRoot)

    gradle.linkProject(project, projectRoot)

    buildView.assertSyncViewTree {
      assertNode("failed") {
        assertNodeWithDeprecatedGradleWarning(gradleVersion)
        when {
          !isTaskConfigurationAvoidanceSupported(gradleVersion) -> {
            assertNode("Could not create task of type 'BrokenIdeaProjectTask'")
          }
          !isResilientSyncEnabled(gradleVersion) -> {
            assertNode("Could not create task ':brokenIdeaProjectTask'.")
          }
          else -> {
            if (showSuppressedFailures) {
              assertNode("build.gradle.kts") {
                assertNode("Could not create task ':brokenIdeaProjectTask'.")
              }
            }
          }
        }
      }
    }
  }

  @Test
  fun `test sync reports build script compilation failure`(): Unit = runBlocking {
    val projectInfo = gradleProjectInfo(gradleVersion) {
      buildFile {
        addPostfix("""
          |dependencies {
        """.trimMargin())
      }
    }

    val projectRoot = projectInfo.initProject(testRoot)

    gradle.linkProject(project, projectRoot)

    buildView.assertSyncViewTree {
      assertNode("failed") {
        assertNodeWithDeprecatedGradleWarning(gradleVersion)
        assertNode("build.gradle.kts") {
          if (isResilientSyncEnabled(gradleVersion) && showSuppressedFailures) {
            assertNode("A problem occurred configuring root project 'project'.")
          }
          assertNode("Expecting '}'")
        }
      }
    }
  }

  @Test
  fun `test sync reports dependency resolution failure`(): Unit = runBlocking {
    val projectInfo = gradleProjectInfo(gradleVersion) {
      buildFile {
        withJavaPlugin()
        addImplementationDependency("abc:abc:123")
      }
    }

    val projectRoot = projectInfo.initProject(testRoot)

    gradle.linkProject(project, projectRoot)

    buildView.assertSyncViewTree {
      assertNode("finished") {
        assertNodeWithDeprecatedGradleWarning(gradleVersion)
        assertNode("Could Not Resolve abc:abc:123 for project:main")
        assertNode("Could Not Resolve abc:abc:123 for project:test")
      }
    }
  }

  @Test
  fun `test sync reports model builder failure`(): Unit = runBlocking {
    project.registerProjectResolverExtension(TestProjectResolverExtension::class.java, asDisposable()) {
      addProjectModelClass(FailingTestModelBuilder.Model::class.java)
      addToolingExtensionClass(FailingTestModelBuilder::class.java)
    }

    val projectInfo = gradleProjectInfo(gradleVersion) {
      buildFile { }
    }

    val projectRoot = projectInfo.initProject(testRoot)

    gradle.linkProject(project, projectRoot)

    buildView.assertSyncViewTree {
      assertNode("failed") {
        assertNodeWithDeprecatedGradleWarning(gradleVersion)
        assertNode("root project 'project': Test import errors")
      }
    }
    buildView.assertSyncViewNode("root project 'project': Test import errors") {
      assertThat(it).startsWith("""
        |Unable to import Test model
        |
        |java.lang.RuntimeException: Boom! '"{}}${'\n'}${'\t'}
        |${'\t'}at org.jetbrains.plugins.gradle.tooling.builder.FailingTestModelBuilder.buildAll(FailingTestModelBuilder.java:
      """.trimMargin())
    }
  }


  companion object {

    private fun isResilientSyncEnabled(gradleVersion: GradleVersion): Boolean =
      GradleVersionUtil.isGradleAtLeast(gradleVersion, "9.3")
  }
}
