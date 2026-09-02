// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.shelf

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadActionBlocking
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
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.LineSeparator
import com.intellij.vcs.test.refresh
import com.intellij.vcs.test.updateChangeListManager
import git4idea.stash.GitShelveChangesSaver
import git4idea.test.ChangesBuilder
import git4idea.test.GitSingleRepoContext
import git4idea.test.assertChanges
import git4idea.test.assertNoChanges
import git4idea.test.assertStatus
import git4idea.test.createDir
import git4idea.test.createRepository
import git4idea.test.file
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ValueSource

@TestApplication
@ParameterizedClass(name = "batch optimization = {0}")
@ValueSource(booleans = [false, true])
class GitShelveTest(private val batchOptimization: Boolean) {
  private val fixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = fixture.get()

  @TestDisposable
  lateinit var disposable: Disposable

  private lateinit var shelveChangesManager: ShelveChangesManager
  private lateinit var saver: GitShelveChangesSaver

  companion object {
    private val CRLF = LineSeparator.CRLF.separatorString
  }

  @BeforeEach
  fun setUp(): Unit = with(context) {
    shelveChangesManager = ShelveChangesManager.getInstance(project)
    saver = GitShelveChangesSaver(project, git, EmptyProgressIndicator(), "test")
    git("config core.autocrlf false")
    val batchSize = if (batchOptimization) 100 else -1
    Registry.get("git.shelve.load.base.in.batches").setValue(batchSize, disposable)
  }

  @Test
  fun `test modification`(): Unit = with(context) {
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
    assertThat(file.read()).describedAs("Current file content is incorrect").isEqualTo(initialContent)

    val list = `assert single shelvelist`()
    assertChanges(list) {
      modified("a.txt", initialContent, newContent)
    }
  }

  @Test
  fun `test two files modification`(): Unit = with(context) {
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
    assertThat(aFile.read()).describedAs("Current file content is incorrect").isEqualTo(initialContent)
    assertThat(bfile.read()).describedAs("Current file content is incorrect").isEqualTo(initialContent)

    val list = `assert single shelvelist`()
    assertChanges(list) {
      modified("a.txt", initialContent, aNewContent)
      modified("b.txt", initialContent, bNewContent)
    }
  }

  @Test
  fun `test addition`(): Unit = with(context) {
    val file = file("a.txt")
    val initialContent = "initial\n"
    file.create(initialContent).add()

    refresh()
    updateChangeListManager()

    saver.saveLocalChanges(listOf(repo.root))
    refresh()
    updateChangeListManager()

    changeListManager.assertNoChanges()
    assertThat(file.file).doesNotExist()

    val list = `assert single shelvelist`()
    assertChanges(list) {
      added("a.txt", initialContent)
    }
  }

  @Test
  fun `test hidden stage-only changes 1`(): Unit = with(context) {
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
    assertThat(aFile.read()).describedAs("Current file content is incorrect").isEqualTo(initialContent)
    assertThat(bfile.read()).describedAs("Current file content is incorrect").isEqualTo(initialContent)

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

  @Test
  fun `test hidden stage-only changes 2`(): Unit = with(context) {
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

    assertThat(repo.stagingAreaHolder.allRecords).isEmpty()
    assertThat(shelveChangesManager.shelvedChangeLists).isEmpty()

    saver.load()
    refresh()
    updateChangeListManager()

    assertThat(repo.stagingAreaHolder.allRecords).isEmpty()
    assertThat(shelveChangesManager.shelvedChangeLists).isEmpty()
  }

  @Test
  fun `test hidden stage-only changes 3`(): Unit = with(context) {
    val bFile = file("b.txt")
    bFile.create("initial content").add()
    bFile.delete()

    refresh()
    updateChangeListManager()

    saver.saveLocalChanges(listOf(repo.root))
    refresh()
    updateChangeListManager()

    assertThat(repo.stagingAreaHolder.allRecords).isEmpty()
    assertThat(shelveChangesManager.shelvedChangeLists).isEmpty()

    saver.load()
    refresh()
    updateChangeListManager()

    assertThat(repo.stagingAreaHolder.allRecords).isEmpty()
    assertThat(shelveChangesManager.shelvedChangeLists).isEmpty()
  }

  @Test
  fun `test shelf and load files added in multiple roots`(): Unit = with(context) {
    val file = file("a.txt")
    val initialContent = "initial\n"
    file.create(initialContent).add()

    val secondRoot = createRepository(project, createDir(projectRoot, "secondRoot").toNioPath(), false)
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

    assertThat(file.file).exists()
    assertThat(file2.file).exists()
    repo.assertStatus(file.file, 'A')
    secondRoot.assertStatus(file2.file, 'A')
  }

  @Test
  fun `test crlf line separators are preserved after shelve and unshelve`(): Unit = with(context) {
    val file = file("a.txt")
    val initialText = "first line${CRLF}second line${CRLF}"
    file.create(initialText)

    val vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file.file)
    assertThat(vFile).describedAs("VirtualFile not found for file ${file.file}").isNotNull()
    val nonNullVFile = vFile!!

    val nullableDocument = runReadActionBlocking { FileDocumentManager.getInstance().getDocument(nonNullVFile) }
    assertThat(nullableDocument).describedAs("Document not found for file ${file.file}").isNotNull()
    val document = nullableDocument!!
    assertHasCrlfSeparators(nonNullVFile)

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
    assertHasCrlfSeparators(nonNullVFile)

    val shelvedLists = ShelveChangesManager.getInstance(project).shelvedChangeLists
    assertThat(shelvedLists).isNotEmpty

    saver.load()

    refresh()
    updateChangeListManager()

    assertThat(ChangeListManager.getInstance(project).allChanges).isNotEmpty
    assertHasCrlfSeparators(nonNullVFile)
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
    assertThat(lists).describedAs("Incorrect shelve lists amount").hasSize(1)
    return lists.single()
  }

  private fun assertChanges(list: ShelvedChangeList, changes: ChangesBuilder.() -> Unit) {
    val changesInShelveList = list.changes!!.map { it.change }

    val cb = ChangesBuilder()
    cb.changes()

    val actualChanges = HashSet(changesInShelveList)
    for (change in cb.changes) {
      val found = actualChanges.find(change.changeMatcher)
      assertThat(found).describedAs("The change [$change] not found\n$changesInShelveList").isNotNull()
      actualChanges.remove(found)
    }
    assertThat(actualChanges).describedAs("There are unexpected changes in the shelvelist").isEmpty()
  }
}
