// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Conditions
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.util.VcsLogUtil

import java.awt.*
import java.util.HashMap

class CurrentBranchConditionCache(private val logData: VcsLogData) {
  private var conditions: MutableMap<VirtualFile, Condition<Int>> = HashMap()

  fun getContainedInCurrentBranchCondition(root: VirtualFile): Condition<Int> {
    LOG.assertTrue(EventQueue.isDispatchThread())

    var condition: Condition<Int>? = conditions[root]
    if (condition == null) {
      condition = doGetContainedInCurrentBranchCondition(root)
      conditions[root] = condition
    }
    return condition
  }

  private fun doGetContainedInCurrentBranchCondition(root: VirtualFile): Condition<Int> {
    val dataPack = logData.dataPack
    if (dataPack === DataPack.EMPTY) return Conditions.alwaysFalse()

    val branchName = logData.getLogProvider(root).getCurrentBranch(root) ?: return Conditions.alwaysFalse()
    val branchRef = VcsLogUtil.findBranch(dataPack.refsModel, root, branchName) ?: return Conditions.alwaysFalse()
    val branchIndex = logData.getCommitIndex(branchRef.commitHash, branchRef.root)
    return dataPack.permanentGraph.getContainedInBranchCondition(setOf(branchIndex))
  }

  fun clear() {
    conditions = HashMap()
  }

  companion object {
    private val LOG = Logger.getInstance(CurrentBranchConditionCache::class.java)
  }
}
