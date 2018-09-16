// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.diff

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.vcsUtil.VcsUtil
import com.intellij.vcsUtil.VcsUtil.getFilePath
import git4idea.index.GitIndexUtil
import git4idea.repo.GitRepository

abstract class GitSubmoduleContentRevision(private val submodule: GitRepository,
                                           private val revisionNumber: VcsRevisionNumber) : ContentRevision {

  override fun getFile(): FilePath {
    return VcsUtil.getFilePath(submodule.root.path, false) // NB: treating submodule folder as a file in the parent repository
  }

  override fun getRevisionNumber(): VcsRevisionNumber {
    return revisionNumber
  }

  private class Committed(private val parentRepo: GitRepository,
                          private val submodule: GitRepository,
                          revisionNumber: VcsRevisionNumber) : GitSubmoduleContentRevision(submodule, revisionNumber) {

    override fun getContent(): String? {
      val filePath = getFilePath(submodule.root)
      val lsTree = GitIndexUtil.listTree(parentRepo, listOf(filePath), revisionNumber)
      if (lsTree.size != 1) {
        throw VcsException("Unexpected output of ls-tree command for submodule [$filePath] at [$revisionNumber]: $lsTree")
      }
      val tree = lsTree[0]
      if (tree !is GitIndexUtil.StagedSubrepo) {
        throw VcsException("Unexpected type of ls-tree for submodule [$filePath] at [$revisionNumber]: $tree")
      }
      if (tree.path != filePath) {
        throw VcsException("Submodule path [${submodule.root.path}] doesn't match the ls-tree output path [${tree.path.path}]")
      }
      return tree.blobHash
    }
  }

  private class Current(private val submodule: GitRepository,
                        revisionNumber: VcsRevisionNumber) : GitSubmoduleContentRevision(submodule, revisionNumber) {
    override fun getContent(): String? {
      return submodule.currentRevision
    }
  }

  companion object {
    @JvmStatic
    fun createRevision(parentRepo: GitRepository, submodule: GitRepository, revisionNumber: VcsRevisionNumber): ContentRevision {
      return GitSubmoduleContentRevision.Committed(parentRepo, submodule, revisionNumber)
    }

    @JvmStatic
    fun createCurrentRevision(submodule: GitRepository): ContentRevision {
      return GitSubmoduleContentRevision.Current(submodule, VcsRevisionNumber.NULL)
    }
  }
}

