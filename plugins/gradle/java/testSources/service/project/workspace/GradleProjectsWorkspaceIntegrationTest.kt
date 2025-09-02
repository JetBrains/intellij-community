// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project.workspace

import com.intellij.platform.testFramework.assertion.moduleAssertion.DependencyAssertions.INHERITED_SDK
import com.intellij.platform.testFramework.assertion.moduleAssertion.DependencyAssertions.MODULE_SOURCE
import com.intellij.platform.testFramework.assertion.moduleAssertion.DependencyAssertions.assertDependencies
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions.assertModuleEntity
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions.assertModules
import com.intellij.testFramework.useProjectAsync
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID
import org.junit.jupiter.api.Test

class GradleProjectsWorkspaceIntegrationTest : ExternalProjectsWorkspaceIntegrationTestCase() {

  @Test
  fun `test library substitution`(): Unit = runBlocking {
    createMavenLibrary("workspace/repository", "org.example:gradle-lib:1.0-SNAPSHOT")
    createGradleWrapper("workspace/gradle-app")
    createGradleBuildFile("workspace/gradle-app") {
      withJavaPlugin()
      withMavenLocal("workspace/repository")
      addImplementationDependency("org.example:gradle-lib:1.0-SNAPSHOT")
    }
    createGradleWrapper("workspace/gradle-lib")
    createGradleBuildFile("workspace/gradle-lib") {
      addGroup("org.example")
      addVersion("1.0-SNAPSHOT")
      withJavaLibraryPlugin()
    }

    openProject("workspace").useProjectAsync { project ->
      assertModules(project, "workspace")

      linkProject(project, "workspace/gradle-app", SYSTEM_ID)

      assertModules(project, "workspace",
                    "gradle-app", "gradle-app.main", "gradle-app.test")
      assertModuleEntity(project, "gradle-app.main") { module ->
        assertDependencies(module, INHERITED_SDK, MODULE_SOURCE,
                           "Gradle: org.example:gradle-lib:1.0-SNAPSHOT")
      }

      linkProject(project, "workspace/gradle-lib", SYSTEM_ID)

      assertModules(project, "workspace",
                    "gradle-app", "gradle-app.main", "gradle-app.test",
                    "gradle-lib", "gradle-lib.main", "gradle-lib.test")
      assertModuleEntity(project, "gradle-app.main") { module ->
        assertDependencies(module, INHERITED_SDK, MODULE_SOURCE,
                           "gradle-lib.main")
      }
    }
  }

  @Test
  fun `test library substitution on unlink project`(): Unit = runBlocking {
    createMavenLibrary("workspace/repository", "org.example:gradle-lib:1.0-SNAPSHOT")
    createGradleWrapper("workspace/gradle-app")
    createGradleBuildFile("workspace/gradle-app") {
      withJavaPlugin()
      withMavenLocal("workspace/repository")
      addImplementationDependency("org.example:gradle-lib:1.0-SNAPSHOT")
    }
    createGradleWrapper("workspace/gradle-lib")
    createGradleBuildFile("workspace/gradle-lib") {
      addGroup("org.example")
      addVersion("1.0-SNAPSHOT")
      withJavaLibraryPlugin()
    }

    openProject("workspace").useProjectAsync { project ->
      assertModules(project, "workspace")

      linkProject(project, "workspace/gradle-app", SYSTEM_ID)
      linkProject(project, "workspace/gradle-lib", SYSTEM_ID)
      unlinkProject(project, "workspace/gradle-lib", SYSTEM_ID)

      assertModules(project, "workspace",
                    "gradle-app", "gradle-app.main", "gradle-app.test")
      assertModuleEntity(project, "gradle-app.main") { module ->
        assertDependencies(module, INHERITED_SDK, MODULE_SOURCE,
                           "Gradle: org.example:gradle-lib:1.0-SNAPSHOT")
      }
    }
  }

  @Test
  fun `test library substitution with transitive dependency`(): Unit = runBlocking {
    createMavenLibrary("workspace/repository", "org.example:gradle-lib:1.0-SNAPSHOT") {
      dependency("compile", "org.example:gradle-super-lib:1.0-SNAPSHOT")
    }
    createMavenLibrary("workspace/repository", "org.example:gradle-super-lib:1.0-SNAPSHOT")
    createGradleWrapper("workspace/gradle-app")
    createGradleBuildFile("workspace/gradle-app") {
      withJavaPlugin()
      withMavenLocal("workspace/repository")
      addImplementationDependency("org.example:gradle-lib:1.0-SNAPSHOT")
    }
    createGradleWrapper("workspace/gradle-lib")
    createGradleBuildFile("workspace/gradle-lib") {
      addGroup("org.example")
      addVersion("1.0-SNAPSHOT")
      withJavaLibraryPlugin()
      withMavenLocal("workspace/repository")
      addApiDependency("org.example:gradle-super-lib:1.0-SNAPSHOT")
    }
    createGradleWrapper("workspace/gradle-super-lib")
    createGradleBuildFile("workspace/gradle-super-lib") {
      addGroup("org.example")
      addVersion("1.0-SNAPSHOT")
      withJavaLibraryPlugin()
    }

    openProject("workspace").useProjectAsync { project ->
      assertModules(project, "workspace")

      linkProject(project, "workspace/gradle-app", SYSTEM_ID)

      assertModuleEntity(project, "gradle-app.main") { module ->
        assertDependencies(module, INHERITED_SDK, MODULE_SOURCE,
                           "Gradle: org.example:gradle-lib:1.0-SNAPSHOT",
                           "Gradle: org.example:gradle-super-lib:1.0-SNAPSHOT")
      }

      linkProject(project, "workspace/gradle-lib", SYSTEM_ID)

      assertModuleEntity(project, "gradle-app.main") { module ->
        assertDependencies(module, INHERITED_SDK, MODULE_SOURCE,
                           "gradle-lib.main",
                           "Gradle: org.example:gradle-super-lib:1.0-SNAPSHOT")
      }
      assertModuleEntity(project, "gradle-lib.main") { module ->
        assertDependencies(module, INHERITED_SDK, MODULE_SOURCE,
                           "Gradle: org.example:gradle-super-lib:1.0-SNAPSHOT")
      }

      linkProject(project, "workspace/gradle-super-lib", SYSTEM_ID)

      assertModules(project, "workspace",
                    "gradle-app", "gradle-app.main", "gradle-app.test",
                    "gradle-lib", "gradle-lib.main", "gradle-lib.test",
                    "gradle-super-lib", "gradle-super-lib.main", "gradle-super-lib.test")
      assertModuleEntity(project, "gradle-app.main") { module ->
        assertDependencies(module, INHERITED_SDK, MODULE_SOURCE,
                           "gradle-lib.main",
                           "gradle-super-lib.main")
      }
      assertModuleEntity(project, "gradle-lib.main") { module ->
        assertDependencies(module, INHERITED_SDK, MODULE_SOURCE,
                           "gradle-super-lib.main")
      }
    }
  }
}