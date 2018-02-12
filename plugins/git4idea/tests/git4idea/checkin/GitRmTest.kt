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
package git4idea.checkin

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsVFSListener
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.vcsUtil.VcsUtil.getFilePath
import git4idea.test.GitSingleRepoTest
import git4idea.test.git
import java.io.File

class GitRmTest : GitSingleRepoTest() {

  override fun getDebugLogCategories() = super.getDebugLogCategories().plus("#" + VcsVFSListener::class.java.name)

  // IDEA-75590
  fun `test empty directories are not removed on git rm`() {
    val nestedDir = File(projectPath, "lib/subdir")
    assertTrue("Directory $nestedDir wasn't created", nestedDir.mkdirs())
    val file = File(nestedDir, "f.txt")
    assertTrue("File $file wasn't created", file.createNewFile())
    git("add .")
    git("commit -m 'added file'")
    val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
    assertNotNull("VirtualFile not found for file $file", vf)
    vf!!

    runInEdtAndWait {
      CommandProcessor.getInstance().executeCommand(project, {
        runWriteAction {
          vf.delete(this)
        }
      }, null, null)
    }
    updateChangeListManager()

    assertFalse("File wasn't deleted", file.exists())
    assertTrue("Directory shouldn't have been deleted", nestedDir.exists())
    val change = changeListManager.getChange(getFilePath(vf))
    assertNotNull(change)
    assertEquals(FileStatus.DELETED, change!!.fileStatus)
  }
}