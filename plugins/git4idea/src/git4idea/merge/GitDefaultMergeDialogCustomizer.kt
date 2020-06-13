// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.merge

import com.intellij.diff.DiffEditorTitleCustomizer
import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser
import com.intellij.openapi.vcs.changes.ui.ChangeListViewerDialog
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.Consumer
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.VcsCommitMetadataImpl
import com.intellij.vcs.log.ui.details.MultipleCommitInfoDialog
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.xml.util.XmlStringUtil.*
import git4idea.GitBranch
import git4idea.GitRevisionNumber
import git4idea.GitUtil.*
import git4idea.GitVcs
import git4idea.changes.GitChangeUtils
import git4idea.history.GitCommitRequirements
import git4idea.history.GitHistoryUtils
import git4idea.history.GitLogUtil
import git4idea.history.GitLogUtil.readFullDetails
import git4idea.history.GitLogUtil.readFullDetailsForHashes
import git4idea.i18n.GitBundle
import git4idea.rebase.GitRebaseUtils
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.util.*
import javax.swing.JPanel

internal open class GitDefaultMergeDialogCustomizer(
  private val project: Project
) : MergeDialogCustomizer() {
  override fun getMultipleFileMergeDescription(files: MutableCollection<VirtualFile>): String {
    val repos = getRepositoriesForFiles(project, files)
      .ifEmpty { getRepositories(project).filter { it.stagingAreaHolder.allConflicts.isNotEmpty() } }

    val mergeBranches = repos.mapNotNull { resolveMergeBranch(it)?.presentable }.toSet()
    if (mergeBranches.isNotEmpty()) {
      val currentBranches = getCurrentBranchNameSet(repos)
      return wrapInHtml(
        GitBundle.message(
          "merge.dialog.description.merge.label.text",
          mergeBranches.size, wrapInHtmlTag(escapeString(mergeBranches.first()), "b"),
          currentBranches.size, wrapInHtmlTag(escapeString(currentBranches.first()), "b")
        )
      )
    }

    val rebaseOntoBranches = repos.mapNotNull { resolveRebaseOntoBranch(it) }
    if (rebaseOntoBranches.isNotEmpty()) {
      val singleCurrentBranch = getSingleCurrentBranchName(repos)
      val singleOntoBranch = rebaseOntoBranches.toSet().singleOrNull()
      return getDescriptionForRebase(singleCurrentBranch, singleOntoBranch?.branchName, singleOntoBranch?.hash)
    }

    val cherryPickCommitDetails = repos.mapNotNull { loadCherryPickCommitDetails(it) }
    if (cherryPickCommitDetails.isNotEmpty()) {
      val singleCherryPick = cherryPickCommitDetails.distinctBy { it.authorName + it.commitMessage }.singleOrNull()
      return wrapInHtml(
        GitBundle.message(
          "merge.dialog.description.cherry.pick.label.text",
          cherryPickCommitDetails.size, wrapInHtmlTag(cherryPickCommitDetails.single().shortHash, "code"),
          (singleCherryPick != null).toInt(),
          escapeString(singleCherryPick?.authorName),
          "<br/>" + wrapInHtmlTag(escapeString(singleCherryPick?.commitMessage), "code")
        )
      )
    }

    return super.getMultipleFileMergeDescription(files)
  }

  override fun getTitleCustomizerList(file: FilePath): DiffEditorTitleCustomizerList {
    val repository = GitRepositoryManager.getInstance(project).getRepositoryForFileQuick(file) ?: return DEFAULT_CUSTOMIZER_LIST
    return when (repository.state) {
      Repository.State.MERGING -> getMergeTitleCustomizerList(repository, file)
      Repository.State.REBASING -> getRebaseTitleCustomizerList(repository, file)
      Repository.State.GRAFTING -> getCherryPickTitleCustomizerList(repository, file)
      else -> DEFAULT_CUSTOMIZER_LIST
    }
  }

  private fun getCherryPickTitleCustomizerList(repository: GitRepository, file: FilePath): DiffEditorTitleCustomizerList {
    val cherryPickHead = tryResolveRef(repository, CHERRY_PICK_HEAD) ?: return DEFAULT_CUSTOMIZER_LIST
    val mergeBase = GitHistoryUtils.getMergeBase(
      repository.project,
      repository.root,
      CHERRY_PICK_HEAD,
      HEAD
    )?.rev ?: return DEFAULT_CUSTOMIZER_LIST
    val leftTitleCustomizer = getTitleWithCommitsRangeDetailsCustomizer(
      GitBundle.getString("merge.dialog.diff.left.title.cherry.pick.label.text"),
      repository,
      file,
      Pair(mergeBase, HEAD)
    )
    val rightTitleCustomizer = getTitleWithCommitDetailsCustomizer(
      wrapInHtml(GitBundle.message("merge.dialog.diff.right.title.cherry.pick.label.text", cherryPickHead.toShortString())),
      repository,
      file,
      cherryPickHead.asString()
    )
    return DiffEditorTitleCustomizerList(leftTitleCustomizer, null, rightTitleCustomizer)
  }

  private fun getMergeTitleCustomizerList(repository: GitRepository, file: FilePath): DiffEditorTitleCustomizerList {
    val currentBranchHash = getHead(repository) ?: return DEFAULT_CUSTOMIZER_LIST
    val currentBranchPresentable = repository.currentBranchName ?: currentBranchHash.toShortString()
    val mergeBranch = resolveMergeBranch(repository) ?: return DEFAULT_CUSTOMIZER_LIST
    val mergeBranchHash = mergeBranch.hash
    val mergeBase = GitHistoryUtils.getMergeBase(
      repository.project,
      repository.root,
      currentBranchHash.asString(),
      mergeBranchHash.asString()
    )?.rev ?: return DEFAULT_CUSTOMIZER_LIST

    fun getChangesFromBranchTitle(branch: String) = wrapInHtml(
      GitBundle.message("merge.dialog.diff.title.changes.from.branch.label.text", wrapInHtmlTag(branch, "b"))
    )

    val leftTitleCustomizer = getTitleWithCommitsRangeDetailsCustomizer(
      getChangesFromBranchTitle(currentBranchPresentable),
      repository,
      file,
      Pair(mergeBase, currentBranchHash.asString())
    )
    val rightTitleCustomizer = getTitleWithCommitsRangeDetailsCustomizer(
      getChangesFromBranchTitle(mergeBranch.presentable),
      repository,
      file,
      Pair(mergeBase, mergeBranchHash.asString())
    )
    return DiffEditorTitleCustomizerList(leftTitleCustomizer, null, rightTitleCustomizer)
  }

  private fun getRebaseTitleCustomizerList(repository: GitRepository, file: FilePath): DiffEditorTitleCustomizerList {
    val currentBranchHash = getHead(repository) ?: return DEFAULT_CUSTOMIZER_LIST
    val rebasingBranchPresentable = repository.currentBranchName ?: currentBranchHash.toShortString()
    val upstreamBranch = resolveRebaseOntoBranch(repository) ?: return DEFAULT_CUSTOMIZER_LIST
    val upstreamBranchHash = upstreamBranch.hash
    val rebaseHead = tryResolveRef(repository, REBASE_HEAD) ?: return DEFAULT_CUSTOMIZER_LIST
    val mergeBase = GitHistoryUtils.getMergeBase(
      repository.project,
      repository.root,
      REBASE_HEAD,
      upstreamBranchHash.asString()
    )?.rev ?: return DEFAULT_CUSTOMIZER_LIST
    val leftTitle = wrapInHtml(
      GitBundle.message(
        "merge.dialog.diff.left.title.rebase.label.text",
        rebaseHead.toShortString(),
        wrapInHtmlTag(escapeString(rebasingBranchPresentable), "b")
      )
    )
    val leftTitleCustomizer = getTitleWithCommitDetailsCustomizer(leftTitle, repository, file, rebaseHead.asString())
    val rightTitle = wrapInHtml(
      if (upstreamBranch.branchName != null) {
        GitBundle.message("merge.dialog.diff.right.title.rebase.with.branch.label.text", wrapInHtmlTag(upstreamBranch.branchName, "b"))
      }
      else {
        GitBundle.getString("merge.dialog.diff.right.title.rebase.without.branch.label.text")
      }
    )
    val rightTitleCustomizer = getTitleWithCommitsRangeDetailsCustomizer(rightTitle, repository, file, Pair(mergeBase, HEAD))
    return DiffEditorTitleCustomizerList(leftTitleCustomizer, null, rightTitleCustomizer)
  }

  private fun loadCherryPickCommitDetails(repository: GitRepository): CherryPickDetails? {
    val cherryPickHead = tryResolveRef(repository, CHERRY_PICK_HEAD) ?: return null

    val shortDetails = GitLogUtil.collectMetadata(project, GitVcs.getInstance(project), repository.root,
                                                  listOf(cherryPickHead.asString()))

    val result = shortDetails.singleOrNull() ?: return null
    return CherryPickDetails(cherryPickHead.toShortString(), result.author.name, result.subject)
  }

  private data class CherryPickDetails(val shortHash: String, val authorName: String, val commitMessage: String)
}

