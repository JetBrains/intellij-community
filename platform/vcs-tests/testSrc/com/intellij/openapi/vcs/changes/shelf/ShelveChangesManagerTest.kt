// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.shelf

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.vcs.VcsTestUtil
import com.intellij.openapi.vcs.changes.patch.CreatePatchCommitExecutor.ShelfPatchBuilder
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.project.stateStore
import com.intellij.testFramework.*
import com.intellij.util.io.createDirectories
import junit.framework.TestCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.*
import java.nio.file.Paths

class ShelveChangesManagerTest {
  companion object {
    @ClassRule
    @JvmField
    val appRule = ApplicationRule()
  }
  private lateinit var shelvedChangesManager: ShelveChangesManager

  private lateinit var project: Project

  @Rule
  @JvmField
  val tempDir = TemporaryDirectory()

  @Before
  fun setUp() {
    // test data expects not directory-based project
    val baseDir = tempDir.newPath()
    val projectFile = baseDir.resolve("p.ipr")
    val shelfDir = baseDir.resolve(".shelf")
    shelfDir.createDirectories()
    val testDataFile = Paths.get("${VcsTestUtil.getTestDataPath()}/shelf/shelvedChangeLists")
    testDataFile.toFile().copyRecursively(shelfDir.toFile())

    val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(projectFile.parent)!!
    VfsUtil.markDirtyAndRefresh(false, true, true, virtualFile)

    project = ProjectManagerEx.getInstanceEx().openProject(projectFile, createTestOpenProjectOptions())!!
    shelvedChangesManager = ShelveChangesManager.getInstance(project)
  }

  @After
  fun closeProject() {
    if (project.isInitialized) {
      PlatformTestUtil.forceCloseProjectWithoutSaving(project)
    }
  }

  @Test
  fun `unshelve list`() {
    doTestUnshelve(0, 0, 2, 1)
  }

  @Test
  fun `unshelve files`() {
    doTestUnshelve(changeCount = 1, binariesNum = 1, expectedListNum = 3, expectedRecycledNum = 1)
  }

  @Test
  fun `unshelve all files`() {
    doTestUnshelve(2, 2, 2, 1)
  }

  @Test
  fun `do not remove files when unshelve`() {
    doTestUnshelve(0, 0, 3, 0, false)
  }

  @Test
  fun `delete list`() {
    doTestDelete(shelvedChangesManager.shelvedChangeLists[0], 0, 0, 2, 1)
  }

  @Test
  fun `delete files`() {
    doTestDelete(shelvedChangesManager.shelvedChangeLists[0], 1, 1, 3, 1)
  }

  @Test
  fun `delete all files`() {
    doTestDelete(shelvedChangesManager.shelvedChangeLists[0], 2, 2, 2, 1)
  }

  @Test
  fun `delete deleted list`() {
    val shelvedChangeList = shelvedChangesManager.shelvedChangeLists[0]
    shelvedChangesManager.markChangeListAsDeleted(shelvedChangeList)
    doTestDelete(shelvedChangeList, 0, 0, 2, 0)
  }

  @Test
  fun `delete deleted files`() {
    val shelvedChangeList = shelvedChangesManager.shelvedChangeLists[0]
    shelvedChangesManager.markChangeListAsDeleted(shelvedChangeList)
    doTestDelete(shelvedChangeList, 1, 1, 2, 1)
  }

  @Test
  fun `delete all deleted files`() {
    val shelvedChangeList = shelvedChangesManager.shelvedChangeLists[0]
    shelvedChangesManager.markChangeListAsDeleted(shelvedChangeList)
    doTestDelete(shelvedChangeList, 2, 2, 2, 0)
  }

  @Test
  fun `undo list deletion`() {
    doTestDelete(shelvedChangesManager.shelvedChangeLists[0], 0, 0, 2, 1, true)
  }

  @Test
  fun `undo file deletion`() {
    //correct undo depends on ability to merge 2 shelved lists with separated changes inside
    doTestDelete(shelvedChangesManager.shelvedChangeLists[0], 1, 1, 3, 1, true)
  }

