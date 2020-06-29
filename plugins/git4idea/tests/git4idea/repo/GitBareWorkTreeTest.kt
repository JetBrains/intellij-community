// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.repo

import git4idea.test.cd
import git4idea.test.cloneRepo
import git4idea.test.initRepo
import git4idea.test.tac
import java.io.File

class GitBareWorkTreeTest : GitWorkTreeBaseTest() {
  override fun initMainRepo(): String {
    val sourceRepo = File(testRoot, "source")
    assertTrue(sourceRepo.mkdir())
    initRepo(project, sourceRepo.path, true)

    val mainDir = File(testRoot, "main.git")
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