internal fun getDescriptionForRebase(rebasingBranch: String?, baseBranch: String?, baseHash: Hash?): String = wrapInHtml(
  when {
    baseBranch != null -> GitBundle.message(
      "merge.dialog.description.rebase.with.onto.branch.label.text",
      (rebasingBranch != null).toInt(), wrapInHtmlTag(escapeString(rebasingBranch), "b"),
      wrapInHtmlTag(escapeString(baseBranch), "b"),
      (baseHash != null).toInt(), baseHash?.toShortString()
    )
    baseHash != null -> GitBundle.message(
      "merge.dialog.description.rebase.with.hash.label.text",
      (rebasingBranch != null).toInt(), wrapInHtmlTag(escapeString(rebasingBranch), "b"),
      wrapInHtmlTag(baseHash.toShortString(), "b")
    )
    else -> GitBundle.message(
      "merge.dialog.description.rebase.without.onto.info.label.text",
      (rebasingBranch != null).toInt(), wrapInHtmlTag(escapeString(rebasingBranch), "b")
    )
  }
)

internal fun getDefaultLeftPanelTitleForBranch(branchName: String): String = wrapInHtml(
  GitBundle.message("merge.dialog.diff.left.title.default.branch.label.text", wrapInHtmlTag(escapeString(branchName), "b"))
)

