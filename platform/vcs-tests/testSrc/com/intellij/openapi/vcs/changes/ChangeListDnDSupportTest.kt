// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import app.cash.turbine.test
import com.intellij.openapi.vcs.BaseChangeListsTest
import com.intellij.openapi.vcs.checkin.CheckinEnvironment
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.vcs.impl.shared.RdLocalChanges
import com.intellij.platform.vcs.impl.shared.changes.ChangeListsViewModel
import com.intellij.vcsUtil.VcsUtil
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.runBlocking
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.time.Duration.Companion.seconds

internal class ChangeListDnDSupportTest : BaseChangeListsTest() {
  override fun setUp() {
    super.setUp()
    setRegistryPropertyForTest(RdLocalChanges.REGISTRY_KEY, true.toString())
  }

  fun `test moveChanges moves changes to target changelist`() {
    val defaultList = "List A"
    createChangelist(defaultList)
    setDefaultChangeList(defaultList)
    val targetList = "List B"
    createChangelist(targetList)

    addLocalFile("file1.txt", "content1", "base1")
    addLocalFile("file2.txt", "content2", "base2")
    addLocalFile("file3.txt", "content3", "base3")
    refreshCLM()

    val changesToMove = clm.allChanges.toList().take(2)
    ChangeListsViewModel.getInstance(project).moveChangesTo(targetList.asListNameToList(), changesToMove)

    // Waiting for the state with non-empty target change list
    runBlocking {
      ChangeListsViewModel.getInstance(project).changeListsState.filter { state ->
        state.withName(targetList)?.changes?.size == 2
      }.test(5.seconds) {
        val matchingState = awaitItem()
        assertSize(1, requireNotNull(matchingState.withName(defaultList)).changes)
        assertContainsElements(requireNotNull(matchingState.withName(targetList)).changes, changesToMove)
      }
    }
  }

  fun `test addUnversionedFiles adds files to changelist`() {
    val listName = "Target List"
    createChangelist(listName)

    val defaultCl = "default"
    createChangelist(defaultCl)
    setDefaultChangeList(defaultCl)

    val file1 = addLocalFile("unversioned1.txt", "content1")
    val file2 = addLocalFile("unversioned2.txt", "content2")
    refreshCLM()
    assertEmpty(clm.allChanges)

    val checkinEnvironmentMock = mock<CheckinEnvironment>()
    vcs.checkinEnvironment = checkinEnvironmentMock
    whenever(checkinEnvironmentMock.scheduleUnversionedFilesForAddition(any())).then {
      // After setting a base version, unversioned files will be considered as changes
      it.getArgument<List<VirtualFile>>(0).forEach { file -> setBaseVersion(file.name, null) }
      emptyList<Exception>()
    }

    val filePaths = listOf(file1, file2).map { VcsUtil.getFilePath(it) }
    ChangeListsViewModel.getInstance(project).addUnversionedFiles(listName.asListNameToList(), filePaths)

    runBlocking {
      ChangeListsViewModel.getInstance(project).changeListsState.filter { state ->
        state.withName(listName)?.changes?.size == 2 && state.unversionedFiles.isEmpty()
      }.test(5.seconds) {
        val matchingState = awaitItem()
        val changesPaths = requireNotNull(matchingState.withName(listName)).changes.map { ChangesUtil.getFilePath(it) }
        assertContainsElements(changesPaths, filePaths)
      }
    }
  }

  private fun ChangeListsViewModel.ChangeLists.withName(name: String) = changeLists.find { it.name == name }
}