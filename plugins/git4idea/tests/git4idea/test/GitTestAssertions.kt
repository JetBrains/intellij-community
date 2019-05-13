// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.test

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.openapi.vcs.Executor
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.PlatformTestCase.assertOrderedEquals
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcsUtil.VcsUtil.getFilePath
import git4idea.changes.GitChangeUtils
import git4idea.history.GitHistoryUtils
import git4idea.history.GitLogUtil
import git4idea.repo.GitRepository
import org.assertj.core.api.Assertions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File
import java.util.*

fun GitRepository.assertStatus(file: VirtualFile, status: Char) {
  assertStatus(getFilePath(file), status)
}

fun GitRepository.assertStatus(file: FilePath, status: Char) {
  assertStatus(file.ioFile, status)
}

fun GitRepository.assertStatus(file: File, status: Char) {
  val actualStatus = git("status --porcelain ${file.path}").trim()
  assertTrue("File status is not-changed: $actualStatus", !actualStatus.isEmpty())
  assertEquals("File status is incorrect: $actualStatus", status, actualStatus[0])
}

/**
 * Checks the latest part of git history by commit messages.
 */
fun GitRepository.assertLatestHistory(vararg expectedMessages: String) {
  assertLatestHistory( { it.fullMessage }, *expectedMessages)
}

fun GitRepository.assertLatestSubjects(vararg expectedMessages: String) {
  assertLatestHistory( { it.subject }, *expectedMessages)
}

private fun GitRepository.assertLatestHistory(mapping: (VcsCommitMetadata) -> String, vararg expectedMessages: String) {
  val actualMessages = GitLogUtil.collectMetadata(project, root, false).commits
    .map(mapping)
    .subList(0, expectedMessages.size)
  assertOrderedEquals("History is incorrect", actualMessages, expectedMessages.asList())
}

fun GitRepository.assertStagedChanges(changes: ChangesBuilder.() -> Unit) {
  val cb = ChangesBuilder()
  cb.changes()

  val actualChanges = GitChangeUtils.getStagedChanges(project, root)
  for (change in cb.changes) {
    val found = actualChanges.find(change.matcher)
    PlatformTestCase.assertNotNull("The change [$change] is not staged", found)
    actualChanges.remove(found)
  }
  PlatformTestCase.assertTrue(actualChanges.isEmpty())
}

fun GitRepository.assertCommitted(depth: Int = 1, changes: ChangesBuilder.() -> Unit) {
  val cb = ChangesBuilder()
  cb.changes()

  val allChanges = GitHistoryUtils.history(project, root, "-${depth}")[depth - 1].changes
  val actualChanges = allChanges.toMutableSet()
  for (change in cb.changes) {
    val found = actualChanges.find(change.matcher)
    PlatformTestCase.assertNotNull("The change [$change] wasn't committed\n$allChanges", found)
    actualChanges.remove(found)
  }
  PlatformTestCase.assertTrue(actualChanges.isEmpty())
}

fun GitPlatformTest.assertLastMessage(actual: String, failMessage: String = "Last commit is incorrect") {
  assertMessage(actual, lastMessage(), failMessage)
}

fun assertMessage(actual: String, expected: String, failMessage: String = "Commit message is incorrect") {
  Assertions.assertThat(actual).isEqualToIgnoringWhitespace(expected).withFailMessage(failMessage)
}

fun GitPlatformTest.assertLogMessages(vararg messages: String) {
  val separator = "\u0001"
  val actualMessages = git("log -${messages.size} --pretty=${getPrettyFormatTagForFullCommitMessage(project)}${separator}").split(separator)
  for ((index, message) in messages.withIndex()) {
    Assertions.assertThat(actualMessages[index].trim()).isEqualToIgnoringWhitespace(message.trimIndent())
  }
}

fun ChangeListManager.assertNoChanges() {
  PlatformTestCase.assertEmpty("No changes is expected: ${allChanges.joinToString()}}", allChanges)
}

fun ChangeListManager.assertOnlyDefaultChangelist() {
  PlatformTestCase.assertEquals("Only default changelist is expected among: ${dumpChangeLists()}", 1, changeListsNumber)
  PlatformTestCase.assertEquals("Default changelist is not active", LocalChangeList.DEFAULT_NAME, defaultChangeList.name)
}

fun ChangeListManager.waitScheduledChangelistDeletions() {
  (this as ChangeListManagerImpl).waitEverythingDoneInTestMode()
  ApplicationManager.getApplication().invokeAndWait {
    UIUtil.dispatchAllInvocationEvents()
  }
}

fun ChangeListManager.assertChangeListExists(comment: String): LocalChangeList {
  val changeLists = changeListsCopy
  val list = changeLists.find { it.comment == comment }
  PlatformTestCase.assertNotNull("Didn't find changelist with comment '$comment' among: ${dumpChangeLists()}", list)
  return list!!
}

private fun ChangeListManager.dumpChangeLists() = changeLists.joinToString { "'${it.name}' - '${it.comment}'" }

fun ChangeListManager.assertChanges(changes: ChangesBuilder.() -> Unit): List<Change> {
  this as ChangeListManagerImpl

  val cb = ChangesBuilder()
  cb.changes()

  VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
  ensureUpToDate()

  val vcsChanges = allChanges
  val allChanges = mutableListOf<Change>()
  val actualChanges = HashSet(vcsChanges)

  for (change in cb.changes) {
    val found = actualChanges.find(change.matcher)
    PlatformTestCase.assertNotNull("The change [$change] not found\n$vcsChanges", found)
    actualChanges.remove(found)
    allChanges.add(found!!)
  }
  PlatformTestCase.assertTrue(actualChanges.isEmpty())
  return allChanges
}

class ChangesBuilder {
  data class AChange(val type: FileStatus, val nameBefore: String?, val nameAfter: String, val matcher: (Change) -> Boolean) {
    constructor(type: FileStatus, nameAfter: String, matcher: (Change) -> Boolean) : this(type, null, nameAfter, matcher)

    override fun toString(): String {
      when (type) {
        Change.Type.NEW -> return "A: $nameAfter"
        Change.Type.DELETED -> return "D: $nameAfter"
        Change.Type.MOVED -> return "M: $nameBefore -> $nameAfter"
        else -> return "M: $nameAfter"
      }
    }
  }

  val changes = linkedSetOf<AChange>()

  fun deleted(name: String) {
    PlatformTestCase.assertTrue(changes.add(AChange(FileStatus.DELETED, name) {
      it.fileStatus == FileStatus.DELETED && it.beforeRevision.relativePath == name && it.afterRevision == null
    }))
  }

  fun added(name: String) {
    PlatformTestCase.assertTrue(changes.add(AChange(FileStatus.ADDED, name) {
      it.fileStatus == FileStatus.ADDED && it.beforeRevision == null && it.afterRevision.relativePath == name
    }))
  }

  fun modified(name:String) {
    PlatformTestCase.assertTrue(changes.add(AChange(FileStatus.MODIFIED, name) {
      it.fileStatus == FileStatus.MODIFIED && it.beforeRevision.relativePath == name && it.afterRevision.relativePath == name
    }))
  }

  fun rename(from: String, to: String) {
    PlatformTestCase.assertTrue(changes.add(AChange(FileStatus.MODIFIED, from, to) {
      (it.isRenamed || it.isMoved) && from == it.beforeRevision.relativePath && to == it.afterRevision.relativePath
    }))
  }
}

private val ContentRevision?.relativePath: String
  get() = FileUtil.getRelativePath(Executor.ourCurrentDir().systemIndependentPath, this!!.file.path, '/')!!