internal fun getDefaultRightPanelTitleForBranch(branchName: String?, baseHash: Hash?): String = wrapInHtml(
  when {
    branchName != null -> GitBundle.message(
      "merge.dialog.diff.right.title.default.with.onto.branch.label.text",
      wrapInHtmlTag(escapeString(branchName), "b"),
      (baseHash != null).toInt(), baseHash?.toShortString()
    )
    baseHash != null -> GitBundle.message(
      "merge.dialog.diff.right.title.default.with.hash.label.text",
      wrapInHtmlTag(baseHash.toShortString(), "b")
    )
    else -> GitBundle.getString("merge.dialog.diff.right.title.default.without.onto.info.label.text")
  }
)

@NonNls
private fun resolveMergeBranchOrCherryPick(repository: GitRepository): String? {
  val mergeBranch = resolveMergeBranch(repository)
  if (mergeBranch != null) return mergeBranch.presentable

  val rebaseOntoBranch = resolveRebaseOntoBranch(repository)
  if (rebaseOntoBranch != null) return rebaseOntoBranch.presentable

  val cherryHead = tryResolveRef(repository, CHERRY_PICK_HEAD)
  if (cherryHead != null) return "cherry-pick"
  return null
}

private fun resolveMergeBranch(repository: GitRepository): RefInfo? {
  val mergeHead = tryResolveRef(repository, MERGE_HEAD) ?: return null
  return resolveBranchName(repository, mergeHead)
}

private fun resolveRebaseOntoBranch(repository: GitRepository): RefInfo? {
  val ontoHash = GitRebaseUtils.getOntoHash(repository.project, repository.root) ?: return null
  val repo = GitRepositoryManager.getInstance(repository.project).getRepositoryForRoot(repository.root) ?: return null
  return resolveBranchName(repo, ontoHash)
}

private fun resolveBranchName(repository: GitRepository, hash: Hash): RefInfo {
  var branches: Collection<GitBranch> = repository.branches.findLocalBranchesByHash(hash)
  if (branches.isEmpty()) branches = repository.branches.findRemoteBranchesByHash(hash)
  return RefInfo(hash, branches.singleOrNull()?.name)
}

private fun tryResolveRef(repository: GitRepository, ref: String): Hash? {
  try {
    val revision = GitRevisionNumber.resolve(repository.project, repository.root, ref)
    return HashImpl.build(revision.asString())
  }
  catch (e: VcsException) {
    return null
  }
}

internal fun getSingleMergeBranchName(roots: Collection<GitRepository>): String? = getMergeBranchNameSet(roots).singleOrNull()

