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
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.impl.HashImpl
import git4idea.test.GitSingleRepoTest
import git4idea.test.file
import git4idea.test.last

class GitResetTest : GitSingleRepoTest() {

  fun `test file is refreshed on hard reset`() {
    val (oldHash, vf) = prepare()

    GitResetOperation(myProject, mapOf(myRepo to oldHash), GitResetMode.HARD, EmptyProgressIndicator()).execute()

    assertSuccessfulNotification("Reset successful")
    assertEquals("Branch is on incorrect point", oldHash.asString(), last())
    assertEquals("VirtualFile wasn't refreshed", "initial\n", String(vf.contentsToByteArray()))
  }

  fun `test file status is refreshed on soft reset`() {
    val (oldHash, vf) = prepare()

    GitResetOperation(myProject, mapOf(myRepo to oldHash), GitResetMode.SOFT, EmptyProgressIndicator()).execute()

    assertSuccessfulNotification("Reset successful")
    assertEquals("Branch is on incorrect point", oldHash.asString(), last())
    changeListManager.ensureUpToDate(false)
    assertEquals("File status wasn't refreshed", FileStatus.MODIFIED, changeListManager.getChange(vf)!!.fileStatus)
  }

  private fun prepare(): Pair<Hash, VirtualFile> {
    val file = file("f.txt").create().write("initial\n")
    val prevHash = HashImpl.build(file.addCommit("created").hash())
    file.append("more\n")
    file.addCommit("Added more")
    val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file.file)!!
    return Pair(prevHash, vf)
  }
}