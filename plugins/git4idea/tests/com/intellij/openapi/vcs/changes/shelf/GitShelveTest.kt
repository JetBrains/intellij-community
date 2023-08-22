// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.shelf

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.util.registry.Registry
import git4idea.stash.GitShelveChangesSaver
import git4idea.test.*
import org.assertj.core.api.Assertions.assertThat
import java.util.*

class GitStandardShelveTest : GitShelveTest() {
  override fun setUp() {
    super.setUp()
    setBatchShelveOptimization(false)
  }
}

class GitBatchShelveTest: GitShelveTest() {
  override fun setUp() {
    super.setUp()
    setBatchShelveOptimization(true)
  }
}

abstract class GitShelveTest : GitSingleRepoTest() {
  private lateinit var shelveChangesManager : ShelveChangesManager
  private lateinit var saver: GitShelveChangesSaver

  override fun setUp() {
    super.setUp()
    shelveChangesManager = ShelveChangesManager.getInstance(project)
    saver = GitShelveChangesSaver(project, git, EmptyProgressIndicator(), "test")
    git("config core.autocrlf false")
  }

  protected fun setBatchShelveOptimization(value: Boolean) {
    val batchSize = if (value) 100 else -1
    Registry.get("git.shelve.load.base.in.batches").setValue(batchSize, testRootDisposable)
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
    val aFile = file("a.txt")
    val initialContent = "initial\n"
    aFile.create(initialContent).addCommit("initial")
    aFile.append("more changes\n")
    val aNewContent = aFile.read()

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
    assertEquals("Current file content is incorrect", initialContent, aFile.read())
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

  fun `test shelf and load files added in multiple roots`() {
    val file = file("a.txt")
    val initialContent = "initial\n"
    file.create(initialContent).add()

    val secondRoot = createRepository(project, projectRoot.createDir("secondRoot").toNioPath(), false)
    val file2 = secondRoot.file("b.txt")
    file2.create(initialContent).add()

    refresh()
    updateChangeListManager()

    saver.saveLocalChanges(listOf(repo.root, secondRoot.root))
    refresh()
    updateChangeListManager()

    changeListManager.assertNoChanges()
    assertThat(file.file).doesNotExist()
    assertThat(file2.file).doesNotExist()

    val list = `assert single shelvelist`()
    assertChanges(list) {
      added("a.txt", initialContent)
      added("b.txt", initialContent)
    }

    saver.load()
    refresh()
    updateChangeListManager()

    assertTrue("There should be the file a.txt on the disk", file.file.exists())
    assertTrue("There should be the file b.txt on the disk", file2.file.exists())
    repo.assertStatus(file.file, 'A')
    secondRoot.assertStatus(file2.file, 'A')
  }

  private fun `assert single shelvelist`(): ShelvedChangeList {
    val lists = shelveChangesManager.shelvedChangeLists
    assertEquals("Incorrect shelve lists amount", 1, lists.size)
    return lists[0]
  }

  private fun assertChanges(list: ShelvedChangeList, changes: ChangesBuilder.() -> Unit) {
    val changesInShelveList = list.changes!!.map { it.change }

    val cb = ChangesBuilder()
    cb.changes()

    val actualChanges = HashSet(changesInShelveList)
    for (change in cb.changes) {
      val found = actualChanges.find(change.changeMatcher)
      assertNotNull("The change [$change] not found\n$changesInShelveList", found)
      actualChanges.remove(found)
    }
    assertEmpty("There are unexpected changes in the shelvelist", actualChanges)
  }
}