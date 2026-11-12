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
import com.intellij.openapi.vcs.VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.vcs.AbstractVcsTestCase
import git4idea.GitVcs
import git4idea.test.GitSingleRepoContext
import git4idea.test.assertStatus
import git4idea.test.gitSingleRepoContextFixture
import git4idea.test.prepareUnversionedFile
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class GitAddTest {
  private val fixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = fixture.get()

  @BeforeEach
  fun setUp(): Unit = with(context) {
    AbstractVcsTestCase.setStandardConfirmation(project, GitVcs.NAME, ADD, DO_NOTHING_SILENTLY)
  }

  @Test
  fun `test add one file`(): Unit = with(context) {
    val file = prepareUnversionedFile("unv.txt")
    addUnversionedFile(file)
    repo.assertStatus(file, 'A')
  }

  @Test
  fun `test add directory`(): Unit = with(context) {
    val file = prepareUnversionedFile("dir/unv.txt")
    addUnversionedFile(projectRoot.findChild("dir")!!)
    repo.assertStatus(file, 'A')
  }

  private fun GitSingleRepoContext.addUnversionedFile(file: VirtualFile) {
    changeListManager.addUnversionedFiles(changeListManager.addChangeList("dummy", null), listOf(file))
  }
}