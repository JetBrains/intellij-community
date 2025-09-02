// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.action

import com.intellij.buildsystem.model.unified.UnifiedCoordinates
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.externalSystem.model.project.LibraryPathType
import com.intellij.openapi.externalSystem.util.DEFAULT_SYNC_TIMEOUT
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.storage.impl.VersionedStorageChangeInternal
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.util.asDisposable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.internal.daemon.DaemonState
import org.jetbrains.plugins.gradle.internal.daemon.getDaemonsStatus
import org.jetbrains.plugins.gradle.service.cache.GradleLocalCacheHelper
import org.jetbrains.plugins.gradle.testFramework.util.ExternalSystemExecutionTracer
import org.jetbrains.plugins.gradle.testFramework.util.createBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.importProject
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test
import java.nio.file.Path
import java.util.EnumSet
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
  @TargetVersions("6.0+", "!8.12") // The Gradle Daemon below version 6.0 is unstable and causes test fluctuations
  fun `test daemon reused for source downloading`(): Unit = runBlocking {
    // a custom Gradle User Home is required to isolate execution of the test from different tests running at the same time
    overrideGradleUserHome("test-daemon-reused-for-source-downloading")

    val libraryGroup = "org.apache.commons"
    val libraryName = "commons-lang3"
    val libraryVersion = "3.17.0"
    val libraryHash = "f409092a9f723034a839327029255900a19742b4"
    removeCachedLibrary(
      "caches/modules-2/files-2.1/$libraryGroup/$libraryName/$libraryVersion/$libraryHash/$libraryName-$libraryVersion-sources.jar"
    )

    assertNull(findDaemon())
    importProject {
      withJavaPlugin()
      withMavenCentral()
      addTestImplementationDependency(DEPENDENCY)
      addTestImplementationDependency("$libraryGroup:$libraryName:$libraryVersion")
    }

    val daemonUsedForSync = findDaemon()
    assertNotNull(daemonUsedForSync)

    assertSourcesDownloadedAndAttached(targetModule = "project.test")
    daemonUsedForSync!!.assertReused()

    assertSourcesDownloadedAndAttached(
      targetModule = "project.test",
      dependencyName = "Gradle: $libraryGroup:$libraryName:$libraryVersion",
      dependencyJar = "$libraryName-$libraryVersion.jar",
      dependencySourcesJar = "$libraryName-$libraryVersion-sources.jar",
      classFromDependency = "org.apache.commons.lang3.StringUtils"
    )
    daemonUsedForSync.assertReused()
  }

  private fun DaemonState.assertReused() {
    assertEquals(this, findDaemon())
  }

  @Test
  fun `test download sources dynamic task`(): Unit = runBlocking {
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
  fun `test sources available in gradle cache after task execution`(): Unit = runBlocking {
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
  fun `test download sources with configuration cache`(): Unit = runBlocking {
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
  fun `test download sources with configure on demand`(): Unit = runBlocking {
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
  fun `test download sources from gradle sub module repository`(): Unit = runBlocking {
    createSettingsFile("include 'projectA', 'projectB' ")
    createBuildFile("projectA") {
      withJavaPlugin()
      withIdeaPlugin()
      addPrefix("idea.module.downloadSources = false")
      withMavenCentral()
      addTestImplementationDependency(DEPENDENCY)
    }
    createBuildFile("projectB") {
      withJavaPlugin()
    }
    importProject()
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

  private suspend fun assertSourcesDownloadedAndAttached(
    dependencyName: String = DEPENDENCY_NAME,
    dependencyJar: String = DEPENDENCY_JAR,
    dependencySourcesJar: String = DEPENDENCY_SOURCES_JAR,
    classFromDependency: String = CLASS_FROM_DEPENDENCY,
    actionExecutionDeadlineMs: Long = DEFAULT_ATTACH_SOURCE_DEADLINE_MS,
    targetModule: String,
  ) {
    val library: LibraryOrderEntry = getModuleLibDeps(targetModule, dependencyName).single()
    assertThat(library.getRootFiles(OrderRootType.CLASSES))
      .hasSize(1)
      .allSatisfy(Consumer { assertEquals(dependencyJar, it.name) })

    IndexingTestUtil.waitUntilIndexesAreReady(myProject)

    val psiFile = runReadAction {
      JavaPsiFacade.getInstance(myProject).findClass(classFromDependency, GlobalSearchScope.allScope(myProject))!!.containingFile
    }

    val tracker = ExternalSystemExecutionTracer()
    tracker.traceExecution(ExternalSystemExecutionTracer.PrintOutputMode.ON_EXCEPTION) {
      waitUntilSourcesAttached(dependencyName) {
        coroutineScope {
          val attachSourcesProvider = GradleAttachSourcesProvider(this)
          val attachSourcesActions = attachSourcesProvider.getActions(mutableListOf(library), psiFile)
          val attachSourcesAction = attachSourcesActions.single()
          val attachSourcesCallback = attachSourcesAction.perform(mutableListOf(library))
          withContext(Dispatchers.IO) {
            attachSourcesCallback.waitFor(actionExecutionDeadlineMs)
          }
          assertNull(attachSourcesCallback.error)
        }
      }
    }
    assertThat(tracker.output)
      .filteredOn { it.startsWith("Artifact was downloaded to") }
      .hasSize(1)
      .allSatisfy(Consumer { assertThat(it).endsWith(dependencySourcesJar) })

    assertThat(library.getRootFiles(OrderRootType.SOURCES))
      .hasSize(1)
      .allSatisfy(Consumer { assertEquals(dependencySourcesJar, it.name) })
  }

  private fun removeCachedLibrary(cachePath: String = DEPENDENCY_SOURCES_JAR_CACHE_PATH) = gradleUserHome.resolve(cachePath).run {
    deleteIfExists()
  }

  private suspend fun waitUntilSourcesAttached(libraryName: String = DEPENDENCY_NAME, action: suspend () -> Unit) {
    coroutineScope {
      val deferred = CompletableDeferred<Boolean>()
      myProject.messageBus.connect(this.asDisposable())
        .subscribe(WorkspaceModelTopics.CHANGED, object : WorkspaceModelChangeListener {
          override fun changed(event: VersionedStorageChange) {
            for (change in (event as VersionedStorageChangeInternal).getAllChanges()) {
              if (change is EntityChange.Replaced && change.component2() is LibraryEntity) {
                val modifiedComponent = change.component2() as LibraryEntity
                if (modifiedComponent.name == libraryName && modifiedComponent.roots.any { it.type === LibraryRootTypeId.SOURCES }) {
                  deferred.complete(true)
                }
              }
            }
          }
        })
      action.invoke()
      withTimeout(DEFAULT_SYNC_TIMEOUT) {
        deferred.await()
      }
    }
  }

  private fun findDaemon(): DaemonState? {
    val daemons = getDaemonsStatus(setOf(gradleUserHome.toCanonicalPath()))
    if (daemons.isEmpty()) {
      return null
    }
    return daemons.find {
      if (gradleVersion != it.version) {
        return@find false
      }
      val daemonUserHome = it.registryDir?.toPath() ?: throw IllegalStateException("Gradle daemon user home should not be null")
      if (daemonUserHome != gradleUserHome.resolve("daemon")) {
        return@find false
      }
      val daemonJdkHome = it.javaHome?.canonicalPath ?: throw IllegalStateException("Gradle JDK should never be null")
      gradleJdkHome == daemonJdkHome
    }
  }
}