private fun getMergeBranchNameSet(roots: Collection<GitRepository>): Set<String> = roots.mapNotNull { repo ->
  resolveMergeBranchOrCherryPick(repo)
}.toSet()

internal fun getSingleCurrentBranchName(roots: Collection<GitRepository>): String? = getCurrentBranchNameSet(roots).singleOrNull()

private fun getCurrentBranchNameSet(roots: Collection<GitRepository>): Set<String> = roots.asSequence().mapNotNull { repo ->
  repo.currentBranchName ?: repo.currentRevision?.let { VcsLogUtil.getShortHash(it) }
}.toSet()

internal fun getTitleWithCommitDetailsCustomizer(
  @Nls title: String,
  repository: GitRepository,
  file: FilePath,
  commit: String
) = DiffEditorTitleCustomizer {
  getTitleWithShowDetailsAction(title) {
    val dlg = ChangeListViewerDialog(repository.project)
    dlg.loadChangesInBackground {
      val changeList = GitChangeUtils.getRevisionChanges(
        repository.project,
        repository.root,
        commit,
        true,
        false,
        false
      )
      ChangeListViewerDialog.ChangelistData(changeList, file)
    }
    dlg.title = StringUtil.stripHtml(title, false)
    dlg.isModal = true
    dlg.show()
  }
}

internal fun getTitleWithCommitsRangeDetailsCustomizer(
  @Nls title: String,
  repository: GitRepository,
  file: FilePath,
  range: Pair<String, String>
) = DiffEditorTitleCustomizer {
  getTitleWithShowDetailsAction(title) {
    val details = mutableListOf<VcsCommitMetadata>()
    val filteredCommits = HashSet<VcsCommitMetadata>()
    ProgressManager.getInstance().runProcessWithProgressSynchronously(
      {
        readFullDetails(
          repository.project,
          repository.root,
          Consumer { commit ->
            val commitMetadata = VcsCommitMetadataImpl(
              commit.id, commit.parents, commit.commitTime, commit.root, commit.subject,
              commit.author, commit.fullMessage, commit.committer, commit.authorTime)
            if (commit.affectedPaths.contains(file)) {
              filteredCommits.add(commitMetadata)
            }
            details.add(commitMetadata)
          },
          "${range.first}..${range.second}")
      },
      GitBundle.message("merge.dialog.customizer.collecting.details.progress"),
      true,
      repository.project)
    val dlg = MergeConflictMultipleCommitInfoDialog(repository.project, repository.root, details, filteredCommits)
    dlg.title = StringUtil.stripHtml(title, false)
    dlg.show()
  }
}

internal fun getTitleWithShowDetailsAction(@Nls title: String, action: () -> Unit): JPanel =
  BorderLayoutPanel()
    .addToCenter(JBLabel(title).setCopyable(true))
    .addToRight(LinkLabel.create(GitBundle.message("merge.dialog.customizer.show.details.link.label"), action))

private fun Boolean.toInt() = if (this) 1 else 0

private class MergeConflictMultipleCommitInfoDialog(
  private val project: Project,
  private val root: VirtualFile,
  commits: List<VcsCommitMetadata>,
  private val filteredCommits: Set<VcsCommitMetadata>
) : MultipleCommitInfoDialog(project, commits) {
  init {
    filterCommitsByConflictingFile()
  }

  @Throws(VcsException::class)
  override fun loadChanges(commits: List<VcsCommitMetadata>): List<Change> {
    val changes = mutableListOf<Change>()
    readFullDetailsForHashes(project, root, commits.map { commit -> commit.id.asString() }, GitCommitRequirements.DEFAULT) { gitCommit ->
      changes.addAll(gitCommit.changes)
    }
    return CommittedChangesTreeBrowser.zipChanges(changes)
  }

  private fun filterCommitsByConflictingFile() {
    setFilter { commit -> filteredCommits.contains(commit) }
  }

  override fun createSouthAdditionalPanel(): JPanel {
    val checkbox = JBCheckBox(GitBundle.message("merge.dialog.customizer.filter.by.conflicted.file.checkbox"), true)
    checkbox.addItemListener {
      if (checkbox.isSelected) {
        filterCommitsByConflictingFile()
      }
      else {
        resetFilter()
      }
    }
    return BorderLayoutPanel().addToCenter(checkbox)
  }
}

private data class RefInfo(val hash: Hash, val branchName: String?) {
  val presentable: String = branchName ?: hash.toShortString()
}