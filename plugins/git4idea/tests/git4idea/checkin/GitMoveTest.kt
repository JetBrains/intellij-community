/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package git4idea.checkin

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.vcs.VcsConfiguration.StandardConfirmation.ADD
import com.intellij.openapi.vcs.VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY
import com.intellij.openapi.vcs.VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.vcs.AbstractVcsTestCase
import com.intellij.vcsUtil.VcsUtil
import com.intellij.vcs.test.updateChangeListManager
import git4idea.GitVcs
import git4idea.GitUtil.getLogString
import git4idea.test.GitSingleRepoContext
import git4idea.test.addCommit
import git4idea.test.assertUnversioned
import git4idea.test.createDir
import git4idea.test.createFile
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import git4idea.test.prepareUnversionedFile
import git4idea.test.renameFile
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

@TestApplication
class GitMoveTest {
  private val fixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = fixture.get()

  @Test
  fun `test unchanged file should be added to Git on move`(): Unit = with(context) {
    setAddConfirmation(DO_NOTHING_SILENTLY)
    val file = "before.txt"
    echo(file, "some\ncontent\nere")
    addCommit("created $file")

    val vf = LocalFileSystem.getInstance().refreshAndFindFileByPath("$projectPath/$file")!!

    renameFile(vf, "ver-ren.txt")
    assertThat(changeListManager.isUnversioned(vf))
      .describedAs("File should versioned! All changes: " + getLogString(projectPath, changeListManager.allChanges))
      .isFalse()
    val change = changeListManager.getChange(vf)!!
    assertThat(change.isRenamed).describedAs("Change should be rename: $change").isTrue()
  }

  // IDEA-153272
  @Test
  fun `test move unversioned file over existing file should keep the file`(): Unit = with(context) {
    setAddConfirmation(DO_NOTHING_SILENTLY)
    val content = "original content"
    val fileName = "file.txt"
    val originalDir = createDir(projectRoot, "original")
    val unversionedDir = createDir(projectRoot, "unv")

    val original = createFile(originalDir, fileName, content)
    val unversioned = createFile(unversionedDir, fileName, content)
    val originalFile = File(original.path)
    val unversionedFile = File(unversioned.path)

    git("add original/$fileName")
    git("commit -m msg")
    updateChangeListManager()

    timeoutRunBlocking(context = Dispatchers.EDT) {
      CommandProcessor.getInstance().executeCommand(project, {
        runWriteAction {
          original.delete(this)
        }
        runWriteAction {
          unversioned.move(this, original.parent)
        }
      }, null, null)
    }
    updateChangeListManager()

    assertThat(originalFile).describedAs("Original file should exist").exists() // IDEA-153272 failed here: both files were deleted.
    assertThat(unversionedFile).describedAs("Unversioned file shouldn't exist").doesNotExist()
    updateChangeListManager()
    val change = changeListManager.getChange(VcsUtil.getFilePath(originalFile.toPath(), false))
    assertThat(change)
      .describedAs("There should be no change for $originalFile. Changes: ${getLogString(projectPath, changeListManager.allChanges)}")
      .isNull()
  }

  // IDEA-118140
  @Test
  fun `test unversioned file should not be added to Git on move`(): Unit = with(context) {
    setAddConfirmation(DO_NOTHING_SILENTLY)
    val file = prepareUnversionedFile("unv.txt")

    renameFile(file, "unv-ren.txt")
    assertUnversioned(file)
  }

  @Test
  fun `test unversioned file should not be added to Git on move even if add silently`(): Unit = with(context) {
    setAddConfirmation(DO_NOTHING_SILENTLY)
    val file = prepareUnversionedFile("unv.txt")

    setAddConfirmation(DO_ACTION_SILENTLY)
    renameFile(file, "unv-ren.txt")
    assertUnversioned(file)
  }

  private fun GitSingleRepoContext.setAddConfirmation(value: com.intellij.openapi.vcs.VcsShowConfirmationOption.Value) {
    AbstractVcsTestCase.setStandardConfirmation(project, GitVcs.NAME, ADD, value)
  }
}