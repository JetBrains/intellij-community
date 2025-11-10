// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.cherrypick

import com.intellij.ide.IdeBundle
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.impl.HashImpl
import git4idea.config.GitVcsApplicationSettings
import git4idea.i18n.GitBundle
import git4idea.test.*

abstract class GitCherryPickTest : GitSingleRepoTest() {
  protected lateinit var vcsAppSettings: VcsApplicationSettings
  protected lateinit var gitVcsSettings: GitVcsApplicationSettings

  override fun setUp() {
    super.setUp()
    vcsAppSettings = VcsApplicationSettings.getInstance()
    gitVcsSettings = GitVcsApplicationSettings.getInstance()
  }

  protected fun `check dirty tree conflicting with commit`() {
    val file = file("c.txt")
    file.create("initial\n").addCommit("initial")
    branch("feature")
    val commit = file.append("master\n").addCommit("fix #1").hash()
    checkout("feature")
    file.append("local\n")

    cherryPick(commit, expectSuccess = false)

    assertErrorNotification("Cherry-pick failed",
                            "${shortHash(commit)} fix #1" +
                            UIUtil.BR +
                            GitBundle.message("warning.your.local.changes.would.be.overwritten.by", "cherry-pick", "shelve"),
                            listOf(IdeBundle.message("action.show.files"),
                                   GitBundle.message("apply.changes.save.and.retry.operation", "Shelve")))
  }

  protected fun `check untracked file conflicting with commit`() {
    branch("feature")
    val file = file("untracked.txt")
    val commit = file.create("master\n").addCommit("fix #1").hash()
    checkout("feature")
    file.create("untracked\n")

    cherryPick(commit, expectSuccess = false)

    assertErrorNotification("Untracked Files Prevent Cherry-pick", """
      Move or commit them before cherry-pick""")
  }

  protected fun `check conflict with cherry-picked commit should show merge dialog`() {
    val initial = tac("c.txt", "base\n")
    val commit = repo.appendAndCommit("c.txt", "master")
    repo.checkoutNew("feature", initial)
    repo.appendAndCommit("c.txt", "feature")

    `do nothing on merge`()

    cherryPick(commit, expectSuccess = false)

    `assert merge dialog was shown`()
  }

  protected fun `check resolve conflicts and commit`() {
    val commit = repo.prepareConflict()
    `mark as resolved on merge`()
    vcsHelper.onCommit { msg ->
      git("commit -am '$msg'")
      true
    }

    cherryPick(commit)

    `assert commit dialog was shown`()
    assertLastMessage("on_master")
    repo.assertCommitted {
      modified("c.txt")
    }
    assertSuccessfulNotification("Cherry-pick successful",
                                 "${shortHash(commit)} on_master")
    changeListManager.assertNoChanges()
    changeListManager.waitScheduledChangelistDeletions()
    changeListManager.assertOnlyDefaultChangelist()
  }

  protected fun cherryPick(vararg hashes: String, expectSuccess: Boolean = true) {
    updateChangeListManager()
    val details = readDetails(*hashes)
    val cherryPickResult = GitCherryPicker(project).cherryPick(details)
    assertEquals(expectSuccess, cherryPickResult)
  }

  protected fun shortHash(hash: String) = HashImpl.build(hash).toShortString()
}
