// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.shelf

import com.intellij.openapi.progress.EmptyProgressIndicator
import git4idea.stash.GitShelveChangesSaver
import git4idea.test.ChangesBuilder
import git4idea.test.GitSingleRepoTest
import git4idea.test.assertChanges
import git4idea.test.assertNoChanges

class GitShelveTest : GitSingleRepoTest() {

  private lateinit var shelveChangesManager : ShelveChangesManager
  private lateinit var saver: GitShelveChangesSaver

  override fun setUp() {
    super.setUp()
    shelveChangesManager = ShelveChangesManager.getInstance(project)
    saver = GitShelveChangesSaver(project, git, EmptyProgressIndicator(), "test")
  }

  fun `test modification`() {
    val file = file("a.txt")
    val initialContent = "initial\n"
    file.create(initialContent).addCommit("initial")
    file.append("more changes\n")
    val newContent = file.read()

    refresh()
    updateChangeListManager()

    saver.saveLocalChanges(listOf(repo.root))
    refresh()
    updateChangeListManager()

    changeListManager.assertNoChanges()
    assertEquals("Current file content is incorrect", initialContent, file.read())

    val list = `assert single shelvelist`()
    assertChanges(list) {
      modified("a.txt", initialContent, newContent)
    }
  }

  fun `test two files modification`() {
    val afile = file("a.txt")
    val initialContent = "initial\n"
    afile.create(initialContent).addCommit("initial")
    afile.append("more changes\n")
    val aNewContent = afile.read()

    val bfile = file("b.txt")
    bfile.create(initialContent).addCommit("initial")
    bfile.append("more changes from b\n")
    val bNewContent = bfile.read()

    refresh()
    updateChangeListManager()

    saver.saveLocalChanges(listOf(repo.root))
    refresh()
    updateChangeListManager()

    changeListManager.assertNoChanges()
    assertEquals("Current file content is incorrect", initialContent, afile.read())
    assertEquals("Current file content is incorrect", initialContent, bfile.read())

    val list = `assert single shelvelist`()
    assertChanges(list) {
      modified("a.txt", initialContent, aNewContent)
      modified("b.txt", initialContent, bNewContent)
    }
  }

  fun `test addition`() {
    val file = file("a.txt")
    val initialContent = "initial\n"
    file.create(initialContent).add()

    refresh()
    updateChangeListManager()

    saver.saveLocalChanges(listOf(repo.root))
    refresh()
    updateChangeListManager()

    changeListManager.assertNoChanges()
    assertFalse("There should be no file a.txt on disk", file.file.exists())

    val list = `assert single shelvelist`()
    assertChanges(list) {
      added("a.txt", initialContent)
    }
  }

  private fun `assert single shelvelist`(): ShelvedChangeList {
    val lists = shelveChangesManager.shelvedChangeLists
    assertEquals("Incorrect shelve lists amount", 1, lists.size)
    return lists[0]
  }

  private fun assertChanges(list: ShelvedChangeList, changes: ChangesBuilder.() -> Unit) {
    assertChanges(list.getChanges(project).map { it.getChange(project) }, changes)
  }
}