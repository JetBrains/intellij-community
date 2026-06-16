// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.shelf

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.vcs.VcsTestUtil
import com.intellij.openapi.vcs.changes.patch.CreatePatchCommitExecutor.ShelfPatchBuilder
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.project.stateStore
import com.intellij.concurrency.JobScheduler
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.createTestOpenProjectOptions
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.RunMethodInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.io.createDirectories
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively

@TestApplication
@Suppress("DEPRECATION")
@RunInEdt(allMethods = false)
@OptIn(ExperimentalPathApi::class)
class ShelveChangesManagerTest {
  private lateinit var shelvedChangesManager: ShelveChangesManager

  private lateinit var project: Project

  @TempDir
  lateinit var tempDir: Path

  @BeforeEach
  fun setUp() {
    // test data expects not directory-based project
    val baseDir = tempDir.resolve("project").createDirectories()
    val projectFile = baseDir.resolve("p.ipr")
    val shelfDir = baseDir.resolve(".shelf")
    val testDataFile = Paths.get("${VcsTestUtil.getTestDataPath()}/shelf/shelvedChangeLists")
    testDataFile.copyToRecursively(shelfDir, followLinks = true)

    refreshProjectDir(projectFile)

    project = ProjectManagerEx.getInstanceEx().openProject(projectFile, createTestOpenProjectOptions())!!
    shelvedChangesManager = ShelveChangesManager.getInstance(project)
    runBlocking {
      shelvedChangesManager.scheduleShelvesLoading().await()
    }
    assertThat(shelvedChangesManager.shelvedChangeLists).isNotEmpty
  }

  @AfterEach
  fun closeProject() {
    if (::project.isInitialized && !project.isDisposed) {
      PlatformTestUtil.forceCloseProjectWithoutSaving(project)
    }
  }

  @Test
  @RunMethodInEdt
  fun `unshelve list`() {
    doTestUnshelve(0, 0, 2, 1)
  }

  @Test
  @RunMethodInEdt
  fun `unshelve files`() {
    doTestUnshelve(changeCount = 1, binariesNum = 1, expectedListNum = 3, expectedRecycledNum = 1)
  }

  @Test
  @RunMethodInEdt
  fun `unshelve all files`() {
    doTestUnshelve(2, 2, 2, 1)
  }

  @Test
  @RunMethodInEdt
  fun `do not remove files when unshelve`() {
    doTestUnshelve(0, 0, 3, 0, false)
  }

  @Test
  @RunMethodInEdt
  fun `delete list`() {
    doTestDelete(shelvedChangesManager.shelvedChangeLists[0], 0, 0, 2, 1)
  }

  @Test
  @RunMethodInEdt
  fun `delete files`() {
    doTestDelete(shelvedChangesManager.shelvedChangeLists[0], 1, 1, 3, 1)
  }

  @Test
  @RunMethodInEdt
  fun `delete all files`() {
    doTestDelete(shelvedChangesManager.shelvedChangeLists[0], 2, 2, 2, 1)
  }

  @Test
  @RunMethodInEdt
  fun `delete deleted list`() {
    val shelvedChangeList = shelvedChangesManager.shelvedChangeLists[0]
    shelvedChangesManager.markChangeListAsDeleted(shelvedChangeList)
    doTestDelete(shelvedChangeList, 0, 0, 2, 0)
  }

  @Test
  @RunMethodInEdt
  fun `delete deleted files`() {
    val shelvedChangeList = shelvedChangesManager.shelvedChangeLists[0]
    shelvedChangesManager.markChangeListAsDeleted(shelvedChangeList)
    doTestDelete(shelvedChangeList, 1, 1, 2, 1)
  }

  @Test
  @RunMethodInEdt
  fun `delete all deleted files`() {
    val shelvedChangeList = shelvedChangesManager.shelvedChangeLists[0]
    shelvedChangesManager.markChangeListAsDeleted(shelvedChangeList)
    doTestDelete(shelvedChangeList, 2, 2, 2, 0)
  }

  @Test
  @RunMethodInEdt
  fun `undo list deletion`() {
    doTestDelete(shelvedChangesManager.shelvedChangeLists[0], 0, 0, 2, 1, true)
  }

  @Test
  @RunMethodInEdt
  fun `undo file deletion`() {
    //correct undo depends on ability to merge 2 shelved lists with separated changes inside
    doTestDelete(shelvedChangesManager.shelvedChangeLists[0], 1, 1, 3, 1, true)
  }

  @Test
  @RunMethodInEdt
  fun `create patch from shelf`() {
    val shelvedChangeList = shelvedChangesManager.shelvedChangeLists[0]
    shelvedChangeList.loadChangesIfNeeded(project)
    val patchBuilder = ShelfPatchBuilder(project, shelvedChangeList, emptyList())
    val patches = patchBuilder.buildPatches(project.stateStore.projectBasePath, emptyList(), false, false)
    val changeSize = shelvedChangeList.changes?.size ?: 0
    assertTrue(patches.size == (changeSize + shelvedChangeList.binaryFiles.size))
  }

