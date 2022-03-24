// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.extensions.Extensions
import com.intellij.testFramework.RunAll
import com.intellij.util.ThrowableRunnable
import org.assertj.core.api.Assertions.assertThat
import org.gradle.tooling.GradleConnectionException
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverExtension
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test
import java.io.File
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class GradleActionWithImportTest : BuildViewMessagesImportingTestCase() {

  override fun setUp() {
    super.setUp()
    val point = Extensions.getRootArea().getExtensionPoint(GradleProjectResolverExtension.EP_NAME)
    point.registerExtension(TestProjectResolverExtension(), testRootDisposable)
  }

  override fun tearDown() {
    RunAll(
      ThrowableRunnable { TestProjectResolverExtension.cleanup() },
      ThrowableRunnable { super.tearDown() }
    ).run()
  }

  @Test
  @TargetVersions("4.8+")
  fun `test start tasks can be set by model builder and run on import`() {
    val testFile = File(projectPath, "testFile")
    assertThat(testFile).doesNotExist()

    val randomKey = Random().nextLong().toString()

    importProject(
      """
        import org.gradle.api.Project;
        import javax.inject.Inject;
        import org.gradle.tooling.provider.model.ToolingModelBuilder;
        import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;
        class TestPlugin implements Plugin<Project> {
          private ToolingModelBuilderRegistry registry;

          @Inject
          TestPlugin(ToolingModelBuilderRegistry registry) {
            this.registry = registry;
          }

          void apply(Project project) {
            registry.register(new TestModelBuilder());
          }

          private static class TestModelBuilder implements ToolingModelBuilder {
            boolean canBuild(String modelName) {
              return 'java.lang.Object' == modelName;
            }

          @Override
          Object buildAll(String modelName, Project project) {
              StartParameter startParameter = project.getGradle().getStartParameter();
              Set<String> tasks = new HashSet<>(startParameter.getTaskNames());
              tasks.add("importTestTask");
              startParameter.setTaskNames(tasks);
              return null;
            }
          }
        }
        apply plugin: TestPlugin
        task importTestTask {
          doLast {
            def f = new File('testFile')
            f.write '$randomKey'
          }
        }
      """.trimIndent())

    TestProjectResolverExtension.waitForBuildFinished(projectPath, 10, TimeUnit.SECONDS)
    assertThat(testFile)
      .exists()
      .hasContent(randomKey)

    assertSyncViewTreeEquals("-\n" +
                             " -finished\n" +
                             "  :importTestTask")
  }

  @Test
  fun `test default tasks are not run on import`() {
    importProject(
      """
        defaultTasks "clean", "build"
        
        import org.gradle.api.Project;
        import javax.inject.Inject;
        import org.gradle.tooling.provider.model.ToolingModelBuilder;
        import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;
        class TestPlugin implements Plugin<Project> {
          private ToolingModelBuilderRegistry registry;

          @Inject
          TestPlugin(ToolingModelBuilderRegistry registry) {
            this.registry = registry;
          }

          void apply(Project project) {
            registry.register(new TestModelBuilder());
          }

          private static class TestModelBuilder implements ToolingModelBuilder {
            boolean canBuild(String modelName) {
              return 'java.lang.Object' == modelName;
            }

          @Override
          Object buildAll(String modelName, Project project) {
              return null;
            }
          }
        }
        apply plugin: TestPlugin
      """.trimIndent())

    assertSyncViewTreeEquals("-\n" +
                             " finished")
  }
}

class TestProjectResolverExtension : AbstractProjectResolverExtension() {
  val buildFinished = CompletableFuture<Boolean>()

  override fun setProjectResolverContext(projectResolverContext: ProjectResolverContext) {
    register(this, projectResolverContext.projectPath)
  }

  override fun buildFinished(exception: GradleConnectionException?) {
    buildFinished.complete(true)
  }

  override fun getProjectsLoadedModelProvider(): ProjectImportModelProvider {
    return TestBuildObjectModelProvider()
  }

  companion object {
    private val extensions: MutableMap<String, TestProjectResolverExtension> = mutableMapOf()

    fun register(extension: TestProjectResolverExtension, key: String) {
      extensions[key] = extension
    }

    @Throws(Exception::class)
    fun waitForBuildFinished(key: String, timeout: Int, unit: TimeUnit): Boolean {
      val ext = extensions[key] ?: throw Exception("Unknown test extension key [$key].\nAvailable extension keys: ${extensions.keys}")
      return ext.buildFinished.get(timeout.toLong(), unit)
    }

    fun cleanup() {
      extensions.clear()
    }
  }
}
