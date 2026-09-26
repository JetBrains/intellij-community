// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsConfiguration.StandardConfirmation.ADD
import com.intellij.openapi.vcs.VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY
import com.intellij.openapi.vcs.VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY
import com.intellij.openapi.vcs.actions.VcsContextFactory
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.VcsIgnoreManager
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.peer.impl.VcsContextFactoryImpl
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.fileOrDirInProjectFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.registerOrReplaceServiceInstance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * [ExternallyAddedFilesProcessorImpl] keeps the path of every file that a VFS refresh created.
 * When the change list manager reports it is up to date, the processor adds every unversioned file under a kept path to the VCS.
 *
 * The tests call [ExternallyAddedFilesProcessorImpl.filesChanged] and [ExternallyAddedFilesProcessorImpl.unchangedFileStatusChanged]
 * directly. They do not call `install`, so no message bus and no VFS post processor take part.
 */
@TestApplication
internal class ExternallyAddedFilesProcessorImplTest {
  private val projectFixture = projectFixture()
  private val baseDirFixture = projectFixture.fileOrDirInProjectFixture(".")

  private val project: Project get() = projectFixture.get()
  private val baseDir: VirtualFile get() = baseDirFixture.get()

  private lateinit var changeListManager: TestChangeListManager
  private lateinit var ignoreManager: TestVcsIgnoreManager
  private lateinit var vcs: MockAbstractVcs
  private lateinit var processor: ExternallyAddedFilesProcessorImpl
  private lateinit var configDir: VirtualFile
  private val addedFiles = mutableListOf<VirtualFile>()

  @BeforeEach
  fun setUp(@TestDisposable disposable: Disposable) {
    // The lightweight @TestApplication environment does not load VcsExtensions.xml here.
    ApplicationManager.getApplication()
      .registerOrReplaceServiceInstance(VcsContextFactory::class.java, VcsContextFactoryImpl(), disposable)

    changeListManager = TestChangeListManager()
    ignoreManager = TestVcsIgnoreManager()
    project.registerOrReplaceServiceInstance(ChangeListManager::class.java, changeListManager, disposable)
    project.registerOrReplaceServiceInstance(VcsIgnoreManager::class.java, ignoreManager, disposable)

    // The processor adds a file only under these two settings.
    vcs = MockAbstractVcs(project)
    ProjectLevelVcsManager.getInstance(project).getStandardConfirmation(ADD, vcs).value = DO_ACTION_SILENTLY
    VcsConfiguration.getInstance(project).ADD_EXTERNAL_FILES_SILENTLY = true

    configDir = createProjectConfigDir(project)
    processor = ExternallyAddedFilesProcessorImpl(project, disposable, vcs) { addedFiles.addAll(it) }
  }

  @Test
  @Timeout(30)
  fun `an unversioned file under a created directory goes to the vcs`(): Unit = timeoutRunBlocking {
    val underExternalDir = unversionedPath("external/a.txt")
    changeListManager.unversionedPaths = listOf(underExternalDir)

    processor.filesChanged(listOf(createEvent("external", isDirectory = true)))
    processor.unchangedFileStatusChanged(true)

    assertEquals(listOf(underExternalDir.file), addedFiles)
  }

  /**
   * The defect that IJPL-252739 reports. The processor must not resolve a [VirtualFile] for a path that fails the ancestor filter.
   * A project can hold hundreds of thousands of unversioned paths, and every resolve reads the VFS.
   */
  @Test
  @Timeout(30)
  fun `a path outside every created directory is never resolved`(): Unit = timeoutRunBlocking {
    val underExternalDir = unversionedPath("external/a.txt")
    val outsideExternalDir = unversionedPath("other/b.txt")
    changeListManager.unversionedPaths = listOf(underExternalDir, outsideExternalDir)

    processor.filesChanged(listOf(createEvent("external", isDirectory = true)))
    processor.unchangedFileStatusChanged(true)

    assertEquals(listOf(underExternalDir.file), addedFiles)
    assertEquals(1, underExternalDir.resolveCount, "the matched path must resolve to a file")
    assertEquals(0, outsideExternalDir.resolveCount, "a path outside the created directory must not touch the VFS")
  }

