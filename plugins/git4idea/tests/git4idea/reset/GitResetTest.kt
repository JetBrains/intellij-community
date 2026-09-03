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
package git4idea.reset

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.test.assertSuccessfulNotification
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.impl.HashImpl
import git4idea.test.GitSingleRepoContext
import git4idea.test.file
import git4idea.test.gitSingleRepoContextFixture
import git4idea.test.last
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class GitResetTest {
  private val fixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = fixture.get()

  @Test
  fun `test file is refreshed on hard reset`(): Unit = with(context) {
    val (oldHash, vf) = prepare()

    GitResetOperation(project, mapOf(repo to oldHash), GitResetMode.HARD, EmptyProgressIndicator()).execute()

    assertSuccessfulNotification("Reset successful")
    assertThat(last()).describedAs("Branch is on incorrect point").isEqualTo(oldHash.asString())
    assertThat(String(vf.contentsToByteArray())).describedAs("VirtualFile wasn't refreshed").isEqualTo("initial")
  }

  @Test
  fun `test file status is refreshed on soft reset`(): Unit = with(context) {
    val (oldHash, vf) = prepare()

    GitResetOperation(project, mapOf(repo to oldHash), GitResetMode.SOFT, EmptyProgressIndicator()).execute()

    assertSuccessfulNotification("Reset successful")
    assertThat(last()).describedAs("Branch is on incorrect point").isEqualTo(oldHash.asString())
    changeListManager.ensureUpToDate()
    assertThat(changeListManager.getChange(vf)!!.fileStatus).describedAs("File status wasn't refreshed").isEqualTo(FileStatus.MODIFIED)
  }

  private fun GitSingleRepoContext.prepare(): Pair<Hash, VirtualFile> {
    val file = file("f.txt").create().write("initial")
    val prevHash = HashImpl.build(file.addCommit("created").hash())
    file.append("more" + System.lineSeparator())
    file.addCommit("Added more")
    val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file.file)!!
    return Pair(prevHash, vf)
  }
}