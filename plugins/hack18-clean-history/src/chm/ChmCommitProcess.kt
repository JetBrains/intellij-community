// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package chm

import com.intellij.history.core.RevisionsCollector
import com.intellij.history.core.revisions.Difference
import com.intellij.history.core.revisions.Revision
import com.intellij.history.integration.LocalHistoryImpl
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.invokeAndWaitIfNeed
import com.intellij.openapi.diff.impl.patch.FilePatch
import com.intellij.openapi.diff.impl.patch.IdeaTextPatchBuilder
import com.intellij.openapi.diff.impl.patch.formove.PatchApplier
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
import git4idea.util.GitVcsConsoleWriter

class MyCommitProcess(val project: Project, val vcs: GitVcs) : GitCheckinEnvironment.OverridingCommitProcedure {

  val gateway = LocalHistoryImpl.getInstanceImpl().gateway!!
  val facade = LocalHistoryImpl.getInstanceImpl().facade!!
  private val git = Git.getInstance()
  lateinit var root: VirtualFile

  override fun commit(ce: GitCheckinEnvironment, changes: List<Change>, message: String) {
    // only a single root is supported
    root = ProjectLevelVcsManager.getInstance(project).getVcsRootFor(changes[0].virtualFile)!!
    val repo = GitUtil.getRepositoryManager(project).getRepositoryForRoot(root)!!

    // remember actions
    val ancestor = LocalFileSystem.getInstance().findFileByIoFile(ChangesUtil.findCommonAncestor(changes)!!)!!
    // todo checked only for files yet
    val historySinceLastCommit = getLocalHistoryItems(getLocalHistorySinceLastCommit(ancestor))

    // make a standard commit
    ce.myOverridingCommitProcedure = null
    ce.commit(changes, message)
    // remember the hash and reset the branch
    val stdHash = repo.last()
    repo.git("tag --force std-commit")
    repo.git("reset --keep HEAD^")
    markDirtyAndRefresh(false, true, false, root)

    // -- here is where the magic starts
    // todo extract refactorings from them and apply them first

    var revisionsToApply = moveRefactoringsToStart(historySinceLastCommit)

    for (rev in historySinceLastCommit) {
      val file = ancestor

      applyRevision(rev, root, file)

      val msg = rev.comment ?: "unnamed"
      commit(root, file, msg)
    }

    ce.myOverridingCommitProcedure = null // for safety
  }

  private fun moveRefactoringsToStart(origHistory: List<Item>): List<Item> {
    return origHistory; // todo
  }

  private fun applyRevision(rev: Item, root: VirtualFile, file: VirtualFile) {
    invokeAndWaitIfNeed {
      //      runWriteAction {
      // as content:
      //        val entry = rev.revision.findEntry()
      //        val c = entry.content
      //        if (!c.isAvailable) throw IllegalStateException("$c is not available")
      //        file.setBinaryContent(c.bytes, -1, entry.timestamp)   // todo only one file yet
      //      }

      // as patches
      val patchApplier = PatchApplier<Any>(project, root, rev.patches, null, null) // todo no dialogs, fail on conflicts
      patchApplier.execute(false, true)
    }
  }

  private fun commit(root: VirtualFile, file: VirtualFile, msg: String) {
    val h = GitLineHandler(project, root, COMMIT)
    h.setStdoutSuppressed(false)
    h.setStderrSuppressed(false)
    h.addParameters("-m", msg)
    h.addParameters("--only")
    h.endOptions()
    h.addRelativeFiles(listOf(file))
    val result = git.runCommand(h)
    GitVcsConsoleWriter.getInstance(project).showMessage(result.outputAsJoinedString)
  }

  fun getLocalHistorySinceLastCommit(f: VirtualFile): List<Revision> {
    val revisions = getLocalHistory(f) // (1) backwards: 0 is the latest (2) labels are included as revisions

    val historyAfterLastCommit = mutableListOf<Revision>()
    var lastRev = false
    for (rev in revisions) {
      if (rev.isLabel) {
        if (rev.label!!.startsWith("Commit Changes: ")) {
          lastRev = true
        }
      }
      else {
        historyAfterLastCommit.add(rev)   // one more, the last one, because the name is written for previous revision
        if (lastRev) break
      }
    }

    return historyAfterLastCommit.reversed()
  }

  fun getLocalHistoryItems(revisions: List<Revision>): List<Item> {
    val res = mutableListOf<Item>()
    var comment : String? = null
    var prevRev: Revision? = null
    for (i in revisions.indices) {
      val rev = revisions[i]
      // name from prev entry, content from current
      if (i == 0) {
        comment = rev.changeSetName
        prevRev = rev
        continue
      }

      val diffs = prevRev!!.getDifferencesWith(rev)
      val changes = getChanges(diffs)
      val patches = IdeaTextPatchBuilder.buildPatch(project, changes, root.path, false)

      res.add(Item(rev, comment, patches))
      comment = rev.changeSetName
      prevRev = rev
    }
    return res
  }

  fun getChanges(diffs: List<Difference>): List<Change> {
    val result = mutableListOf<Change>()
    for (d in diffs) {
      result.add(createChange(d))
    }
    return result
  }

  private fun createChange(d: Difference): Change {
    return Change(d.getLeftContentRevision(gateway), d.getRightContentRevision(gateway))
  }

  fun getLocalHistory(file: VirtualFile) : List<Revision> {
    return ReadAction.compute<List<Revision>, RuntimeException> {
      gateway.registerUnsavedDocuments(facade)
      val path = file.path
      val root = gateway.createTransientRootEntry()
      val collector = RevisionsCollector(facade, root, path, project.locationHash, null)
      collector.result as List<Revision>
    }
  }

  data class Item(val revision: Revision,
                  val comment: String?,
                  val patches: List<FilePatch>)

}