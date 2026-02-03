// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.shelf

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.LineSeparator
import git4idea.stash.GitShelveChangesSaver
import git4idea.test.ChangesBuilder
import git4idea.test.GitSingleRepoTest
import git4idea.test.assertNoChanges
import git4idea.test.assertStatus
import git4idea.test.createRepository
import git4idea.test.file
import org.assertj.core.api.Assertions.assertThat

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

  companion object {
    private val CRLF = LineSeparator.CRLF.separatorString
  }

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

  fun `test hidden stage-only changes 1`() {
    val initialContent = "initial\n"

    val aFile = file("a.txt")
    aFile.create(initialContent).addCommit("initial")

    val bfile = file("b.txt")
    bfile.create(initialContent).addCommit("initial")

    aFile.write("more changes\n").add()
    aFile.write(initialContent)

    bfile.write("more changes from b\n").add()
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
      modified("b.txt", initialContent, bNewContent)
    }

    saver.load()
    refresh()
    updateChangeListManager()

    assertChanges {
      modified("b.txt", initialContent, bNewContent)
    }
  }

  fun `test hidden stage-only changes 2`() {
    val aFile = file("a.txt")
    val initialContent = "initial\n"
    aFile.create(initialContent).addCommit("initial")
    aFile.write("more changes\n")
    aFile.add()
    aFile.write(initialContent)

    refresh()
    updateChangeListManager()

    saver.saveLocalChanges(listOf(repo.root))
    refresh()
    updateChangeListManager()

    assertEmpty(repo.stagingAreaHolder.allRecords)
    assertEmpty(shelveChangesManager.shelvedChangeLists)

    saver.load()
    refresh()
    updateChangeListManager()

    assertEmpty(repo.stagingAreaHolder.allRecords)
    assertEmpty(shelveChangesManager.shelvedChangeLists)
  }

  fun `test hidden stage-only changes 3`() {
    val afile = file("b.txt")
    afile.create("initial content").add()
    afile.delete()

    refresh()
    updateChangeListManager()

    saver.saveLocalChanges(listOf(repo.root))
    refresh()
    updateChangeListManager()

    assertEmpty(repo.stagingAreaHolder.allRecords)
    assertEmpty(shelveChangesManager.shelvedChangeLists)

    saver.load()
    refresh()
    updateChangeListManager()

    assertEmpty(repo.stagingAreaHolder.allRecords)
    assertEmpty(shelveChangesManager.shelvedChangeLists)
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

  fun `test crlf line separators are preserved after shelve and unshelve`() {
    val file = file("a.txt")
    val initialText = "first line${CRLF}second line${CRLF}"
    file.create(initialText)

    val vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file.file)
    if (vFile == null) {
      assertNotNull("VirtualFile not found for file ${file.file}", vFile)
      return
    }

    val document = runReadAction { FileDocumentManager.getInstance().getDocument(vFile) }
    if (document == null) {
      assertNotNull("Document not found for file ${file.file}", document)
      return
    }
    assertHasCrlfSeparators(vFile)

    file.addCommit("initial commit file with CRLF line separators")

    runInEdtAndWait {
      runWriteCommandAction(project) {
        document.insertString(document.textLength, "appended line\n")
        FileDocumentManager.getInstance().saveDocument(document)
      }
    }

    refresh()
    updateChangeListManager()

    saver.saveLocalChanges(listOf(repo.root))

    refresh()
    updateChangeListManager()

    assertThat(ChangeListManager.getInstance(project).allChanges).isEmpty()
    assertHasCrlfSeparators(vFile)

    val shelvedLists = ShelveChangesManager.getInstance(project).shelvedChangeLists
    assertThat(shelvedLists).isNotEmpty

    saver.load()

    refresh()
    updateChangeListManager()

    assertThat(ChangeListManager.getInstance(project).allChanges).isNotEmpty
    assertHasCrlfSeparators(vFile)
  }

  private fun assertHasCrlfSeparators(vFile: VirtualFile) {
    // Read raw bytes and check for CRLF occurrences to avoid re-normalization
    val bytes = vFile.contentsToByteArray()
    val text = String(bytes, vFile.charset)
    assertThat(text).contains(CRLF)

    val detected = LoadTextUtil.detectLineSeparator(vFile, true)
    assertThat(detected).isEqualTo(CRLF)
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
