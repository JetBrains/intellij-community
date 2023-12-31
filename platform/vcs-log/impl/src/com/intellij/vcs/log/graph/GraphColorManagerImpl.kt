// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph

import com.intellij.vcs.log.data.RefsModel

class GraphColorManagerImpl(private val refsModel: RefsModel) : GraphColorManager<Int> {
  override fun getColor(headCommit: Int, headFragmentIndex: Int, fragmentIndex: Int): Int {
    if (headFragmentIndex == fragmentIndex) {
      val firstRef = refsModel.bestRefToHead(headCommit) ?: return DEFAULT_COLOR
      return firstRef.name.hashCode()
    }
    return fragmentIndex
  }

  companion object {
    const val DEFAULT_COLOR = 0
  }
}