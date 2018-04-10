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

import com.intellij.openapi.vcs.VcsConfiguration.StandardConfirmation.ADD
import com.intellij.openapi.vfs.VirtualFile
import git4idea.test.GitSingleRepoTest
import git4idea.test.assertStatus

class GitAddTest : GitSingleRepoTest() {

  override fun setUp() {
    super.setUp()
    ADD.doNothing()
  }

  fun `test add one file`() {
    val file = prepareUnversionedFile("unv.txt")
    addUnversionedFile(file)
    repo.assertStatus(file, 'A')
  }

  fun `test add directory`() {
    val file = prepareUnversionedFile("dir/unv.txt")
    addUnversionedFile(projectRoot.findChild("dir")!!)
    repo.assertStatus(file, 'A')
  }

  private fun addUnversionedFile(file: VirtualFile) {
    changeListManager.addUnversionedFiles(changeListManager.addChangeList("dummy", null), listOf(file))
  }
}