// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.actions.VcsContextFactory
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.VcsIgnoreManager
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.peer.impl.VcsContextFactoryImpl
import com.intellij.testFramework.VfsTestUtil
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
 * [ProjectConfigurationFilesProcessorImpl] takes a project configuration file out of the "Add to VCS" dialog.
 * It remembers that it found such a file. When the change list manager reports it is up to date, the processor
 * looks for every unversioned configuration file and proposes to add it to the VCS.
 *
 * A configuration file is a file under the project configuration directory, or a file with the `ipr` or the `iml` extension.
 *
 * The tests call [ProjectConfigurationFilesProcessorImpl.filterNotProjectConfigurationFiles] and
 * [ProjectConfigurationFilesProcessorImpl.unchangedFileStatusChanged] directly. They do not call `install`,
 * so no message bus takes part.
 */
@TestApplication
internal class ProjectConfigurationFilesProcessorImplTest {
  private val projectFixture = projectFixture()
  private val baseDirFixture = projectFixture.fileOrDirInProjectFixture(".")

  private val project: Project get() = projectFixture.get()
  private val baseDir: VirtualFile get() = baseDirFixture.get()

  private lateinit var changeListManager: TestChangeListManager
  private lateinit var ignoreManager: TestVcsIgnoreManager
  private lateinit var processor: ProjectConfigurationFilesProcessorImpl
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

    // The fixture project is not under a VCS, so the processor proposes the add in a notification.
    // Tell the processor that it asked the user before, to keep the notification out of the tests.
    PropertiesComponent.getInstance(project).setValue(ASKED_SHARE_PROJECT_CONFIGURATION_FILES_PROPERTY, true)

    configDir = createProjectConfigDir(project)
    processor = ProjectConfigurationFilesProcessorImpl(project, disposable, MockAbstractVcs(project)) { }
  }

  @Test
  @Timeout(30)
  fun `the filter keeps only a file that is not a project configuration file`() {
    val underConfigDir = VfsTestUtil.createFile(configDir, "misc.xml")
    val projectFile = VfsTestUtil.createFile(baseDir, "a.ipr")
    val moduleFile = VfsTestUtil.createFile(baseDir, "a.iml")
    val sourceFile = VfsTestUtil.createFile(baseDir, "a.txt")

    val kept = processor.filterNotProjectConfigurationFiles(listOf(underConfigDir, projectFile, moduleFile, sourceFile))

    assertEquals(listOf(sourceFile), kept)
  }

  @Test
  @Timeout(30)
  fun `the filter keeps an ignored project configuration file`() {
    val projectFile = VfsTestUtil.createFile(baseDir, "a.ipr")
    ignoreManager.ignoredPaths.add(projectFile.path)

    val kept = processor.filterNotProjectConfigurationFiles(listOf(projectFile))

    assertEquals(listOf(projectFile), kept)
  }

  @Test
  @Timeout(30)
  fun `no found configuration file leaves the change list manager alone`() {
    val sourceFile = VfsTestUtil.createFile(baseDir, "a.txt")
    processor.filterNotProjectConfigurationFiles(listOf(sourceFile))

    processor.unchangedFileStatusChanged(true)

    assertEquals(0, changeListManager.unversionedRequestCount)
  }

  /**
   * The defect that IJPL-252739 reports. The processor must not resolve a [VirtualFile] for a path that is not a configuration file.
   * A project can hold hundreds of thousands of unversioned paths, and every resolve reads the VFS.
   */
  @Test
  @Timeout(30)
  fun `a path that is not a configuration file is never resolved`() {
    val moduleFile = unversionedPath("a.iml")
    val underConfigDir = unversionedPath("${configDir.name}/misc.xml")
    val sourceFile = unversionedPath("a.txt")
    changeListManager.unversionedPaths = listOf(moduleFile, underConfigDir, sourceFile)

    findConfigurationFile()
    processor.unchangedFileStatusChanged(true)

    assertEquals(1, moduleFile.resolveCount, "a module file is a configuration file")
    assertEquals(1, underConfigDir.resolveCount, "a file under the project configuration directory is a configuration file")
    assertEquals(0, sourceFile.resolveCount, "a source file must not touch the VFS")
  }

  @Test
  @Timeout(30)
  fun `an ignored configuration path is never resolved`() {
    val moduleFile = unversionedPath("a.iml")
    changeListManager.unversionedPaths = listOf(moduleFile)
    ignoreManager.ignoredPaths.add(moduleFile.path)

    findConfigurationFile()
    processor.unchangedFileStatusChanged(true)

    assertEquals(0, moduleFile.resolveCount, "an ignored path must not touch the VFS")
  }

  @Test
  @Timeout(30)
  fun `a stale change list keeps the found configuration file for the next update`() {
    changeListManager.unversionedPaths = listOf(unversionedPath("a.iml"))

    findConfigurationFile()
    processor.unchangedFileStatusChanged(false)

    assertEquals(0, changeListManager.unversionedRequestCount, "a stale change list gives no unversioned path")

    processor.unchangedFileStatusChanged(true)

    assertEquals(1, changeListManager.unversionedRequestCount)
  }

  @Test
  @Timeout(30)
  fun `an update drops the found configuration file`() {
    changeListManager.unversionedPaths = listOf(unversionedPath("a.iml"))

    findConfigurationFile()
    processor.unchangedFileStatusChanged(true)
    processor.unchangedFileStatusChanged(true)

    assertEquals(1, changeListManager.unversionedRequestCount, "the second update must find no configuration file")
  }

  /** Makes the processor remember that it found a project configuration file. */
  private fun findConfigurationFile() {
    processor.filterNotProjectConfigurationFiles(listOf(VfsTestUtil.createFile(baseDir, "found.iml")))
  }

  private fun unversionedPath(relativePath: String): CountingFilePath =
    CountingFilePath("${baseDir.path}/$relativePath")
}
