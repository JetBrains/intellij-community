// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log

import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsLogObjectsFactory
import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.impl.HashImpl
import git4idea.GitTag
import git4idea.GitUtil
import git4idea.repo.GitRepository

internal fun VcsLogObjectsFactory.createBranchesRefs(repository: GitRepository): Set<VcsRef> {
  val repoInfo = repository.info
  val root = repository.root
  val refs = HashSet<VcsRef>(repoInfo.localBranchesWithHashes.size + repoInfo.remoteBranchesWithHashes.size)
  repoInfo.localBranchesWithHashes.forEach { (branch, hash) ->
    refs.add(createRef(hash, branch.name, GitRefManager.LOCAL_BRANCH, root))
  }
  repoInfo.remoteBranchesWithHashes.forEach { (branch, hash) ->
    refs.add(createRef(hash, branch.nameForLocalOperations, GitRefManager.REMOTE_BRANCH, root))
  }
  val currentRevision = repoInfo.currentRevision
  if (currentRevision != null) { // null => fresh repository
    refs.add(createRef(HashImpl.build(currentRevision), GitUtil.HEAD, GitRefManager.HEAD, root))
  }
  return refs
}

internal fun VcsLogObjectsFactory.createTag(repository: GitRepository, tagName: String, hash: Hash): VcsRef =
  createRef(hash, tagName, GitRefManager.TAG, repository.root)
