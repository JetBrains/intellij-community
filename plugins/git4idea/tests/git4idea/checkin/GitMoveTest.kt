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

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.vcs.Executor.echo
import com.intellij.openapi.vcs.VcsConfiguration.StandardConfirmation.ADD
import com.intellij.openapi.vcs.VcsVFSListener
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitUtil.getLogString
import git4idea.test.GitSingleRepoTest
import git4idea.test.addCommit
import git4idea.test.git
import java.io.File

class GitMoveTest : GitSingleRepoTest() {

  override fun getDebugLogCategories() = super.getDebugLogCategories().plus("#" + VcsVFSListener::class.java.name)

  fun `test unchanged file should be added to Git on move`() {
    ADD.doNothing()
    val file = "before.txt"
    echo(file, "some\ncontent\nere")
    addCommit("created $file")

    val vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(projectPath + "/$file")!!

    renameFile(vf, "ver-ren.txt")
    assertTrue("File should versioned! All changes: " + getLogString(projectPath, changeListManager.allChanges),
               !changeListManager.isUnversioned(vf))
    val change = changeListManager.getChange(vf)!!
    assertTrue("Change should be rename: " + change, change.isRenamed)
  }

  // IDEA-153272
  fun `test move unversioned file over existing file should keep the file`() {
    ADD.doNothing()
    val content = "original content"
    val fileName = "file.txt"
    val originalDir = projectRoot.createDir("original")
    val unversionedDir = projectRoot.createDir("unv")

    val original = originalDir.createFile(fileName, content)
    val unversioned = unversionedDir.createFile(fileName, content)
    val originalFile = File(original.path)
    val unversionedFile = File(unversioned.path)

    git("add original/$fileName")
    git("commit -m msg")
    updateChangeListManager()

    runInEdtAndWait {
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

    assertTrue("Original file should exist", originalFile.exists()) // IDEA-153272 failed here: both files were deleted.
    assertFalse("Unversioned file shouldn't exist", unversionedFile.exists())
    updateChangeListManager()
    val change = changeListManager.getChange(VcsUtil.getFilePath(originalFile))
    assertNull("There should be no change for $originalFile. Changes: ${getLogString(projectPath, changeListManager.allChanges)}", change)
  }

  // IDEA-118140
  fun `test unversioned file should not be added to Git on move`() {
    ADD.doNothing()
    val file = prepareUnversionedFile("unv.txt")

    renameFile(file, "unv-ren.txt")
    assertUnversioned(file)
  }

  fun `test unversioned file should not be added to Git on move even if add silently`() {
    ADD.doNothing()
    val file = prepareUnversionedFile("unv.txt")

    ADD.doSilently()
    renameFile(file, "unv-ren.txt")
    assertUnversioned(file)
  }
}