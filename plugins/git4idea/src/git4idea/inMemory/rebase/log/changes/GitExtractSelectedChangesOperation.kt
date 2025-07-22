// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.inMemory.rebase.log.changes

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcsUtil.VcsFileUtil
import com.intellij.vcsUtil.VcsUtil
import git4idea.i18n.GitBundle
import git4idea.inMemory.GitObjectRepository
import git4idea.inMemory.objects.GitObject
import git4idea.inMemory.chainCommits
import git4idea.inMemory.rebase.log.GitInMemoryCommitEditingOperation
import org.jetbrains.annotations.NonNls

internal class GitExtractSelectedChangesOperation(
  objectRepo: GitObjectRepository,
  targetCommitMetadata: VcsCommitMetadata,
  private val newMessage: String,
  private val changes: List<Change>,
) : GitInMemoryCommitEditingOperation(objectRepo, targetCommitMetadata) {
  companion object {
    private val LOG = logger<GitExtractSelectedChangesOperation>()
  }

  private val emptyTree by lazy { objectRepo.createTree(emptyMap()) }

  @NonNls
  override val reflogMessage: String = "extract changes from $targetCommitMetadata"
  override val failureTitle: String = GitBundle.message("in.memory.rebase.log.changes.extract.failed.title")

  override suspend fun editCommits(): CommitEditingResult {
    val targetCommit = commits.first()
    LOG.info("Start computing new head for extract operation of $targetCommit")
    val originalTree = objectRepo.findTree(targetCommit.treeOid)
    val includedPaths = changes.map { change ->
      val file = change.afterRevision?.file ?: change.beforeRevision?.file
      checkNotNull(file) { "Can't get a file from a change" }

      VcsUtil.getFilePath(VcsFileUtil.getRelativeFilePath(file.path, objectRepo.repository.root), false)
    }.toSet()

    val parentTree = if (targetCommit.parentsOids.isNotEmpty()) {
      val parentCommit = objectRepo.findCommit(targetCommit.parentsOids.single())
      objectRepo.findTree(parentCommit.treeOid)
    }
    else {
      objectRepo.createTree(mapOf())
    }

    LOG.info("Start tree split")
    val remainingTree = splitTreeByPaths(parentTree, originalTree, includedPaths)
    LOG.info("Finish tree split")

    objectRepo.persistObject(remainingTree)

    val firstCommit = objectRepo.commitTreeWithOverrides(targetCommit, treeOid = remainingTree.oid)
    val secondCommit = objectRepo.commitTreeWithOverrides(targetCommit,
                                                          parentsOids = listOf(firstCommit),
                                                          message = newMessage.toByteArray())
    val newHead = objectRepo.chainCommits(secondCommit, commits.drop(1))
    LOG.info("Finish computing new head for extract operation")
    return CommitEditingResult(newHead, secondCommit)
  }

  private fun splitTreeByPaths(
    parentTree: GitObject.Tree,
    tree: GitObject.Tree,
    paths: Set<FilePath>,
  ): GitObject.Tree {
    val newTreeEntries = mutableMapOf<GitObject.Tree.FileName, GitObject.Tree.Entry>()

    val pathsByFirstComponent = paths
      .groupBy { it.firstComponent }
      .mapValues { it.value.toSet() }

    val allNames = (parentTree.entries.keys + tree.entries.keys).distinct()

    for (name in allNames) {
      val parentEntry = parentTree.entries[name]
      val currentEntry = tree.entries[name]
      val matchingPaths = pathsByFirstComponent[name.value].orEmpty()
      if (matchingPaths.isEmpty()) {
        currentEntry?.let { newTreeEntries[name] = it }
        continue
      }
      validateNoSubmodules(parentEntry, currentEntry)

      val subtreeResult = processSubtree(parentEntry, currentEntry, matchingPaths)
      subtreeResult?.let { newTreeEntries[name] = it }
    }
    return objectRepo.createTree(newTreeEntries)
  }

  private fun processSubtree(
    parentEntry: GitObject.Tree.Entry?,
    currentEntry: GitObject.Tree.Entry?,
    matchingPaths: Set<FilePath>,
  ): GitObject.Tree.Entry? {
    val nextTree = getTreeFromEntry(currentEntry)
    val nextParentTree = getTreeFromEntry(parentEntry)
    val nextPaths = advancePaths(matchingPaths)
    val newSubtree = splitTreeByPaths(nextParentTree, nextTree, nextPaths)
    val hasDirectFileMatch = matchingPaths.any { it.componentCount == 1 }

    return when {
      hasDirectFileMatch -> handleDirectFileMatch(parentEntry, newSubtree)
      else -> handleInSubtreeFileMatch(currentEntry, newSubtree)
    }
  }

  private fun handleDirectFileMatch(
    parentEntry: GitObject.Tree.Entry?,
    newSubtree: GitObject.Tree,
  ): GitObject.Tree.Entry? {
    if (parentEntry != null && parentEntry.mode.isFile()) {
      // extraction of file removal or modification
      if (newSubtree.entries.isNotEmpty()) {
        throw VcsException(GitBundle.message("in.memory.split.tree.mixed.error"))
      }
      return parentEntry
    }
    // extraction of file creation
    if (newSubtree.entries.isEmpty()) {
      return null
    }
    return GitObject.Tree.Entry(GitObject.Tree.FileMode.DIR, newSubtree.oid)
  }

  private fun handleInSubtreeFileMatch(
    currentEntry: GitObject.Tree.Entry?,
    newSubtree: GitObject.Tree,
  ): GitObject.Tree.Entry? {
    if (currentEntry != null && currentEntry.mode.isFile() && newSubtree.entries.isNotEmpty()) {
      throw VcsException(GitBundle.message("in.memory.split.tree.mixed.error"))
    }
    if (newSubtree.entries.isEmpty()) return null

    return GitObject.Tree.Entry(GitObject.Tree.FileMode.DIR, newSubtree.oid)
  }

  private fun getTreeFromEntry(entry: GitObject.Tree.Entry?): GitObject.Tree {
    return entry?.takeIf { it.mode == GitObject.Tree.FileMode.DIR }
             ?.let { objectRepo.findTree(it.oid) }
           ?: emptyTree
  }

  private fun validateNoSubmodules(vararg entries: GitObject.Tree.Entry?) {
    if (entries.any { it?.mode == GitObject.Tree.FileMode.GITLINK }) {
      throw VcsException(GitBundle.message("in.memory.split.tree.submodules.error"))
    }
  }

  private fun advancePaths(paths: Set<FilePath>): Set<FilePath> {
    return paths.mapNotNullTo(mutableSetOf()) { path ->
      val pathString = path.path.trim('/')
      val separatorIndex = pathString.indexOf('/')
      if (separatorIndex != -1) {
        val remainingPath = pathString.substring(separatorIndex + 1)
        VcsUtil.getFilePath(remainingPath, false)
      }
      else {
        null
      }
    }
  }

  private val FilePath.firstComponent: String
    get() {
      return this.path.trim('/').substringBefore("/")
    }

  private val FilePath.componentCount: Int
    get() {
      val pathString = this.path
      val normalizedPath = pathString.trim('/')
      if (normalizedPath.isEmpty()) {
        return 0
      }
      return normalizedPath.count { it == '/' } + 1
    }
}