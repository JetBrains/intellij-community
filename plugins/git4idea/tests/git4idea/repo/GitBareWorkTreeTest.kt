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
package git4idea.repo

import git4idea.test.GitExecutor.cd
import git4idea.test.GitExecutor.tac
import git4idea.test.GitTestUtil.cloneRepo
import git4idea.test.GitTestUtil.initRepo
import java.io.File

class GitBareWorkTreeTest : GitWorkTreeBaseTest() {

  override fun initMainRepo(): String {
    val sourceRepo = File(myTestRoot, "source")
    assertTrue(sourceRepo.mkdir())
    initRepo(sourceRepo.path, true)

    val mainDir = File(myTestRoot, "main.git")
    val path = mainDir.path
    cloneRepo(sourceRepo.path, path, true)
    return path
  }

  // IDEA-151598
  fun `test current revision`() {
    cd(myRepo)
    val hash = tac("file.txt")
    myRepo.update()

    assertEquals("Current revision identified incorrectly", hash, myRepo.currentRevision)
  }
}