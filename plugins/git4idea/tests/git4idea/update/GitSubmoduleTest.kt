// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.update

import com.intellij.dvcs.branch.DvcsSyncSettings
import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.update.UpdatedFiles
import com.intellij.testFramework.junit5.TestApplication
import git4idea.config.GitSaveChangesPolicy
import git4idea.config.UpdateMethod.MERGE
import git4idea.config.UpdateMethod.REBASE
import git4idea.test.addCommit
import git4idea.test.assertChanges
import git4idea.test.assertChangesWithRefresh
import git4idea.test.assertCommitted
import git4idea.test.assertNoChanges
import git4idea.test.cd
import git4idea.test.commit
import git4idea.test.git
import git4idea.test.gitPlatformContextFixture
import git4idea.test.last
import git4idea.test.runUnderProgress
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Locale

private val LOG = logger<GitSubmoduleTest>()

@TestApplication
internal class GitSubmoduleTest {
  private val contextFixture = gitPlatformContextFixture(saveChangesPolicy = GitSaveChangesPolicy.STASH).gitSubmoduleProjectFixture()
  private val context: GitSubmoduleProjectContext get() = contextFixture.get()

  private val dirtyScopeManager: VcsDirtyScopeManager get() = VcsDirtyScopeManager.getInstance(context.project)

  @Test
  fun `test submodule in detached HEAD state is updated via 'git submodule update'`(): Unit = with(context) {
    // push from second clone
    cd(sub2)
    echo("a", "content\n")
    val submoduleHash = addCommit("in submodule")
    git("push")
    cd(main2.local)
    val mainHash = addCommit("Advance the submodule")
    git("push")

    insertLogMarker("update process")
    val result = runUnderProgress { indicator ->
      GitUpdateProcess(project, indicator, listOf(main, sub), UpdatedFiles.create(), null, false, true).update(MERGE)
    }

    assertThat(result).describedAs("Update result is incorrect").isEqualTo(GitUpdateResult.SUCCESS)
    assertThat(sub.last()).describedAs("Last commit in submodule is incorrect").isEqualTo(submoduleHash)
    assertThat(main.last()).describedAs("Last commit in main repository is incorrect").isEqualTo(mainHash)
    assertThat(sub.state).describedAs("Submodule should be in detached HEAD").isEqualTo(Repository.State.DETACHED)
  }

  @Test
  fun `test submodule in detached HEAD state doesn't fail in case of sync control`(): Unit = with(context) {
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
      val result = runUnderProgress { indicator ->
        GitUpdateProcess(project, indicator, listOf(main, sub), UpdatedFiles.create(), null, false, true).update(MERGE)
      }

      assertThat(result).describedAs("Update result is incorrect").isEqualTo(GitUpdateResult.SUCCESS)
      assertThat(sub.last()).describedAs("Last commit in submodule is incorrect").isEqualTo(submoduleHash)
      assertThat(main.last()).describedAs("Last commit in main repository is incorrect").isEqualTo(mainHash)
      assertThat(sub.state).describedAs("Submodule should be in detached HEAD").isEqualTo(Repository.State.DETACHED)
    }
    finally {
      settings.syncSetting = DvcsSyncSettings.Value.NOT_DECIDED
    }
  }

  @Test
  fun `test submodule on branch is updated as a normal repository`(): Unit = with(context) {
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
    val result = runUnderProgress { indicator ->
      GitUpdateProcess(project, indicator, listOf(main, sub), UpdatedFiles.create(), null, false, true).update(REBASE)
    }

    assertThat(result).describedAs("Update result is incorrect").isEqualTo(GitUpdateResult.SUCCESS)
    assertThat(sub.currentBranchName).describedAs("Submodule should be on branch").isEqualTo("master")
    assertThat(sub.git("rev-parse HEAD^")).describedAs("Commit from 2nd clone not found in submodule").isEqualTo(submoduleHash)
  }

  // IDEA-234159
  @Test
  fun `test modified submodule is visible in local changes`(): Unit = with(context) {
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

  @Test
  fun `test modified submodule marks parent as dirty`(): Unit = with(context) {
    dirtyScopeManager.markEverythingDirty()
    changeListManager.waitUntilRefreshed()
    assertNoChanges()

    cd(sub)
    touch("a", "content\n")
    addCommit("initial in submodule")

    cd(projectPath)
    addCommit("initial")

    dirtyScopeManager.markEverythingDirty()
    changeListManager.waitUntilRefreshed()
    assertNoChanges()

    cd(sub)
    overwrite("a", "new content\n")
    dirtyScopeManager.fileDirty(childPath("a"))
    changeListManager.waitUntilRefreshed()

    cd(projectPath)
    assertChanges {
      modified("sub")
      modified("sub/a")
    }

    cd(sub)
    overwrite("a", "content\n")
    dirtyScopeManager.fileDirty(childPath("a"))
    changeListManager.waitUntilRefreshed()
    changeListManager.waitUntilRefreshed() // two refreshes needed

    cd(projectPath)
    assertNoChanges()
  }

  @Test
  fun `test commit into submodule and parent at once`(): Unit = with(context) {
    dirtyScopeManager.markEverythingDirty()
    changeListManager.waitUntilRefreshed()
    assertNoChanges()

    cd(sub)
    touch("a", "content\n")
    addCommit("initial in submodule")

    cd(projectPath)
    touch("b", "content\n")
    addCommit("initial")

    dirtyScopeManager.markEverythingDirty()
    changeListManager.waitUntilRefreshed()
    assertNoChanges()

    cd(sub)
    overwrite("a", "new content\n")

    cd(projectPath)
    overwrite("b", "new content\n")

    val changes = assertChangesWithRefresh {
      modified("b")
      modified("sub")
      modified("sub/a")
    }

    commit(changes)
    assertNoChanges()

    sub.assertCommitted {
      modified("sub/a")
    }
    main.assertCommitted {
      modified("b")
      modified("sub")
    }
  }

  private fun insertLogMarker(title: String) {
    LOG.info("")
    LOG.info("--------- STARTING ${title.uppercase(Locale.getDefault())} -----------")
    LOG.info("")
  }
}
