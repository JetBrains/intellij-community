/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.test

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.PlatformTestCase.assertOrderedEquals
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcsUtil.VcsUtil.getFilePath
import git4idea.changes.GitChangeUtils
import git4idea.history.GitHistoryUtils
import git4idea.history.GitLogUtil
import git4idea.repo.GitRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File

fun GitRepository.assertStatus(file: VirtualFile, status: Char) {
  this.assertStatus(getFilePath(file), status)
}

fun GitRepository.assertStatus(file: FilePath, status: Char) {
  this.assertStatus(file.ioFile, status)
}

fun GitRepository.assertStatus(file: File, status: Char) {
  val actualStatus = git(this, "status --porcelain ${file.path}")
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
  val actualMessages = GitLogUtil.collectMetadata(this.project, this.root).commits
    .map(mapping)
    .subList(0, expectedMessages.size)
  assertOrderedEquals("History is incorrect", actualMessages, expectedMessages.asList())
}

fun GitRepository.assertStagedChanges(changes: ChangesBuilder.() -> Unit) {
  val cb = ChangesBuilder()
  cb.changes()

  val actualChanges = GitChangeUtils.getStagedChanges(this.project, this.root)
  for (change in cb.changes) {
    val found = actualChanges.find(change.matcher)
    PlatformTestCase.assertNotNull("The change [$change] is not staged", found)
    actualChanges.remove(found)
  }
  PlatformTestCase.assertTrue(actualChanges.isEmpty())
}

fun GitRepository.assertCommitted(changes: ChangesBuilder.() -> Unit) {
  val cb = ChangesBuilder()
  cb.changes()

  val actualChanges = GitHistoryUtils.history(this.project, this.root, "-1")[0].changes
  for (change in cb.changes) {
    val found = actualChanges.find(change.matcher)
    PlatformTestCase.assertNotNull("The change [$change] wasn't committed", found)
    actualChanges.remove(found)
  }
  PlatformTestCase.assertTrue(actualChanges.isEmpty())
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

  fun added(name: String) {
    PlatformTestCase.assertTrue(changes.add(AChange(FileStatus.ADDED, name) {
      it.fileStatus == FileStatus.ADDED && it.beforeRevision == null && it.afterRevision!!.file.name == name
    }))
  }

  fun modified(name:String) {
    PlatformTestCase.assertTrue(changes.add(AChange(FileStatus.MODIFIED, name) {
      it.fileStatus == FileStatus.MODIFIED && it.beforeRevision!!.file.name == name && it.afterRevision!!.file.name == name
    }))
  }

  fun rename(from: String, to: String) {
    PlatformTestCase.assertTrue(changes.add(AChange(FileStatus.MODIFIED, from, to) {
      it.isRenamed && from == it.beforeRevision!!.file.name && to == it.afterRevision!!.file.name
    }))
  }
}