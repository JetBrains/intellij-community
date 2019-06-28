// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.shelf

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.VcsTestUtil
import com.intellij.testFramework.PlatformTestCase
import junit.framework.TestCase
import java.io.File
import java.nio.file.Path

class ShelveChangesManagerTest : PlatformTestCase() {
  private lateinit var myShelvedChangesManager: ShelveChangesManager

  override fun doCreateProject(projectFile: Path): Project {
    val project = super.doCreateProject(projectFile)
    val testDataFile = File("${VcsTestUtil.getTestDataPath()}/shelf/shelvedChangeLists")
    val shelfFile = File("${project.basePath}/.shelf")
    FileUtil.createDirectory(shelfFile)
    myFilesToDelete.add(shelfFile)
    testDataFile.copyRecursively(shelfFile)
    return project
  }

  override fun setUp() {
    super.setUp()
    myShelvedChangesManager = ShelveChangesManager.getInstance(myProject)
  }

  fun `test unshelve list`() {
    doTestUnshelve(0, 0, 2, 1)
  }

  fun `test unshelve files`() {
    doTestUnshelve(1, 1, 3, 1)
  }

  fun `test unshelve all files`() {
    doTestUnshelve(2, 2, 2, 1)
  }

  fun `test do not remove files when unshelve`() {
    doTestUnshelve(0, 0, 3, 0, false)
  }

  fun `test delete list`() {
    doTestDelete(myShelvedChangesManager.shelvedChangeLists[0], 0, 0, 2, 1)
  }

  fun `test delete files`() {
    doTestDelete(myShelvedChangesManager.shelvedChangeLists[0], 1, 1, 3, 1)
  }

  fun `test delete all files`() {
    doTestDelete(myShelvedChangesManager.shelvedChangeLists[0], 2, 2, 2, 1)
  }

  fun `test delete deleted list`() {
    val shelvedChangeList = myShelvedChangesManager.shelvedChangeLists[0]
    myShelvedChangesManager.markChangeListAsDeleted(shelvedChangeList)
    doTestDelete(shelvedChangeList, 0, 0, 2, 0)
  }

  fun `test delete deleted files`() {
    val shelvedChangeList = myShelvedChangesManager.shelvedChangeLists[0]
    myShelvedChangesManager.markChangeListAsDeleted(shelvedChangeList)
    doTestDelete(shelvedChangeList, 1, 1, 2, 1)
  }

  fun `test delete all deleted files`() {
    val shelvedChangeList = myShelvedChangesManager.shelvedChangeLists[0]
    myShelvedChangesManager.markChangeListAsDeleted(shelvedChangeList)
    doTestDelete(shelvedChangeList, 2, 2, 2, 0)
  }

  fun `test undo list deletion`() {
    doTestDelete(myShelvedChangesManager.shelvedChangeLists[0], 0, 0, 2, 1, true)
  }

  fun `test undo file deletion`() {
    //correct undo depends on ability to merge 2 shelved lists with separated changes inside
    doTestDelete(myShelvedChangesManager.shelvedChangeLists[0], 1, 1, 3, 1, true)
  }

  private fun doTestUnshelve(changesNum: Int,
                             binariesNum: Int,
                             expectedListNum: Int,
                             expectedRecycledNum: Int,
                             removeFilesFromShelf: Boolean = true) {
    myShelvedChangesManager.isRemoveFilesFromShelf = removeFilesFromShelf
    val shelvedChangeList = myShelvedChangesManager.shelvedChangeLists[0]
    shelvedChangeList.loadChangesIfNeeded(project)
    val originalDate = shelvedChangeList.DATE
    val changes = if (changesNum == 0) null else shelvedChangeList.changes!!.subList(0, changesNum)
    val binaries = if (changesNum == 0) null else shelvedChangeList.binaryFiles.subList(0, binariesNum)

    myShelvedChangesManager.unshelveChangeList(shelvedChangeList, changes, binaries, null, false)
    val recycledShelvedChangeLists = myShelvedChangesManager.recycledShelvedChangeLists
    TestCase.assertEquals(expectedListNum, myShelvedChangesManager.shelvedChangeLists.size)
    TestCase.assertEquals(expectedRecycledNum, recycledShelvedChangeLists.size)
    if (recycledShelvedChangeLists.isNotEmpty())
      assertTrue(originalDate.before(recycledShelvedChangeLists[0].DATE))
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
    val deleteShelvesWithDates = myShelvedChangesManager.deleteShelves(
      if (shouldDeleteEntireList) listOf(shelvedChangeList) else emptyList(),
      if (!shouldDeleteEntireList) listOf(shelvedChangeList) else emptyList(),
      changes, binaries)

    val deletedLists = myShelvedChangesManager.deletedLists
    TestCase.assertEquals(expectedListNum, myShelvedChangesManager.shelvedChangeLists.size)
    TestCase.assertEquals(expectedDeletedNum, deletedLists.size)
    if (deletedLists.isNotEmpty() && deleteShelvesWithDates.isNotEmpty())
      assertTrue(originalDate.before(deletedLists[0].DATE))

    if (undoDeletion) {
      deleteShelvesWithDates.forEach { l, d -> myShelvedChangesManager.restoreList(l, d) }
      TestCase.assertEquals(expectedListNum + expectedDeletedNum, myShelvedChangesManager.shelvedChangeLists.size)
    }
  }
}



