// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ignore

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CountingFilePath
import com.intellij.openapi.vcs.TestChangeListManager
import com.intellij.openapi.vcs.TestVcsIgnoreManager
import com.intellij.openapi.vcs.actions.VcsContextFactory
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.IgnoredFileProvider
import com.intellij.openapi.vcs.changes.VcsIgnoreManager
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs
import com.intellij.openapi.vcs.changes.ignore.IgnoreConfigurationProperty.ASKED_MANAGE_IGNORE_FILES_PROPERTY
import com.intellij.openapi.vcs.createProjectConfigDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.peer.impl.VcsContextFactoryImpl
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.fileOrDirInProjectFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.registerOrReplaceServiceInstance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * [IgnoreFilesProcessorImpl] keeps the path of every potentially ignored file that a VFS event reports.
 * When the change list manager reports it is up to date, the processor writes an ignore file for every unversioned
 * file under a kept path.
 *
 * The processor splits a kept path in two sets. A path under the project configuration directory always goes to an
 * ignore file. A path outside the directory needs the `vcs.ignorefile.generation` registry key.
 *
 * [IgnoreFilesProcessorImpl.filesChanged] and [IgnoreFilesProcessorImpl.unchangedFileStatusChanged] return at once in
 * the unit test mode. The tests call [IgnoreFilesProcessorImpl.collectPotentiallyIgnoredPaths] and
 * [IgnoreFilesProcessorImpl.processCollectedPaths] instead. They do not call `install`, so no message bus and no VFS
 * post processor take part.
 */
@TestApplication
internal class IgnoreFilesProcessorImplTest {
  private val projectFixture = projectFixture()
  private val baseDirFixture = projectFixture.fileOrDirInProjectFixture(".")

  private val project: Project get() = projectFixture.get()
  private val baseDir: VirtualFile get() = baseDirFixture.get()

  private lateinit var changeListManager: TestChangeListManager
  private lateinit var ignoreManager: TestVcsIgnoreManager
  private lateinit var processor: IgnoreFilesProcessorImpl
  private lateinit var configDir: VirtualFile

  @BeforeEach
  fun setUp(@TestDisposable disposable: Disposable) {
    // The lightweight @TestApplication environment does not load VcsExtensions.xml here.
    ApplicationManager.getApplication()
      .registerOrReplaceServiceInstance(VcsContextFactory::class.java, VcsContextFactoryImpl(), disposable)

    changeListManager = TestChangeListManager()
    ignoreManager = TestVcsIgnoreManager()
    project.registerOrReplaceServiceInstance(ChangeListManager::class.java, changeListManager, disposable)
    project.registerOrReplaceServiceInstance(VcsIgnoreManager::class.java, ignoreManager, disposable)

    // The processor proposes the write in a notification. Tell it that it asked the user before, to keep the
    // notification out of the tests.
    PropertiesComponent.getInstance(project).setValue(ASKED_MANAGE_IGNORE_FILES_PROPERTY, true)

    // The tests assert the filter order and not the ignore file content, so mask every ignored file provider.
    // ProjectExcludesIgnoredFileProvider caches the excluded roots of the project under the project key.
    // The cache drops the key on the project close only, and a fixture project never opens.
    ExtensionTestUtil.maskExtensions(IgnoredFileProvider.IGNORE_FILE, emptyList(), disposable)

    configDir = createProjectConfigDir(project)
    // The processor reads the ignore manager in its constructor, so it must come after the service registration.
    processor = IgnoreFilesProcessorImpl(project, disposable, MockAbstractVcs(project))
  }

  @Test
  @Timeout(30)
  fun `an unversioned file under the project configuration directory reaches an ignore file`(): Unit = timeoutRunBlocking {
    val underConfigDir = unversionedPath("${configDir.name}/runConfigurations/a.xml")
    changeListManager.unversionedPaths = listOf(underConfigDir)

    processor.collectPotentiallyIgnoredPaths(listOf(ignoredDirEvent(configDir, "runConfigurations")))
    processor.processCollectedPaths(true)

    assertEquals(1, changeListManager.unversionedRequestCount)
    assertEquals(1, underConfigDir.resolveCount, "the matched path must resolve to a file")
  }

  /**
   * The defect that IJPL-252739 reports. The processor must not resolve a [VirtualFile] for a path that fails the
   * ancestor filter. A project can hold hundreds of thousands of unversioned paths, and every resolve reads the VFS.
   */
  @Test
  @Timeout(30)
  fun `a path outside every collected path is never resolved`(): Unit = timeoutRunBlocking {
    val underConfigDir = unversionedPath("${configDir.name}/runConfigurations/a.xml")
    val outsideConfigDir = unversionedPath("src/b.txt")
    changeListManager.unversionedPaths = listOf(underConfigDir, outsideConfigDir)

    processor.collectPotentiallyIgnoredPaths(listOf(ignoredDirEvent(configDir, "runConfigurations")))
    processor.processCollectedPaths(true)

    assertEquals(1, underConfigDir.resolveCount, "the matched path must resolve to a file")
    assertEquals(0, outsideConfigDir.resolveCount, "a path outside the collected path must not touch the VFS")
  }

