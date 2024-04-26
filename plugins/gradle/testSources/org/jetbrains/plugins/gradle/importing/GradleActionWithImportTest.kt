// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test
import java.io.File
import java.util.*

class GradleActionWithImportTest : GradleActionWithImportTestCase() {

  @Test
  @TargetVersions("4.8+")
  fun `test start tasks can be set by model builder and run on import`() {
    Disposer.newDisposable().use { disposable ->
      addProjectResolverExtension(TestProjectResolverExtension::class.java, disposable) {
        addModelProviders(disposable, TestBuildObjectModelProvider())
      }

      val testFile = File(projectPath, "testFile")
      assertThat(testFile).doesNotExist()

      val testFilePath = testFile.absolutePath
      val randomKey = Random().nextLong().toString()

      importProject("""
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
            def f = new File('$testFilePath')
            f.write '$randomKey'
          }
        }
      """.trimIndent())

      assertThat(testFile)
        .exists()
        .hasContent(randomKey)

      assertSyncViewTree {
        assertNode("finished") {
          assertNode(":importTestTask")
          assertNodeWithDeprecatedGradleWarning()
        }
      }
    }
  }

  @Test
  fun `test default tasks are not run on import`() {
    Disposer.newDisposable().use { disposable ->
      addProjectResolverExtension(TestProjectResolverExtension::class.java, disposable) {
        addModelProviders(disposable, TestBuildObjectModelProvider())
      }

      importProject("""
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

      assertSyncViewTree {
        assertNode("finished") {
          assertNodeWithDeprecatedGradleWarning()
        }
      }
    }
  }
}
