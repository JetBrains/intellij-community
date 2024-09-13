// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch

import com.intellij.dvcs.branch.BranchType
import com.intellij.ide.util.treeView.PathElementIdProvider
import git4idea.GitBranch
import git4idea.GitReference
import git4idea.GitTag
import git4idea.branch.GitBranchType.LOCAL
import git4idea.branch.GitBranchType.REMOTE

interface GitRefType : BranchType, PathElementIdProvider {
  companion object {
    fun of(reference: GitReference): GitRefType {
      return when (reference) {
        is GitBranch -> if (reference.isRemote) REMOTE else LOCAL
        is GitTag -> return GitTagType
        else -> throw IllegalArgumentException()
      }
    }
  }
}

enum class GitBranchType(private val myName: String) : GitRefType {
  LOCAL("LOCAL"), REMOTE("REMOTE");

  override fun getName(): String {
    return myName
  }

  override fun getPathElementId(): String = myName
}

object GitTagType : GitRefType {
  override fun getName(): String = "TAG"
  override fun getPathElementId(): String = name
}