  /**
   * The processor reads the unversioned paths lazily, so an update with no collected path must not touch the change
   * list manager at all.
   */
  @Test
  @Timeout(30)
  fun `no collected path leaves the change list manager alone`() {
    changeListManager.unversionedPaths = listOf(unversionedPath("${configDir.name}/a.xml"))

    processor.processCollectedPaths(true)

    assertEquals(0, changeListManager.unversionedRequestCount)
  }

  @Test
  @Timeout(30)
  fun `an event for a file that is not potentially ignored is dropped`(): Unit = timeoutRunBlocking {
    changeListManager.unversionedPaths = listOf(unversionedPath("${configDir.name}/runConfigurations/a.xml"))

    // The event reports a directory that the ignore manager does not report as potentially ignored.
    processor.collectPotentiallyIgnoredPaths(listOf(createEvent(configDir, "runConfigurations")))
    processor.processCollectedPaths(true)

    assertEquals(0, changeListManager.unversionedRequestCount)
  }

  /**
   * Only a create, a move, a copy, or a rename brings a new potentially ignored file.
   */
  @Test
  @Timeout(30)
  fun `a delete event is dropped`(): Unit = timeoutRunBlocking {
    val deletedDir = VfsTestUtil.createDir(configDir, "runConfigurations")
    ignoreManager.ignoredPaths.add(deletedDir.path)
    changeListManager.unversionedPaths = listOf(unversionedPath("${configDir.name}/runConfigurations/a.xml"))

    processor.collectPotentiallyIgnoredPaths(listOf(VFileDeleteEvent(VFileEvent.REFRESH_REQUESTOR, deletedDir)))
    processor.processCollectedPaths(true)

    assertEquals(0, changeListManager.unversionedRequestCount)
  }

  @Test
  @Timeout(30)
  fun `a stale change list keeps every collected path for the next update`(): Unit = timeoutRunBlocking {
    val underConfigDir = unversionedPath("${configDir.name}/runConfigurations/a.xml")
    changeListManager.unversionedPaths = listOf(underConfigDir)

    processor.collectPotentiallyIgnoredPaths(listOf(ignoredDirEvent(configDir, "runConfigurations")))
    processor.processCollectedPaths(false)

    assertEquals(0, changeListManager.unversionedRequestCount, "a stale change list gives no unversioned path")

    processor.processCollectedPaths(true)

    assertEquals(1, underConfigDir.resolveCount)
  }

  @Test
  @Timeout(30)
  fun `an update drops every collected path`(): Unit = timeoutRunBlocking {
    val underConfigDir = unversionedPath("${configDir.name}/runConfigurations/a.xml")
    changeListManager.unversionedPaths = listOf(underConfigDir)

    processor.collectPotentiallyIgnoredPaths(listOf(ignoredDirEvent(configDir, "runConfigurations")))
    processor.processCollectedPaths(true)
    processor.processCollectedPaths(true)

    assertEquals(1, changeListManager.unversionedRequestCount, "the second update must find no collected path")
    assertEquals(1, underConfigDir.resolveCount)
  }

  /**
   * The registry key is off by default, so the processor keeps a path outside the project configuration directory
   * without any work on it.
   */
  @Test
  @Timeout(30)
  fun `a path outside the project configuration directory waits for the registry key`(): Unit = timeoutRunBlocking {
    val outsideConfigDir = unversionedPath("src/a.txt")
    changeListManager.unversionedPaths = listOf(outsideConfigDir)

    processor.collectPotentiallyIgnoredPaths(listOf(ignoredDirEvent(baseDir, "src")))
    processor.processCollectedPaths(true)

    assertEquals(0, changeListManager.unversionedRequestCount)
    assertEquals(0, outsideConfigDir.resolveCount)
  }

  @Test
  @Timeout(30)
  @RegistryKey(key = "vcs.ignorefile.generation", value = "true")
  fun `the registry key lets a path outside the project configuration directory through`(): Unit = timeoutRunBlocking {
    val outsideConfigDir = unversionedPath("src/a.txt")
    changeListManager.unversionedPaths = listOf(outsideConfigDir)

    processor.collectPotentiallyIgnoredPaths(listOf(ignoredDirEvent(baseDir, "src")))
    processor.processCollectedPaths(true)

    assertEquals(1, changeListManager.unversionedRequestCount)
    assertEquals(1, outsideConfigDir.resolveCount)
  }

  /** Reports the created directory as potentially ignored, and gives the event that reports the creation. */
  private fun ignoredDirEvent(parent: VirtualFile, name: String): VFileCreateEvent {
    ignoreManager.ignoredPaths.add("${parent.path}/$name")
    return createEvent(parent, name)
  }

  private fun createEvent(parent: VirtualFile, name: String): VFileCreateEvent =
    VFileCreateEvent(VFileEvent.REFRESH_REQUESTOR, parent, name, true, null, null, null)

  private fun unversionedPath(relativePath: String): CountingFilePath =
    CountingFilePath("${baseDir.path}/$relativePath")
}