  @Test
  fun `create patch from shelf`() {
    val shelvedChangeList = shelvedChangesManager.shelvedChangeLists[0]
    shelvedChangeList.loadChangesIfNeeded(project)
    val patchBuilder = ShelfPatchBuilder(project, shelvedChangeList, emptyList())
    val patches = patchBuilder.buildPatches(project.stateStore.projectBasePath, emptyList(), false, false)
    val changeSize = shelvedChangeList.changes?.size ?: 0
    TestCase.assertTrue(patches.size == (changeSize + shelvedChangeList.binaryFiles.size))
  }

  @Test
  fun `create patch from shelved changes`() {
    val shelvedChangeList = shelvedChangesManager.shelvedChangeLists[0]
    shelvedChangeList.loadChangesIfNeeded(project)
    val selectedPaths = listOf(ShelvedWrapper(shelvedChangeList.changes!!.first()).path,
                               ShelvedWrapper(shelvedChangeList.binaryFiles!!.first()).path)
    val patchBuilder = ShelfPatchBuilder(project, shelvedChangeList, selectedPaths)
    val patches = patchBuilder.buildPatches(project.stateStore.projectBasePath, emptyList(), false, false)
    TestCase.assertTrue(patches.size == selectedPaths.size)
  }

  private fun doTestUnshelve(changeCount: Int,
                             binariesNum: Int,
                             expectedListNum: Int,
                             expectedRecycledNum: Int,
                             removeFilesFromShelf: Boolean = true) {
    shelvedChangesManager.isRemoveFilesFromShelf = removeFilesFromShelf
    val shelvedChangeList = shelvedChangesManager.shelvedChangeLists[0]
    shelvedChangeList.loadChangesIfNeeded(project)
    val originalDate = shelvedChangeList.DATE
    val changes = if (changeCount == 0) null else shelvedChangeList.changes!!.subList(0, changeCount)
    val binaries = if (changeCount == 0) null else shelvedChangeList.binaryFiles.subList(0, binariesNum)

    shelvedChangesManager.unshelveChangeList(shelvedChangeList, changes, binaries, null, false)

    // unshelveChangeList uses GuiUtils.invokeLaterIfNeeded
    runInEdtAndWait { PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue() }

    val recycledShelvedChangeLists = shelvedChangesManager.recycledShelvedChangeLists
    assertThat(shelvedChangesManager.shelvedChangeLists.size).isEqualTo(expectedListNum)
    assertThat(recycledShelvedChangeLists.size).isEqualTo(expectedRecycledNum)
    if (recycledShelvedChangeLists.isNotEmpty()) {
      assertThat(originalDate.before(recycledShelvedChangeLists[0].DATE)).isTrue()
    }
  }

  private fun doTestDelete(shelvedChangeList: ShelvedChangeList,
                           changesNum: Int,
                           binariesNum: Int,
                           expectedListNum: Int,
                           expectedDeletedNum: Int,
                           undoDeletion: Boolean = false) {
    val originalDate = shelvedChangeList.DATE
    shelvedChangeList.loadChangesIfNeeded(project)
    val changes = if (changesNum == 0) emptyList<ShelvedChange>() else shelvedChangeList.changes!!.subList(0, changesNum)
    val binaries = if (changesNum == 0) emptyList<ShelvedBinaryFile>() else shelvedChangeList.binaryFiles.subList(0, binariesNum)

    val shouldDeleteEntireList = changesNum == 0 && binariesNum == 0
    val deleteShelvesWithDates = shelvedChangesManager.deleteShelves(
      if (shouldDeleteEntireList) listOf(shelvedChangeList) else emptyList(),
      if (!shouldDeleteEntireList) listOf(shelvedChangeList) else emptyList(),
      changes, binaries)

    val deletedLists = shelvedChangesManager.deletedLists
    TestCase.assertEquals(expectedListNum, shelvedChangesManager.shelvedChangeLists.size)
    TestCase.assertEquals(expectedDeletedNum, deletedLists.size)
    if (deletedLists.isNotEmpty() && deleteShelvesWithDates.isNotEmpty())
      assertThat(originalDate.before(deletedLists[0].DATE)).isTrue()

    if (undoDeletion) {
      for ((l, d) in deleteShelvesWithDates) {
        shelvedChangesManager.restoreList(l, d)
      }
      TestCase.assertEquals(expectedListNum + expectedDeletedNum, shelvedChangesManager.shelvedChangeLists.size)
    }
  }
}



