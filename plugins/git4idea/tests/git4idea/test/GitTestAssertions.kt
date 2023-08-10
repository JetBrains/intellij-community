// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.test

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.openapi.vcs.Executor
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.HeavyPlatformTestCase.assertOrderedEquals
import com.intellij.testFramework.HeavyPlatformTestCase.assertTrue
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcsUtil.VcsUtil.getFilePath
import git4idea.changes.GitChangeUtils
import git4idea.history.GitHistoryUtils
import git4idea.history.GitLogUtil
import git4idea.repo.GitRepository
import org.assertj.core.api.Assertions
import org.junit.Assert.assertEquals
import java.io.File

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
  val actualMessages = GitLogUtil.collectMetadata(project, root).commits
    .map(mapping)
    .subList(0, expectedMessages.size)
  assertOrderedEquals("History is incorrect", actualMessages, expectedMessages.asList())
}

fun GitRepository.assertStagedChanges(changes: ChangesBuilder.() -> Unit) {
  val cb = ChangesBuilder()
  cb.changes()

  val actualChanges = GitChangeUtils.getStagedChanges(project, root)
  for (change in cb.changes) {
    val found = actualChanges.find(change.gitDiffChangeMatcher)
    HeavyPlatformTestCase.assertNotNull("The change [$change] is not staged", found)
    actualChanges.remove(found)
  }
  assertTrue(actualChanges.isEmpty())
}

fun GitRepository.assertCommitted(depth: Int = 1, changes: ChangesBuilder.() -> Unit) {
  val cb = ChangesBuilder()
  cb.changes()

  val allChanges = GitHistoryUtils.history(project, root, "-${depth}")[depth - 1].changes
  val actualChanges = allChanges.toMutableSet()
  for (change in cb.changes) {
    val found = actualChanges.find(change.changeMatcher)
    HeavyPlatformTestCase.assertNotNull("The change [$change] wasn't committed\n$allChanges", found)
    actualChanges.remove(found)
  }
  assertTrue(actualChanges.isEmpty())
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
  HeavyPlatformTestCase.assertEmpty("No changes is expected: ${allChanges.joinToString()}}", allChanges)
}

fun ChangeListManager.assertOnlyDefaultChangelist() {
  HeavyPlatformTestCase.assertEquals("Only default changelist is expected among: ${dumpChangeLists()}", 1, changeListsNumber)
  HeavyPlatformTestCase.assertEquals("Default changelist is not active", LocalChangeList.getDefaultName(), defaultChangeList.name)
}

fun ChangeListManager.waitScheduledChangelistDeletions() {
  (this as ChangeListManagerImpl).waitEverythingDoneInTestMode()
  ApplicationManager.getApplication().invokeAndWait {
    UIUtil.dispatchAllInvocationEvents()
  }
}

fun ChangeListManager.assertChangeListExists(comment: String): LocalChangeList {
  val changeLists = changeLists
  val list = changeLists.find { it.comment == comment }
  HeavyPlatformTestCase.assertNotNull("Didn't find changelist with comment '$comment' among: ${dumpChangeLists()}", list)
  return list!!
}

private fun ChangeListManager.dumpChangeLists() = changeLists.joinToString { "'${it.name}' - '${it.comment}'" }

fun ChangeListManager.assertChanges(changes: ChangesBuilder.() -> Unit): List<Change> {
  this as ChangeListManagerImpl

  val cb = ChangesBuilder()
  cb.changes()

  val vcsChanges = allChanges
  val allChanges = mutableListOf<Change>()
  val actualChanges = HashSet(vcsChanges)

  for (change in cb.changes) {
    val found = actualChanges.find(change.changeMatcher)
    HeavyPlatformTestCase.assertNotNull("The change [$change] not found\n$vcsChanges", found)
    actualChanges.remove(found)
    allChanges.add(found!!)
  }
  HeavyPlatformTestCase.assertEmpty(actualChanges)
  return allChanges
}

class ChangesBuilder {


  data class AChange(val type: FileStatus,
                     val nameBefore: String?,
                     val nameAfter: String?,
                     val matcher: (FileStatus, FilePath?, FilePath?) -> Boolean,
                     val changeMatcher: (Change) -> Boolean) {

    val gitDiffChangeMatcher: (GitChangeUtils.GitDiffChange) -> Boolean = { it -> matcher(it.status, it.beforePath, it.afterPath) }

    constructor(type: FileStatus, nameBefore: String?, nameAfter: String?, matcher: (FileStatus, FilePath?, FilePath?) -> Boolean)
      : this(type, nameBefore, nameAfter, matcher,
             changeMatcher = { matcher(it.fileStatus, it.beforeRevision?.file, it.afterRevision?.file) })

    override fun toString(): String {
      when {
        type == FileStatus.ADDED -> return "A: $nameAfter"
        type == FileStatus.DELETED -> return "D: $nameAfter"
        nameBefore != nameAfter -> return "M: $nameBefore -> $nameAfter"
        else -> return "M: $nameAfter"
      }
    }
  }

  val changes = linkedSetOf<AChange>()

  fun deleted(name: String) {
    assertTrue(changes.add(AChange(FileStatus.DELETED, nameBefore = name, nameAfter = null)
                           { status, beforePath, afterPath ->
                             status == FileStatus.DELETED && beforePath.relativePath == name && afterPath == null
                           }))
  }

  fun added(name: String, newContent: String) {
    val addition = AChange(FileStatus.ADDED, nameBefore = null, nameAfter = name,
                           matcher = { status, beforePath, afterPath ->
                             status == FileStatus.ADDED && beforePath == null && afterPath.relativePath == name },
                           changeMatcher = { change ->
                             change.afterRevision!!.content == newContent
                           })
    assertTrue(changes.add(addition))
  }

  fun added(name: String) {
    assertTrue(changes.add(AChange(FileStatus.ADDED, nameBefore = null, nameAfter = name)
                           { status, beforePath, afterPath ->
                             status == FileStatus.ADDED && beforePath == null && afterPath.relativePath == name
                           }))
  }

  fun modified(name: String, oldContent: String, newContent: String) {
    val modification = AChange(FileStatus.MODIFIED, nameBefore = name, nameAfter = name,
                               matcher = { status, beforePath, afterPath ->
                                status == FileStatus.MODIFIED && beforePath.relativePath == name && afterPath.relativePath == name
                              },
                               changeMatcher = { change ->
                                change.beforeRevision!!.content == oldContent &&
                                change.afterRevision!!.content == newContent
                              })
    assertTrue(changes.add(modification))
  }

  fun modified(name: String) {
    assertTrue(changes.add(AChange(FileStatus.MODIFIED, nameBefore = name, nameAfter = name)
                           { status, beforePath, afterPath ->
                             status == FileStatus.MODIFIED && beforePath.relativePath == name && afterPath.relativePath == name
                           }))
  }

  fun rename(from: String, to: String) {
    assertTrue(changes.add(AChange(FileStatus.MODIFIED, from, to) { status, beforePath, afterPath ->
      beforePath != null && afterPath != null && beforePath.path != afterPath.path &&
      from == beforePath.relativePath && to == afterPath.relativePath
    }))
  }
}

private val FilePath?.relativePath: String
  get() = FileUtil.getRelativePath(Executor.ourCurrentDir().systemIndependentPath, this!!.path, '/')!!