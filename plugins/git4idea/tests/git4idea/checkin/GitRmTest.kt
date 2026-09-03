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

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.test.updateChangeListManager
import com.intellij.vcsUtil.VcsUtil.getFilePath
import git4idea.test.GitSingleRepoContext
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

@TestApplication
class GitRmTest {
  private val fixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = fixture.get()

  // IDEA-75590
  @Test
  fun `test empty directories are not removed on git rm`(): Unit = with(context) {
    val nestedDir = File(projectPath, "lib/subdir")
    assertThat(nestedDir.mkdirs()).describedAs("Directory $nestedDir wasn't created").isTrue()
    val file = File(nestedDir, "f.txt")
    assertThat(file.createNewFile()).describedAs("File $file wasn't created").isTrue()
    git("add .")
    git("commit -m 'added file'")
    val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
    assertThat(vf).describedAs("VirtualFile not found for file $file").isNotNull()
    vf!!

    timeoutRunBlocking(context = Dispatchers.EDT) {
      CommandProcessor.getInstance().executeCommand(project, {
        runWriteAction {
          vf.delete(this)
        }
      }, null, null)
    }
    updateChangeListManager()

    assertThat(file).describedAs("File wasn't deleted").doesNotExist()
    assertThat(nestedDir).describedAs("Directory shouldn't have been deleted").exists()
    val change = changeListManager.getChange(getFilePath(vf))
    assertThat(change).isNotNull()
    assertThat(change!!.fileStatus).isEqualTo(FileStatus.DELETED)
  }
}