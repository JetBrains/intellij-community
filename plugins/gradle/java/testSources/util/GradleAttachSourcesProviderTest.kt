// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import com.intellij.buildsystem.model.unified.UnifiedCoordinates
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.externalSystem.model.project.LibraryPathType
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.OrderRootType
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.importing.TestGradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.service.cache.GradleLocalCacheHelper
import org.jetbrains.plugins.gradle.testFramework.util.importProject
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import kotlin.io.path.deleteIfExists

class GradleAttachSourcesProviderTest : GradleImportingTestCase() {

  private companion object {
    private const val DEPENDENCY = "junit:junit:4.12"
    private const val DEPENDENCY_NAME = "Gradle: junit:junit:4.12"
    private const val DEPENDENCY_JAR = "junit-4.12.jar"
    private const val DEPENDENCY_SOURCES_JAR = "junit-4.12-sources.jar"
    private const val CLASS_FROM_DEPENDENCY = "junit.framework.Test"
    private const val DEPENDENCY_SOURCES_JAR_CACHE_PATH = "caches/modules-2/files-2.1/junit/junit/4.12/" +
                                                          "a6c32b40bf3d76eca54e3c601e5d1470c86fcdfa/$DEPENDENCY_SOURCES_JAR"
    private const val DEFAULT_ATTACH_SOURCE_DEADLINE_MS = 5000L
  }

  override fun setUp() {
    super.setUp()
    removeCachedLibrary()
  }

  @Test
  fun `test download sources dynamic task`() {
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
  fun `test sources available in gradle cache after task execution`() {
    importProject {
      withJavaPlugin()
      withIdeaPlugin()
      withMavenCentral()
      addTestImplementationDependency(DEPENDENCY)
      addPrefix("idea.module.downloadSources = false")
    }
    assertModules("project", "project.main", "project.test")
    assertSourcesDownloadedAndAttached(targetModule = "project.test")
    assertThat(findArtifactComponentsInGradleCache(DEPENDENCY)[LibraryPathType.SOURCE]).isNotEmpty
  }

  @Test
  @TargetVersions("6.5+")
  fun `test download sources with configuration cache`() {
    createProjectSubFile("gradle.properties", "org.gradle.configuration-cache=true\n org.gradle.unsafe.configuration-cache=true")
    importProject {
      withJavaPlugin()
      withIdeaPlugin()
      addPrefix("idea.module.downloadSources = false")
      withMavenCentral()
      addTestImplementationDependency(DEPENDENCY)
    }
    assertModules("project", "project.main", "project.test")
    // Running Gradle with configuration cache for the first time lead to the task graph calculation.
    // Because of that, action execution would be much slower.
    assertSourcesDownloadedAndAttached(targetModule = "project.test", actionExecutionDeadlineMs = TimeUnit.SECONDS.toMillis(30))
  }

  @Test
  @TargetVersions("5.6+")
  fun `test download sources with configure on demand`() {
    createProjectSubFile("gradle.properties", "org.gradle.configureondemand=true")
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
  fun `test download sources from gradle sub module repository`() {
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
    assertSourcesDownloadedAndAttached(targetModule = "project.projectA.test", actionExecutionDeadlineMs = TimeUnit.SECONDS.toMillis(30))
    assertThat(getModuleLibDeps("project.projectB.test", DEPENDENCY_NAME)).isEmpty()
  }

  private fun findArtifactComponentsInGradleCache(dependencyNotation: String): Map<LibraryPathType, List<Path>> {
    val coordinates = dependencyNotation.split(":").let { UnifiedCoordinates(it[0], it[1], it[2]) }
    return GradleLocalCacheHelper.findArtifactComponents(coordinates, gradleUserHome, EnumSet.allOf(LibraryPathType::class.java))
  }

  private fun assertSourcesDownloadedAndAttached(dependencyName: String = DEPENDENCY_NAME,
                                                 dependencyJar: String = DEPENDENCY_JAR,
                                                 dependencySourcesJar: String = DEPENDENCY_SOURCES_JAR,
                                                 classFromDependency: String = CLASS_FROM_DEPENDENCY,
                                                 actionExecutionDeadlineMs: Long = DEFAULT_ATTACH_SOURCE_DEADLINE_MS,
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
    waitUntilSourcesAttached {
      try {
        ExternalSystemProgressNotificationManager.getInstance().addNotificationListener(listener)
        val callback = GradleAttachSourcesProvider().getActions(mutableListOf(library), psiFile)
          .single()
          .perform(mutableListOf(library))
          .apply { waitFor(actionExecutionDeadlineMs) }
        assertNull(callback.error)
      }
      finally {
        ExternalSystemProgressNotificationManager.getInstance().removeNotificationListener(listener)
      }
    }
    assertThat(output)
      .filteredOn { it.startsWith("Sources were downloaded to") }
      .hasSize(1)
      .allSatisfy(Consumer { assertThat(it).endsWith(dependencySourcesJar) })

    assertThat(library.getRootFiles(OrderRootType.SOURCES))
      .hasSize(1)
      .allSatisfy(Consumer { assertEquals(dependencySourcesJar, it.name) })
  }

  private fun removeCachedLibrary(cachePath: String = DEPENDENCY_SOURCES_JAR_CACHE_PATH) = gradleUserHome.resolve(cachePath).run {
    deleteIfExists()
  }

  private fun waitUntilSourcesAttached(libraryName: String = DEPENDENCY_NAME, action: () -> Unit) {
    val latch = CountDownLatch(1)
    myProject.messageBus.connect(testRootDisposable)
      .subscribe(WorkspaceModelTopics.CHANGED, object : WorkspaceModelChangeListener {
        override fun changed(event: VersionedStorageChange) {
          for (change in event.getAllChanges()) {
            if (change is EntityChange.Replaced && change.component2() is LibraryEntity) {
              val modifiedComponent = change.component2() as LibraryEntity
              if (modifiedComponent.name == libraryName && modifiedComponent.roots.any { it.type === LibraryRootTypeId.SOURCES }) {
                latch.countDown()
              }
            }
          }
        }
      })
    action.invoke()
    if (!latch.await(10, TimeUnit.SECONDS)) {
      LOG.error("A timeout has been reached while waiting for the library sources")
    }
  }
}