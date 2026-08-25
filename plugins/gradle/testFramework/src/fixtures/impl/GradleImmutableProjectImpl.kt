// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("RAW_RUN_BLOCKING")

package com.intellij.gradle.testFramework.fixtures.impl

import com.intellij.gradle.testFramework.jvm.JavaVersionRestriction
import com.intellij.gradle.testFramework.projectInfo.GradleProjectInfo
import com.intellij.gradle.testFramework.util.refreshAndAwait
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.observable.operation.core.whenOperationStarted
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.findOrCreateDirectory
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase
import com.intellij.platform.externalSystem.testFramework.ExternalSystemTestObservation.awaitOpenProjectActivity
import com.intellij.testFramework.closeProjectAsync
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.junit5.impl.TypedStoreKey
import com.intellij.testFramework.junit5.impl.TypedStoreKey.Companion.get
import com.intellij.testFramework.junit5.impl.TypedStoreKey.Companion.remove
import com.intellij.testFramework.junit5.impl.TypedStoreKey.Companion.set
import com.intellij.testFramework.openProjectAsync
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexEx
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.execution.gradleUserHomeDir
import org.jetbrains.plugins.gradle.util.getGradleProjectReloadOperation
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Identity of a reusable Gradle project. Two tests get the same [Project] instance if and only if their ids match.
 */
internal data class GradleImmutableProjectId(
  val projectId: String,
  val gradleVersion: GradleVersion,
)

/**
 * A Gradle project that is imported once and then reused by many tests.
 *
 * The project sources live in a directory that survives the JVM, under
 * `<system path>/FileTestFixture/GradleImmutableProject/<gradle version>/<project id>`, together with the `.idea`
 * directory and the workspace model caches produced by the initial import. Reopening it therefore performs no
 * Gradle import; [installGradleProjectReloadWatcher] turns an unexpected one into a fixture error.
 *
 * Test isolation comes from [files]. They keep a baseline of the project sources beside the project.
 * After each test, [GradleImmutableProjectFiles.restore] makes the project sources equal to the baseline again.
 * This makes the project *immutable* from the point of view of a test.
 */
internal class GradleImmutableProject(
  val id: GradleImmutableProjectId,
  private val projectInfo: GradleProjectInfo,
) {

  private var fixtureDisposable: Disposable? = null
  private var gradleJvmFixture: GradleJvmTestFixture? = null

  private var _files: GradleImmutableProjectFiles? = null
  val files: GradleImmutableProjectFiles
    get() = requireNotNull(_files) { "Gradle project '$id' isn't opened" }

  private var _project: Project? = null
  val project: Project
    get() = requireNotNull(_project) { "Gradle project '$id' isn't opened" }

  suspend fun open(javaVersionRestriction: JavaVersionRestriction) {
    val fixtureDisposable = Disposer.newDisposable("GradleImmutableProject($id)")
    this.fixtureDisposable = fixtureDisposable

    // The Gradle JVM has to outlive a single test: the project keeps a reference to it, and re-registering the
    // SDK for every test would change the project roots and drop the indexes that reuse is supposed to preserve.
    val gradleJvmFixture = GradleJvmTestFixture(id.gradleVersion, javaVersionRestriction)
    this.gradleJvmFixture = gradleJvmFixture
    gradleJvmFixture.setUp()

    val eelDescriptor = PathManager.getSystemDir().getEelDescriptor()
    val gradleUserHome = withContext(Dispatchers.IO) {
      gradleUserHomeDir(eelDescriptor).findOrCreateDirectory().toRealPath()
    }
    VfsRootAccess.allowRootAccess(fixtureDisposable, gradleUserHome.toString())

    val files = createFiles()
    _files = files
    gradleJvmFixture.withProjectSettingsConfigurator {
      files.setUp()
    }

    installGradleProjectReloadWatcher(fixtureDisposable)

    _project = awaitOpenProjectActivity {
      openProjectAsync(files.projectRoot)
    }
  }

  fun close() {
    val project = _project
    val files = _files
    val gradleJvmFixture = gradleJvmFixture
    val fixtureDisposable = fixtureDisposable
    runAll(
      {
        val index = ApplicationManager.getApplication().serviceIfCreated<FileBasedIndex>() as? FileBasedIndexEx
        index?.waitUntilIndicesAreInitialized()
      },
      { if (project != null) runBlocking { files?.root?.refreshAndAwait() } },
      { project?.let { runBlocking { it.closeProjectAsync() } } },
      { _project = null },
      { files?.tearDown() },
      { _files = null },
      { gradleJvmFixture?.tearDown() },
      { this.gradleJvmFixture = null },
      { fixtureDisposable?.let { Disposer.dispose(it) } },
      { this.fixtureDisposable = null },
    )
  }

  private fun createFiles(): GradleImmutableProjectFiles {
    return GradleImmutableProjectFiles(
      relativePath = "GradleImmutableProject/${id.gradleVersion.version}/${id.projectId}",
      projectInfo = projectInfo,
      // The caches of Gradle and the persisted project state must survive the restore after each test.
      excludedFilePatterns = listOf("glob:**/.gradle{,/**}", "glob:**/build{,/**}", "glob:**/.idea{,/**}"),
    )
  }

  private fun installGradleProjectReloadWatcher(parentDisposable: Disposable) {
    val operation = getGradleProjectReloadOperation(parentDisposable) { id, _ -> id.findProject() == _project }
    operation.whenOperationStarted {
      files.addIllegalOperationError("Unexpected project reload: $operation")
    }
  }
}