  /**
   * The ignore check is cheaper than a VFS resolve, so the processor must apply it first.
   */
  @Test
  @Timeout(30)
  fun `a potentially ignored path is never resolved`(): Unit = timeoutRunBlocking {
    val ignored = unversionedPath("external/ignored.txt")
    changeListManager.unversionedPaths = listOf(ignored)
    ignoreManager.ignoredPaths.add(ignored.path)

    processor.filesChanged(listOf(createEvent("external", isDirectory = true)))
    processor.unchangedFileStatusChanged(true)

    assertTrue(addedFiles.isEmpty(), "an ignored path must not go to the VCS")
    assertEquals(0, ignored.resolveCount, "an ignored path must not touch the VFS")
  }

  @Test
  @Timeout(30)
  fun `no kept path leaves the change list manager alone`() {
    processor.unchangedFileStatusChanged(true)

    assertEquals(0, changeListManager.unversionedRequestCount)
    assertTrue(addedFiles.isEmpty())
  }

  @Test
  @Timeout(30)
  fun `a stale change list keeps every path for the next update`(): Unit = timeoutRunBlocking {
    val underExternalDir = unversionedPath("external/a.txt")
    changeListManager.unversionedPaths = listOf(underExternalDir)

    processor.filesChanged(listOf(createEvent("external", isDirectory = true)))
    processor.unchangedFileStatusChanged(false)

    assertEquals(0, changeListManager.unversionedRequestCount, "a stale change list gives no unversioned path")

    processor.unchangedFileStatusChanged(true)

    assertEquals(listOf(underExternalDir.file), addedFiles)
  }

  @Test
  @Timeout(30)
  fun `an update drops every kept path`(): Unit = timeoutRunBlocking {
    val underExternalDir = unversionedPath("external/a.txt")
    changeListManager.unversionedPaths = listOf(underExternalDir)

    processor.filesChanged(listOf(createEvent("external", isDirectory = true)))
    processor.unchangedFileStatusChanged(true)
    addedFiles.clear()

    processor.unchangedFileStatusChanged(true)

    assertEquals(1, changeListManager.unversionedRequestCount, "the second update must find no kept path")
    assertTrue(addedFiles.isEmpty())
  }

  @Test
  @Timeout(30)
  fun `the processor keeps no path when the user turned the silent add off`(): Unit = timeoutRunBlocking {
    ProjectLevelVcsManager.getInstance(project).getStandardConfirmation(ADD, vcs).value = DO_NOTHING_SILENTLY
    val underExternalDir = unversionedPath("external/a.txt")
    changeListManager.unversionedPaths = listOf(underExternalDir)

    processor.filesChanged(listOf(createEvent("external", isDirectory = true)))
    processor.unchangedFileStatusChanged(true)

    assertEquals(0, changeListManager.unversionedRequestCount)
    assertTrue(addedFiles.isEmpty())
  }

  /**
   * The IDE writes its own configuration files, so a refresh under the project configuration directory is not an external add.
   */
  @Test
  @Timeout(30)
  fun `a file under the project configuration directory is not external`(): Unit = timeoutRunBlocking {
    val underConfigDir = unversionedPath("${configDir.name}/runConfigurations/a.xml")
    changeListManager.unversionedPaths = listOf(underConfigDir)

    processor.filesChanged(listOf(createEvent("runConfigurations", isDirectory = true, parent = configDir)))
    processor.unchangedFileStatusChanged(true)

    assertEquals(0, changeListManager.unversionedRequestCount)
    assertTrue(addedFiles.isEmpty())
  }

  /**
   * The IDE reports its own file operation without a refresh requestor. Only a refresh brings an externally added file.
   */
  @Test
  @Timeout(30)
  fun `a file that the ide created is not external`(): Unit = timeoutRunBlocking {
    val underExternalDir = unversionedPath("external/a.txt")
    changeListManager.unversionedPaths = listOf(underExternalDir)

    processor.filesChanged(listOf(VFileCreateEvent(this, baseDir, "external", true, null, null, null)))
    processor.unchangedFileStatusChanged(true)

    assertEquals(0, changeListManager.unversionedRequestCount)
    assertTrue(addedFiles.isEmpty())
  }

  private fun createEvent(name: String, isDirectory: Boolean, parent: VirtualFile = baseDir): VFileCreateEvent =
    VFileCreateEvent(VFileEvent.REFRESH_REQUESTOR, parent, name, isDirectory, null, null, null)

  private fun unversionedPath(relativePath: String): CountingFilePath =
    CountingFilePath("${baseDir.path}/$relativePath")
}
