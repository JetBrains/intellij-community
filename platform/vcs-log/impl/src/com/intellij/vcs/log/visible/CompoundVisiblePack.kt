// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.visible

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.graph.VisibleGraph

/**
 * Compound visible pack, represented by two visible packs (parts).
 * First part [newPack] is fast computable (small) part, and hence can be rendered immediately.
 * Second part [oldPack] (possible a big pack) is already computed and rendered part.
 */
internal class CompoundVisiblePack private constructor(private val newPack: VisiblePack,
                                                       private val oldPack: VisiblePack) :
  VisiblePack(oldPack.dataPack, oldPack.visibleGraph, oldPack.canRequestMore(), oldPack.filters, oldPack.additionalData) {

  private val compoundVisibleGraph = CompoundVisibleGraph(newPack.visibleGraph, oldPack.visibleGraph)

  override fun getVisibleGraph(): VisibleGraph<Int> {
    return compoundVisibleGraph
  }

  override fun getRootAtHead(headCommitIndex: Int): VirtualFile? {
    return newPack.dataPack.refsModel.rootAtHead(headCommitIndex)
           ?: oldPack.dataPack.refsModel.rootAtHead(headCommitIndex)
  }

  private fun getOldNotCompoundVisiblePack(): VisiblePack {
    var curOldPack = oldPack

    while (curOldPack is CompoundVisiblePack) {
      curOldPack = curOldPack.oldPack
    }

    return curOldPack
  }

  companion object {
    @JvmStatic
    fun build(newPack: VisiblePack, oldPack: VisiblePack): CompoundVisiblePack {
      return CompoundVisiblePack(newPack, if (oldPack is CompoundVisiblePack) oldPack.getOldNotCompoundVisiblePack() else oldPack)
    }
  }
}
