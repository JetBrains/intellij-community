// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.branch

data class LinkedBranchDataImpl(private val presentableRootName: String,
                                private val branchName: String?,
                                private val linkedBranchName: String?) : LinkedBranchData {
  override fun getPresentableRootName() = presentableRootName
  override fun getBranchName() = branchName
  override fun getLinkedBranchName() = linkedBranchName
}