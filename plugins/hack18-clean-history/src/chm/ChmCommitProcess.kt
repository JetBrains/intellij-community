// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package chm

import com.intellij.history.integration.LocalHistoryImpl
import com.intellij.history.integration.ui.models.DirectoryHistoryDialogModel
import com.intellij.history.integration.ui.models.EntireFileHistoryDialogModel
import com.intellij.history.integration.ui.models.RevisionItem
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil.markDirtyAndRefresh
import com.intellij.openapi.vfs.VirtualFile
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.checkin.GitCheckinEnvironment
import git4idea.commands.Git
import git4idea.commands.GitCommand.COMMIT
import git4idea.commands.GitLineHandler

class MyCommitProcess(val project: Project, val vcs: GitVcs) : GitCheckinEnvironment.OverridingCommitProcedure {

  val gateway = LocalHistoryImpl.getInstanceImpl().gateway
  val facade = LocalHistoryImpl.getInstanceImpl().facade
  private val git = Git.getInstance()

  override fun commit(ce: GitCheckinEnvironment, changes: List<Change>, message: String) {
    // only a single root is supported
    val root = ProjectLevelVcsManager.getInstance(project).getVcsRootFor(changes[0].virtualFile)!!
    val repo = GitUtil.getRepositoryManager(project).getRepositoryForRoot(root)!!

    // remember actions
    val ancestor = LocalFileSystem.getInstance().findFileByIoFile(ChangesUtil.findCommonAncestor(changes)!!)!!
    val historySinceLastCommit = getLocalHistorySinceLastCommit(ancestor)   // todo checked only for files yet

    // make a standard commit
    ce.myOverridingCommitProcedure = null
    ce.commit(changes, message)
    // remember the hash and reset the branch
    val stdHash = repo.last()
    repo.git("tag std-commit")
    repo.git("reset --keep HEAD^")     // warning: hard reset
    markDirtyAndRefresh(false, true, false, root)

    // -- here is where the magic starts

    // todo extract refactorings from them and apply them first

    for (rev in historySinceLastCommit.reversed()) {



      val h = GitLineHandler(project, root, COMMIT)
      val msg = rev.revision.changeSetName ?: "unnamed"
      h.addParameters("-m", msg)
      h.endOptions()
      h.addRelativePaths(ChangesUtil.getPaths(changes))
      git.runCommand(h)
    }

    // proceed with standard commit
    ce.myOverridingCommitProcedure = null
    ce.commit(changes, message)
  }

  // get history for ancestor
  // find latest label "commit changes"
  // get all changes made after "commit changes"
  fun getLocalHistorySinceLastCommit(f: VirtualFile): List<RevisionItem> {
    val dirHistoryModel = if (f.isDirectory)
      DirectoryHistoryDialogModel(project, gateway, facade, f)
    else
      EntireFileHistoryDialogModel(project, gateway, facade, f)

    val revs = dirHistoryModel.revisions // backwards: 0 is the latest

    var indexOfLastCommit: Int = -1
    for (i in revs.indices) {
      val rev = revs[i]
      if (rev.labels.any { it.label?.startsWith("Commit Changes: ") == true }) {
        indexOfLastCommit = i - 1
        break;
      }
    }

    val revsSinceLastCommit = revs.subList(0, indexOfLastCommit)
    return revsSinceLastCommit
  }


}