// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.diff

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.vcsUtil.VcsUtil
import git4idea.index.GitIndexUtil
import git4idea.repo.GitRepository
import git4idea.repo.GitSubmodule

abstract class GitSubmoduleContentRevision(val submodule: GitRepository,
                                           private val revisionNumber: VcsRevisionNumber) : ContentRevision {

  override fun getFile(): FilePath {
    return VcsUtil.getFilePath(submodule.root.path, false) // NB: treating submodule folder as a file in the parent repository
  }

  override fun getRevisionNumber(): VcsRevisionNumber {
    return revisionNumber
  }

  private class Committed(private val parentRepo: GitRepository,
                          submodule: GitRepository,
                          revisionNumber: VcsRevisionNumber) : GitSubmoduleContentRevision(submodule, revisionNumber) {

    override fun getContent(): String? {
      return GitIndexUtil.loadSubmoduleHashAt(submodule, parentRepo, revisionNumber)?.asString()
    }
  }

  private class Current(submodule: GitRepository,
                        revisionNumber: VcsRevisionNumber) : GitSubmoduleContentRevision(submodule, revisionNumber) {
    override fun getContent(): String? {
      return submodule.currentRevision
    }
  }

  companion object {
    @JvmStatic
    fun createRevision(submodule: GitSubmodule, revisionNumber: VcsRevisionNumber): ContentRevision {
      return Committed(submodule.parent, submodule.repository, revisionNumber)
    }

    @JvmStatic
    fun createCurrentRevision(submodule: GitRepository): ContentRevision {
      return Current(submodule, VcsRevisionNumber.NULL)
    }
  }
}

