// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.update

import com.intellij.openapi.vcs.Executor.cd
import git4idea.test.GitPlatformTest
import git4idea.test.git
import git4idea.test.setupDefaultUsername
import git4idea.test.tac
import java.io.File

abstract class GitSubmoduleTestBase : GitPlatformTest() {

  protected fun createPlainRepo(repoName: String): RepositoryAndParent {
    cd(testRoot)
    git("init $repoName")
    val repoDir = File(testRoot, repoName)
    cd(repoDir)
    setupDefaultUsername()
    tac("initial.txt", "initial")
    val parentName = "$repoName.git"
    git("remote add origin ${testRoot}/$parentName")

    cd(testRoot)
    git("init --bare $parentName")
    cd(repoDir)
    git("push -u origin master")
    return RepositoryAndParent(repoName, repoDir, File(testRoot, parentName))
  }

  protected data class RepositoryAndParent(val name: String,
                                           val local: File,
                                           val remote: File)

}