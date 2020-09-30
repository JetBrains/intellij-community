// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.update

import com.intellij.dvcs.DvcsUtil.getPushSupport
import com.intellij.dvcs.branch.DvcsSyncSettings
import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.Executor.cd
import com.intellij.openapi.vcs.Executor.echo
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.update.UpdatedFiles
import git4idea.config.UpdateMethod.MERGE
import git4idea.config.UpdateMethod.REBASE
import git4idea.push.GitPushOperation
import git4idea.push.GitPushRepoResult
import git4idea.push.GitPushSupport
import git4idea.push.GitRejectedPushUpdateDialog
import git4idea.push.GitRejectedPushUpdateDialog.REBASE_EXIT_CODE
import git4idea.repo.GitRepository
import git4idea.test.*
import java.nio.file.Path

class GitSubmoduleTest : GitSubmoduleTestBase() {
  private lateinit var main: GitRepository
  private lateinit var sub: GitRepository
  private lateinit var main2: RepositoryAndParent
  private lateinit var sub2: Path

  private lateinit var dirtyScopeManager: VcsDirtyScopeManager

  override fun setUp() {
    super.setUp()

    dirtyScopeManager = VcsDirtyScopeManager.getInstance(project)

    // prepare second clone & parent.git
    main2 = createPlainRepo("main")
    val sub3 = createPlainRepo("sub")
    sub2 = addSubmodule(main2.local, sub3.remote, "sub")

    // clone into the project
    cd(testNioRoot)
    git("clone --recurse-submodules ${main2.remote} maintmp")
    FileUtil.moveDirWithContent(testNioRoot.resolve("maintmp").toFile(), projectRoot.toNioPath().toFile())
    cd(projectRoot)
    setupDefaultUsername()
    val subFile = projectNioRoot.resolve("sub")
    cd(subFile)
    setupDefaultUsername()

    refresh()
    main = registerRepo(project, projectNioRoot)
    sub = registerRepo(project, subFile)
  }

  fun `test submodule in detached HEAD state is updated via 'git submodule update'`() {
    // push from second clone
    cd(sub2)
    echo("a", "content\n")
    val submoduleHash = addCommit("in submodule")
    git("push")
    cd(main2.local)
    val mainHash = addCommit("Advance the submodule")
    git("push")

    insertLogMarker("update process")
    val result = GitUpdateProcess(project, EmptyProgressIndicator(), listOf(main, sub), UpdatedFiles.create(), null, false, true).update(MERGE)

    assertEquals("Update result is incorrect", GitUpdateResult.SUCCESS, result)
    assertEquals("Last commit in submodule is incorrect", submoduleHash, sub.last())
    assertEquals("Last commit in main repository is incorrect", mainHash, main.last())
    assertEquals("Submodule should be in detached HEAD", Repository.State.DETACHED, sub.state)
  }

  fun `test submodule in detached HEAD state doesn't fail in case of sync control`() {
    settings.syncSetting = DvcsSyncSettings.Value.SYNC
    try {
      // push from second clone
      cd(sub2)
      echo("a", "content\n")
      val submoduleHash = addCommit("in submodule")
      git("push")
      cd(main2.local)
      val mainHash = addCommit("Advance the submodule")
      git("push")

      insertLogMarker("update process")
      val result = GitUpdateProcess(project, EmptyProgressIndicator(), listOf(main, sub), UpdatedFiles.create(), null, false, true).update(MERGE)

      assertEquals("Update result is incorrect", GitUpdateResult.SUCCESS, result)
      assertEquals("Last commit in submodule is incorrect", submoduleHash, sub.last())
      assertEquals("Last commit in main repository is incorrect", mainHash, main.last())
      assertEquals("Submodule should be in detached HEAD", Repository.State.DETACHED, sub.state)
    }
    finally {
      settings.syncSetting = DvcsSyncSettings.Value.NOT_DECIDED
    }
  }

  fun `test submodule on branch is updated as a normal repository`() {
    // push from second clone
    cd(sub2)
    echo("a", "content\n")
    val submoduleHash = addCommit("in submodule")
    git("push")

    // prepare commit in first sub clone
    cd(sub)
    git("checkout master")
    echo("b", "content\n")
    addCommit("msg")

    insertLogMarker("update process")
    val result = GitUpdateProcess(project, EmptyProgressIndicator(), listOf(main, sub), UpdatedFiles.create(), null, false, true).update(REBASE)

    assertEquals("Update result is incorrect", GitUpdateResult.SUCCESS, result)
    assertEquals("Submodule should be on branch", "master", sub.currentBranchName)
    assertEquals("Commit from 2nd clone not found in submodule", submoduleHash, sub.git("rev-parse HEAD^"))
  }

  fun `test push rejected in submodule updates it and pushes again`() {
    // push from second clone
    cd(sub2)
    echo("a", "content\n")
    val submoduleHash = addCommit("in submodule")
    git("push")

    // prepare commit in first sub clone
    cd(sub)
    git("checkout master")
    echo("b", "content\n")
    addCommit("msg")

    val updateAllRootsIfPushRejected = settings.shouldUpdateAllRootsIfPushRejected()
    try {
      settings.setUpdateAllRootsIfPushRejected(false)
      dialogManager.registerDialogHandler(GitRejectedPushUpdateDialog::class.java, TestDialogHandler { REBASE_EXIT_CODE })

      val pushSpecs = listOf(main, sub).associate {
        it to makePushSpec(it, "master", "origin/master")
      }

      insertLogMarker("push process")
      val result = GitPushOperation(project, getPushSupport(vcs) as GitPushSupport, pushSpecs, null, false, false).execute()

      val mainResult = result.results[main]!!
      val subResult = result.results[sub]!!

      assertEquals("Submodule push result is incorrect", GitPushRepoResult.Type.SUCCESS, subResult.type)
      assertEquals("Main push result is incorrect", GitPushRepoResult.Type.UP_TO_DATE, mainResult.type)
      assertEquals("Submodule should be on branch", "master", sub.currentBranchName)
      assertEquals("Commit from 2nd clone not found in submodule", submoduleHash, sub.git("rev-parse HEAD^"))
    }
    finally {
      settings.setUpdateAllRootsIfPushRejected(updateAllRootsIfPushRejected)
    }
  }

  // IDEA-234159
  fun `test modified submodule is visible in local changes`() {
    dirtyScopeManager.markEverythingDirty()
    changeListManager.waitUntilRefreshed()
    assertNoChanges()

    cd(sub)
    echo("a", "content\n")
    addCommit("in submodule")

    dirtyScopeManager.markEverythingDirty()
    changeListManager.waitUntilRefreshed()
    cd(projectPath)
    assertChanges {
      modified("sub")
    }
  }

  private fun insertLogMarker(title: String) {
    LOG.info("")
    LOG.info("--------- STARTING ${title.toUpperCase()} -----------")
    LOG.info("")
  }
}