/**
 * Keeps at most one [GradleImmutableProject] open, the way
 * [com.intellij.gradle.testFramework.GradleProjectBaseTestCase] does for inheritance-based tests.
 *
 * A test that asks for a different [GradleImmutableProjectId] closes the previous project first.
 *
 * The holder lives in the JUnit context of a test class. A nested class uses the holder of its outer class.
 */
internal class GradleImmutableProjectHolder {

  private var openedProject: GradleImmutableProject? = null

  /**
   * The parent of the global watchers. They have no project filter, so only one set can exist at a time.
   */
  private var watchersDisposable: Disposable? = null

  suspend fun acquire(
    id: GradleImmutableProjectId,
    projectInfo: GradleProjectInfo,
    javaVersionRestriction: JavaVersionRestriction,
  ): GradleImmutableProject {
    val openedProject = openedProject
    if (openedProject?.id == id) {
      return openedProject
    }
    evict()
    val watchersDisposable = Disposer.newDisposable("GradleImmutableProjectHolder")
    this.watchersDisposable = watchersDisposable
    WorkspaceModelCacheImpl.forceEnableCaching(watchersDisposable)
    ExternalSystemImportingTestCase.installExecutionOutputPrinter(watchersDisposable)

    val project = GradleImmutableProject(id, projectInfo)
    this.openedProject = project
    try {
      project.open(javaVersionRestriction)
    }
    catch (exception: Throwable) {
      runCatching { evict() }
        .onFailure(exception::addSuppressed)
      throw exception
    }
    return project
  }

  /**
   * Reverts everything a test has changed, keeping the project open for the next one.
   * A project that cannot be reverted to its original state, or that has an error, is closed instead.
   */
  fun release() {
    val files = openedProject?.files ?: return
    try {
      files.restore()
    }
    catch (exception: Throwable) {
      runCatching { evict() }
        .onFailure(exception::addSuppressed)
      throw exception
    }
    if (files.hasErrors()) {
      evict()
    }
  }

  /**
   * Closes the open project, then removes the global watchers.
   */
  fun evict() {
    val project = openedProject
    val watchersDisposable = watchersDisposable
    runAll(
      { openedProject = null },
      { project?.close() },
      { this.watchersDisposable = null },
      { watchersDisposable?.let { Disposer.dispose(it) } },
    )
  }

  companion object {

    private val HOLDER_KEY = TypedStoreKey.createKey<GradleImmutableProjectHolder>()

    /**
     * @return the holder of [context], or of the nearest parent context that has one.
     */
    fun find(context: ExtensionContext): GradleImmutableProjectHolder? {
      return context[HOLDER_KEY]
    }

    /**
     * Puts a new holder in [context] when no parent context has one.
     */
    fun install(context: ExtensionContext) {
      if (find(context) == null) {
        context[HOLDER_KEY] = GradleImmutableProjectHolder()
      }
    }

    /**
     * Closes the open project, because the leak trackers run at the end of each class.
     * The context that has the holder also removes it.
     */
    fun uninstall(context: ExtensionContext) {
      val holder = find(context) ?: return
      try {
        holder.evict()
      }
      finally {
        // The removal does not search the parent contexts.
        context.remove(HOLDER_KEY)
      }
    }
  }
}
