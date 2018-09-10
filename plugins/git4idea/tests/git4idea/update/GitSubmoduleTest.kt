// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.update

import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.Executor.cd
import com.intellij.openapi.vcs.Executor.echo
import com.intellij.openapi.vcs.update.UpdatedFiles
import com.intellij.openapi.vfs.VfsUtil
import git4idea.config.UpdateMethod
import git4idea.test.*
import java.io.File

class GitSubmoduleTest : GitSubmoduleTestBase() {

  fun `test submodule is updated via 'git submodule update'`() {
    // prepare second clone & parent.git
    val main2 = createPlainRepo("main")
    val sub3 = createPlainRepo("sub")
    val sub2 = addSubmodule(main2.local, sub3.remote, "sub")

    // clone into the project
    cd(testRoot)
    git("clone --recurse-submodules ${main2.remote} maintmp")
    FileUtil.moveDirWithContent(File(testRoot, "maintmp"), VfsUtil.virtualToIoFile(projectRoot))
    cd(projectRoot)
    setupDefaultUsername()
    val subFile = File(projectPath, "sub")
    cd(subFile)
    setupDefaultUsername()

    refresh()
    val main = registerRepo(project, projectPath)
    val sub = registerRepo(project, subFile.path)

    // push from second clone
    cd(sub2)
    echo("a", "content\n")
    val submoduleHash = addCommit("in submodule")
    git("push")
    cd(main2.local)
    val mainHash = addCommit("Advance the submodule")
    git("push")

    val result = GitUpdateProcess(project, EmptyProgressIndicator(), listOf(main, sub), UpdatedFiles.create(), false, true).update(
      UpdateMethod.MERGE)

    assertEquals("Update result is incorrect", GitUpdateResult.SUCCESS, result)
    assertEquals("Last commit in submodule is incorrect", submoduleHash, sub.last())
    assertEquals("Last commit in main repository is incorrect", mainHash, main.last())
    assertEquals("Submodule should be in detached HEAD", Repository.State.DETACHED, sub.state)
  }
}