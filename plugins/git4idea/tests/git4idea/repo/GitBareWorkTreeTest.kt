// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.repo

import git4idea.test.cd
import git4idea.test.cloneRepo
import git4idea.test.initRepo
import git4idea.test.tac
import java.nio.file.Files
import java.nio.file.Path

class GitBareWorkTreeTest : GitWorkTreeBaseTest() {
  override fun initMainRepo(): Path {
    val sourceRepo = testNioRoot.resolve("source")
    Files.createDirectories(testNioRoot)
    initRepo(project, sourceRepo, true)

    val mainDir = testNioRoot.resolve("main.git")
    cloneRepo(sourceRepo.toString(), mainDir.toString(), true)
    return mainDir
  }

  // IDEA-151598
  fun `test current revision`() {
    cd(myRepo)
    val hash = tac("file.txt")
    myRepo.update()

    assertEquals("Current revision identified incorrectly", hash, myRepo.currentRevision)
  }
}