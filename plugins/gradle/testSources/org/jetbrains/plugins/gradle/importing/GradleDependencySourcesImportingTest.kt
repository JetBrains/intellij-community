// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import org.jetbrains.plugins.gradle.testFramework.util.createBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.createSettingsFile
import org.jetbrains.plugins.gradle.testFramework.util.importProject
import org.junit.Test

class GradleDependencySourcesImportingTest : GradleDependencySourcesImportingTestCase() {

  @Test
  fun testDependencyPoliciesWorksWithGenericProjects() {
    importProject {
      withJavaPlugin()
      withMavenCentral()
      addTestImplementationDependency(DEPENDENCY)
      withIdeaPlugin()
      withPrefix {
        settings.ideaPluginValue?.let<Boolean, Unit> { downloadSources ->
          call("idea.module") {
            assign("downloadJavadoc", false)
            assign("downloadSources", downloadSources)
          }
        }
      }
    }
    assertSingleLibraryOrderEntry("project.test", DEPENDENCY_NAME)
    assertDependencyInGradleCache(DEPENDENCY)
  }

  @Test
  fun testSourcesExcludedFromGradleMultiModuleProjectCacheOnDisabledFlag() {
    createSettingsFile("include 'projectA', 'projectB' ")
    createSettingsFile {
      include("projectA", "projectB")
    }
    createBuildFile("projectA") {
      withJavaPlugin()
      withMavenCentral()
      addTestImplementationDependency(DEPENDENCY)
    }
    createBuildFile("projectB") {
      withJavaPlugin()
      withMavenCentral()
      addTestImplementationDependency(DEPENDENCY)
    }
    importProject {
      withIdeaPlugin()
      withPrefix {
        settings.ideaPluginValue?.let<Boolean, Unit> { downloadSources ->
          call("idea.module") {
            assign("downloadJavadoc", false)
            assign("downloadSources", downloadSources)
          }
        }
      }
    }
    assertSingleLibraryOrderEntry("project.projectA.test", DEPENDENCY_NAME)
    assertSingleLibraryOrderEntry("project.projectB.test", DEPENDENCY_NAME)
    assertDependencyInGradleCache(DEPENDENCY)
  }

  companion object {
    private const val DEPENDENCY = "junit:junit:4.12"
    private const val DEPENDENCY_NAME = "Gradle: $DEPENDENCY"
  }
}