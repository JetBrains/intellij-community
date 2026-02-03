// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsLogObjectsFactory
import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.impl.HashImpl
import git4idea.GitUtil
import git4idea.repo.GitRepoInfo
import git4idea.repo.GitRepository

internal fun VcsLogObjectsFactory.createBranchesRefs(repository: GitRepository): Set<VcsRef> {
  val repoInfo = repository.info
  val root = repository.root
  val refs = HashSet<VcsRef>(repoInfo.localBranchesWithHashes.size + repoInfo.remoteBranchesWithHashes.size)
  return createBranchesRefsSequence(repoInfo, root).toCollection(refs)
}

internal fun VcsLogObjectsFactory.createBranchesRefsSequence(repository: GitRepository): Sequence<VcsRef> {
  val repoInfo = repository.info
  val root = repository.root
  return createBranchesRefsSequence(repoInfo, root)
}

private fun VcsLogObjectsFactory.createBranchesRefsSequence(
  repoInfo: GitRepoInfo,
  root: VirtualFile,
): Sequence<VcsRef> {
  val localRefs = repoInfo.localBranchesWithHashes.asSequence().map { (branch, hash) ->
    createRef(hash, branch.name, GitRefManager.LOCAL_BRANCH, root)
  }
  val remoteRefs = repoInfo.remoteBranchesWithHashes.asSequence().map { (branch, hash) ->
    createRef(hash, branch.nameForLocalOperations, GitRefManager.REMOTE_BRANCH, root)
  }
  val headRef = repoInfo.currentRevision?.let {
    sequenceOf(createRef(HashImpl.build(it), GitUtil.HEAD, GitRefManager.HEAD, root))
  } ?: emptySequence()
  return localRefs + remoteRefs + headRef
}

internal fun VcsLogObjectsFactory.createTag(repository: GitRepository, tagName: String, hash: Hash): VcsRef =
  createRef(hash, tagName, GitRefManager.TAG, repository.root)
