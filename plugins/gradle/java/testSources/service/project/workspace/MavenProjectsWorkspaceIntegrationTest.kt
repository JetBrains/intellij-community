// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project.workspace

import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.platform.testFramework.assertion.moduleAssertion.DependencyAssertions.INHERITED_SDK
import com.intellij.platform.testFramework.assertion.moduleAssertion.DependencyAssertions.MODULE_SOURCE
import com.intellij.platform.testFramework.assertion.moduleAssertion.DependencyAssertions.assertDependencies
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions.assertModuleEntity
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions.assertModules
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.useProjectAsync
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.plugins.gradle.testFramework.util.DEFAULT_SYNC_TIMEOUT
import org.junit.jupiter.api.Test

class MavenProjectsWorkspaceIntegrationTest : ExternalProjectsWorkspaceIntegrationTestCase() {

  @Test
  fun `test library substitution`(): Unit = timeoutRunBlocking(DEFAULT_SYNC_TIMEOUT) {
    createMavenLibrary("workspace/repository", "org.example:maven-lib:1.0-SNAPSHOT")
    createMavenConfigFile("workspace/maven-app", "--settings=" + MavenConstants.SETTINGS_XML)
    createMavenSettingsFile("workspace/maven-app") {
      property("localRepository", testRoot.resolve("workspace/repository").toCanonicalPath())
    }
    createMavenPomFile("workspace/maven-app", "org.example:maven-app:1.0-SNAPSHOT") {
      dependency("compile", "org.example:maven-lib:1.0-SNAPSHOT")
    }
    createMavenPomFile("workspace/maven-lib", "org.example:maven-lib:1.0-SNAPSHOT")

    openProject("workspace").useProjectAsync { project ->
      assertModules(project, "workspace")

      linkProject(project, "workspace/maven-app")

      assertModules(project, "workspace", "maven-app")
      assertModuleEntity(project, "maven-app") { module ->
        assertDependencies(module, INHERITED_SDK, MODULE_SOURCE,
                           "Maven: org.example:maven-lib:1.0-SNAPSHOT")
      }

      linkProject(project, "workspace/maven-lib")

      assertModules(project, "workspace", "maven-app", "maven-lib")
      assertModuleEntity(project, "maven-app") { module ->
        assertDependencies(module, INHERITED_SDK, MODULE_SOURCE,
                           "maven-lib")
      }
    }
  }

  @Test
  fun `test library substitution on unlink project`(): Unit = timeoutRunBlocking(DEFAULT_SYNC_TIMEOUT) {
    createMavenLibrary("workspace/repository", "org.example:maven-lib:1.0-SNAPSHOT")
    createMavenConfigFile("workspace/maven-app", "--settings=" + MavenConstants.SETTINGS_XML)
    createMavenSettingsFile("workspace/maven-app") {
      property("localRepository", testRoot.resolve("workspace/repository").toCanonicalPath())
    }
    createMavenPomFile("workspace/maven-app", "org.example:maven-app:1.0-SNAPSHOT") {
      dependency("compile", "org.example:maven-lib:1.0-SNAPSHOT")
    }
    createMavenPomFile("workspace/maven-lib", "org.example:maven-lib:1.0-SNAPSHOT")

    openProject("workspace").useProjectAsync { project ->
      assertModules(project, "workspace")

      linkProject(project, "workspace/maven-app")
      linkProject(project, "workspace/maven-lib")
      unlinkProject(project, "workspace/maven-lib")

      assertModules(project, "workspace", "maven-app")
      assertModuleEntity(project, "maven-app") { module ->
        assertDependencies(module, INHERITED_SDK, MODULE_SOURCE,
                           "Maven: org.example:maven-lib:1.0-SNAPSHOT")
      }
    }
  }

  @Test
  fun `test library substitution with transitive dependency`(): Unit = timeoutRunBlocking(DEFAULT_SYNC_TIMEOUT) {
    createMavenLibrary("workspace/repository", "org.example:maven-lib:1.0-SNAPSHOT") {
      dependency("compile", "org.example:maven-super-lib:1.0-SNAPSHOT")
    }
    createMavenLibrary("workspace/repository", "org.example:maven-super-lib:1.0-SNAPSHOT")
    createMavenConfigFile("workspace/maven-app", "--settings=" + MavenConstants.SETTINGS_XML)
    createMavenSettingsFile("workspace/maven-app") {
      property("localRepository", testRoot.resolve("workspace/repository").toCanonicalPath())
    }
    createMavenPomFile("workspace/maven-app", "org.example:maven-app:1.0-SNAPSHOT") {
      dependency("compile", "org.example:maven-lib:1.0-SNAPSHOT")
    }
    createMavenConfigFile("workspace/maven-lib", "--settings=" + MavenConstants.SETTINGS_XML)
    createMavenSettingsFile("workspace/maven-lib") {
      property("localRepository", testRoot.resolve("workspace/repository").toCanonicalPath())
    }
    createMavenPomFile("workspace/maven-lib", "org.example:maven-lib:1.0-SNAPSHOT") {
      dependency("compile", "org.example:maven-super-lib:1.0-SNAPSHOT")
    }
    createMavenPomFile("workspace/maven-super-lib", "org.example:maven-super-lib:1.0-SNAPSHOT")

    openProject("workspace").useProjectAsync { project ->
      assertModules(project, "workspace")

      linkProject(project, "workspace/maven-app")

      assertModules(project, "workspace", "maven-app")
      assertModuleEntity(project, "maven-app") { module ->
        assertDependencies(module, INHERITED_SDK, MODULE_SOURCE,
                           "Maven: org.example:maven-lib:1.0-SNAPSHOT",
                           "Maven: org.example:maven-super-lib:1.0-SNAPSHOT")
      }

      linkProject(project, "workspace/maven-lib")

      assertModules(project, "workspace", "maven-app", "maven-lib")
      assertModuleEntity(project, "maven-app") { module ->
        assertDependencies(module, INHERITED_SDK, MODULE_SOURCE,
                           "maven-lib",
                           "Maven: org.example:maven-super-lib:1.0-SNAPSHOT")
      }
      assertModuleEntity(project, "maven-lib") { module ->
        assertDependencies(module, INHERITED_SDK, MODULE_SOURCE,
                           "Maven: org.example:maven-super-lib:1.0-SNAPSHOT")
      }

      linkProject(project, "workspace/maven-super-lib")

      assertModules(project, "workspace", "maven-app", "maven-lib", "maven-super-lib")
      assertModuleEntity(project, "maven-app") { module ->
        assertDependencies(module, INHERITED_SDK, MODULE_SOURCE,
                           "maven-lib", "maven-super-lib")
      }
      assertModuleEntity(project, "maven-lib") { module ->
        assertDependencies(module, INHERITED_SDK, MODULE_SOURCE,
                           "maven-super-lib")
      }
    }
  }
}