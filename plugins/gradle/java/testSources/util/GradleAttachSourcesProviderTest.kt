// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.OrderRootType
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import org.assertj.core.api.Assertions.assertThat
import org.gradle.initialization.BuildLayoutParameters
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.importing.TestGradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.testFramework.util.importProject
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test
import java.io.File
import java.nio.file.Path
import java.util.function.Consumer

class GradleAttachSourcesProviderTest : GradleImportingTestCase() {

  private companion object {
    private const val DEPENDENCY = "junit:junit:4.12"
    private const val DEPENDENCY_NAME = "Gradle: junit:junit:4.12"
    private const val DEPENDENCY_JAR = "junit-4.12.jar"
    private const val DEPENDENCY_SOURCES_JAR = "junit-4.12-sources.jar"
    private const val CLASS_FROM_DEPENDENCY = "junit.framework.Test"
    private const val DEPENDENCY_SOURCES_JAR_CACHE_PATH = "caches/modules-2/files-2.1/junit/junit/4.12/" +
                                                          "a6c32b40bf3d76eca54e3c601e5d1470c86fcdfa/$DEPENDENCY_SOURCES_JAR"
  }

  @Test
  fun `test download sources dynamic task`() {
    removeCachedLibrary()
    importProject {
      withJavaPlugin()
      withIdeaPlugin()
      withMavenCentral()
      addTestImplementationDependency(DEPENDENCY)
      addPrefix("idea.module.downloadSources = false")
    }
    assertModules("project", "project.main", "project.test")
    assertSourcesDownloadedAndAttached(targetModule = "project.test")
  }

  @Test
  @TargetVersions("6.5+")
  fun `test download sources with configuration cache`() {
    removeCachedLibrary()
    createProjectSubFile("gradle.properties", "org.gradle.configuration-cache=true\n org.gradle.unsafe.configuration-cache=true")
    importProject {
      withJavaPlugin()
      withIdeaPlugin()
      addPrefix("idea.module.downloadSources = false")
      withMavenCentral()
      addTestImplementationDependency(DEPENDENCY)
    }
    assertModules("project", "project.main", "project.test")
    assertSourcesDownloadedAndAttached(targetModule = "project.test")
  }

  @Test
  @TargetVersions("!4.0")
  fun `test download sources from gradle sub module repository`() {
    removeCachedLibrary()
    createSettingsFile("include 'projectA', 'projectB' ")
    importProject(
      createBuildScriptBuilder()
        .project(":projectA") { it: TestGradleBuildScriptBuilder ->
          it
            .withJavaPlugin()
            .withIdeaPlugin()
            .addPrefix("idea.module.downloadSources = false")
            .withMavenCentral()
            .addTestImplementationDependency(DEPENDENCY)
        }
        .project(":projectB") { it: TestGradleBuildScriptBuilder ->
          it
            .withJavaPlugin()
        }
        .generate()
    )
    assertModules(
      "project",
      "project.projectA", "project.projectA.main", "project.projectA.test",
      "project.projectB", "project.projectB.main", "project.projectB.test",
    )
    assertSourcesDownloadedAndAttached(targetModule = "project.projectA.test")
    assertThat(getModuleLibDeps("project.projectB.test", DEPENDENCY_NAME)).isEmpty()
  }

  private fun assertSourcesDownloadedAndAttached(dependencyName: String = DEPENDENCY_NAME,
                                                 dependencyJar: String = DEPENDENCY_JAR,
                                                 dependencySourcesJar: String = DEPENDENCY_SOURCES_JAR,
                                                 classFromDependency: String = CLASS_FROM_DEPENDENCY,
                                                 targetModule: String
  ) {
    val library: LibraryOrderEntry = getModuleLibDeps(targetModule, dependencyName).single()
    assertThat(library.getRootFiles(OrderRootType.CLASSES))
      .hasSize(1)
      .allSatisfy(Consumer { assertEquals(dependencyJar, it.name) })

    val psiFile = runReadAction {
      JavaPsiFacade.getInstance(myProject).findClass(classFromDependency, GlobalSearchScope.allScope(myProject))!!.containingFile
    }

    val output = mutableListOf<String>()
    val listener = object : ExternalSystemTaskNotificationListenerAdapter() {
      override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {
        output += text
      }
    }
    try {
      ExternalSystemProgressNotificationManager.getInstance().addNotificationListener(listener)
      val callback = GradleAttachSourcesProvider().getActions(mutableListOf(library), psiFile)
        .single()
        .perform(mutableListOf(library))
        .apply { waitFor(5000) }
      assertNull(callback.error)
    }
    finally {
      ExternalSystemProgressNotificationManager.getInstance().removeNotificationListener(listener)
    }

    assertThat(output)
      .filteredOn { it.startsWith("Sources were downloaded to") }
      .hasSize(1)
      .allSatisfy(Consumer { assertThat(it).endsWith(dependencySourcesJar) })

    assertThat(library.getRootFiles(OrderRootType.SOURCES))
      .hasSize(1)
      .allSatisfy(Consumer { assertEquals(dependencySourcesJar, it.name) })
  }

  private fun removeCachedLibrary(cachePath: String = DEPENDENCY_SOURCES_JAR_CACHE_PATH) {
    val serviceDirectory = GradleSettings.getInstance(myProject).serviceDirectoryPath
    val gradleUserHome = if (serviceDirectory != null) Path.of(serviceDirectory) else BuildLayoutParameters().gradleUserHomeDir.toPath()
    val junitPath = "$gradleUserHome/$cachePath"
    val cachedSource = File(junitPath)
    if (cachedSource.exists()) {
      if (!cachedSource.delete()) {
        throw IllegalStateException("Unable to prepare test execution environment")
      }
    }
  }
}