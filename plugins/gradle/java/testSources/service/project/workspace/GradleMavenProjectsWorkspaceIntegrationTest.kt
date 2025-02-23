// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project.workspace

import com.intellij.openapi.externalSystem.util.DEFAULT_SYNC_TIMEOUT
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.platform.testFramework.assertion.moduleAssertion.DependencyAssertions.INHERITED_SDK
import com.intellij.platform.testFramework.assertion.moduleAssertion.DependencyAssertions.MODULE_SOURCE
import com.intellij.platform.testFramework.assertion.moduleAssertion.DependencyAssertions.assertDependencies
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions.assertModuleEntity
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions.assertModules
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.useProjectAsync
import org.jetbrains.idea.maven.model.MavenConstants
import org.junit.jupiter.api.Test

class GradleMavenProjectsWorkspaceIntegrationTest : ExternalProjectsWorkspaceIntegrationTestCase() {

  @Test
  fun `test library substitution with Maven application and Gradle library`(): Unit = timeoutRunBlocking(DEFAULT_SYNC_TIMEOUT) {
    createMavenLibrary("workspace/repository", "org.example:gradle-lib:1.0-SNAPSHOT")
    createMavenConfigFile("workspace/maven-app", "--settings=" + MavenConstants.SETTINGS_XML)
    createMavenSettingsFile("workspace/maven-app") {
      property("localRepository", testRoot.resolve("workspace/repository").toCanonicalPath())
    }
    createMavenPomFile("workspace/maven-app", "org.example:maven-app:1.0-SNAPSHOT") {
      dependency("compile", "org.example:gradle-lib:1.0-SNAPSHOT")
    }
    createGradleWrapper("workspace/gradle-lib")
    createGradleBuildFile("workspace/gradle-lib") {
      addGroup("org.example")
      addVersion("1.0-SNAPSHOT")
      withJavaLibraryPlugin()
    }

    openProject("workspace").useProjectAsync { project ->
      assertModules(project, "workspace")

      linkProject(project, "workspace/maven-app")
      linkProject(project, "workspace/gradle-lib")

      assertModules(project, "workspace", "maven-app",
                    "gradle-lib", "gradle-lib.main", "gradle-lib.test")
      assertModuleEntity(project, "maven-app") { module ->
        assertDependencies(module, INHERITED_SDK, MODULE_SOURCE,
                           "gradle-lib.main")
      }
    }
  }

  @Test
  fun `test library substitution with Gradle application and Maven library`(): Unit = timeoutRunBlocking(DEFAULT_SYNC_TIMEOUT) {
    createMavenLibrary("workspace/repository", "org.example:maven-lib:1.0-SNAPSHOT")
    createMavenPomFile("workspace/maven-lib", "org.example:maven-lib:1.0-SNAPSHOT")
    createGradleWrapper("workspace/gradle-app")
    createGradleBuildFile("workspace/gradle-app") {
      withJavaPlugin()
      withMavenLocal("workspace/repository")
      addImplementationDependency("org.example:maven-lib:1.0-SNAPSHOT")
    }

    openProject("workspace").useProjectAsync { project ->
      assertModules(project, "workspace")

      linkProject(project, "workspace/gradle-app")
      linkProject(project, "workspace/maven-lib")

      assertModules(
        project, "workspace", "maven-lib",
        "gradle-app", "gradle-app.main", "gradle-app.test",
      )
      assertModuleEntity(project, "gradle-app.main") { module ->
        assertDependencies(module, INHERITED_SDK, MODULE_SOURCE,
                           "maven-lib")
      }
    }
  }

  @Test
  fun `test library substitution with Gradle application and multi-language level Maven library`(): Unit = timeoutRunBlocking(DEFAULT_SYNC_TIMEOUT) {
    createMavenLibrary("workspace/repository", "org.example:maven-lib:1.0-SNAPSHOT")
    createMavenPomFile("workspace/maven-lib", "org.example:maven-lib:1.0-SNAPSHOT") {
      property("maven.compiler.source", "8")
      property("maven.compiler.testSource", "9")
    }
    createGradleWrapper("workspace/gradle-app")
    createGradleBuildFile("workspace/gradle-app") {
      withJavaPlugin()
      withMavenLocal("workspace/repository")
      addImplementationDependency("org.example:maven-lib:1.0-SNAPSHOT")
    }

    openProject("workspace").useProjectAsync { project ->
      assertModules(project, "workspace")

      linkProject(project, "workspace/gradle-app")
      linkProject(project, "workspace/maven-lib")

      assertModules(
        project, "workspace",
        "maven-lib", "maven-lib.main", "maven-lib.test",
        "gradle-app", "gradle-app.main", "gradle-app.test",
      )
      assertModuleEntity(project, "gradle-app.main") { module ->
        assertDependencies(module, INHERITED_SDK, MODULE_SOURCE,
                           "maven-lib.main")
      }
    }
  }
}