  @Test
  @RunMethodInEdt
  fun `create patch from shelved changes`() {
    val shelvedChangeList = shelvedChangesManager.shelvedChangeLists[0]
    shelvedChangeList.loadChangesIfNeeded(project)
    val selectedPaths = listOf(ShelvedWrapper(shelvedChangeList.changes!!.first(), shelvedChangeList).path,
                               ShelvedWrapper(shelvedChangeList.binaryFiles!!.first(), shelvedChangeList).path)
    val patchBuilder = ShelfPatchBuilder(project, shelvedChangeList, selectedPaths)
    val patches = patchBuilder.buildPatches(project.stateStore.projectBasePath, emptyList(), false, false)
    assertTrue(patches.size == selectedPaths.size)
  }

  @Test
  fun `cleanup task does not retain closed project from scheduler`() {
    PlatformTestUtil.forceCloseProjectWithoutSaving(project)

    val baseDir = tempDir.resolve("leak-project")
    baseDir.createDirectories()
    val projectFile = baseDir.resolve("leak.ipr")
    refreshProjectDir(projectFile)

    val closedProject = ProjectManagerEx.getInstanceEx().openProject(projectFile, createTestOpenProjectOptions())!!
    ShelveChangesManager.getInstance(closedProject)
    DumbService.getInstance(closedProject).waitForSmartMode()
    PlatformTestUtil.forceCloseProjectWithoutSaving(closedProject)

    runReadActionBlocking {
      LeakHunter.checkLeak({ mapOf(JobScheduler.getScheduler() to "JobScheduler") }, ProjectImpl::class.java) { it === closedProject }
    }
  }

  private fun doTestUnshelve(
    changeCount: Int,
    binariesNum: Int,
    expectedListNum: Int,
    expectedRecycledNum: Int,
    removeFilesFromShelf: Boolean = true,
  ) {
    shelvedChangesManager.isRemoveFilesFromShelf = removeFilesFromShelf
    val shelvedChangeList = shelvedChangesManager.shelvedChangeLists[0]
    shelvedChangeList.loadChangesIfNeeded(project)
    val originalDate = shelvedChangeList.date
    val changes = if (changeCount == 0) null else shelvedChangeList.changes!!.subList(0, changeCount)
    val binaries = if (changeCount == 0) null else shelvedChangeList.binaryFiles.subList(0, binariesNum)

    shelvedChangesManager.unshelveChangeList(shelvedChangeList, changes, binaries, null, false)

    // unshelveChangeList uses GuiUtils.invokeLaterIfNeeded
    runInEdtAndWait { PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue() }

    val recycledShelvedChangeLists = shelvedChangesManager.recycledShelvedChangeLists
    assertThat(shelvedChangesManager.shelvedChangeLists.size).isEqualTo(expectedListNum)
    assertThat(recycledShelvedChangeLists.size).isEqualTo(expectedRecycledNum)
    if (recycledShelvedChangeLists.isNotEmpty()) {
      assertThat(originalDate.before(recycledShelvedChangeLists[0].date)).isTrue()
    }
  }

  private fun doTestDelete(
    shelvedChangeList: ShelvedChangeList,
    changesNum: Int,
    binariesNum: Int,
    expectedListNum: Int,
    expectedDeletedNum: Int,
    undoDeletion: Boolean = false,
  ) {
    val originalDate = shelvedChangeList.date
    shelvedChangeList.loadChangesIfNeeded(project)
    val changes = if (changesNum == 0) emptyList<ShelvedChange>() else shelvedChangeList.changes!!.subList(0, changesNum)
    val binaries = if (changesNum == 0) emptyList<ShelvedBinaryFile>() else shelvedChangeList.binaryFiles.subList(0, binariesNum)

    val shouldDeleteEntireList = changesNum == 0 && binariesNum == 0
    val deleteShelvesWithDates = shelvedChangesManager.deleteShelves(
      if (shouldDeleteEntireList) listOf(shelvedChangeList) else emptyList(),
      if (!shouldDeleteEntireList) listOf(shelvedChangeList) else emptyList(),
      changes, binaries)

    val deletedLists = shelvedChangesManager.deletedLists
    assertEquals(expectedListNum, shelvedChangesManager.shelvedChangeLists.size)
    assertEquals(expectedDeletedNum, deletedLists.size)
    if (deletedLists.isNotEmpty() && deleteShelvesWithDates.isNotEmpty())
      assertThat(originalDate.before(deletedLists[0].date)).isTrue()

    if (undoDeletion) {
      for ((l, d) in deleteShelvesWithDates) {
        shelvedChangesManager.restoreList(l, d)
      }
      assertEquals(expectedListNum + expectedDeletedNum, shelvedChangesManager.shelvedChangeLists.size)
    }
  }

  private fun refreshProjectDir(projectFile: Path) {
    val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(projectFile.parent)!!
    ApplicationManager.getApplication().runWriteIntentReadAction<Unit, Nothing?> {
      VfsUtil.markDirtyAndRefresh(false, true, true, virtualFile)
    }
  }
}
