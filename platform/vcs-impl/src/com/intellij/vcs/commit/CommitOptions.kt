// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.ui.RefreshableOnComponent

interface CommitOptions {
  val vcsOptions: Map<AbstractVcs, RefreshableOnComponent>
  val beforeOptions: List<RefreshableOnComponent>
  val afterOptions: List<RefreshableOnComponent>
}

val CommitOptions.allOptions: Sequence<RefreshableOnComponent>
  get() = sequenceOf(vcsOptions.values, beforeOptions, afterOptions).flatten()
val CommitOptions.isEmpty: Boolean
  get() = allOptions.none()

class CommitOptionsImpl(
  override val vcsOptions: Map<AbstractVcs, RefreshableOnComponent>,
  override val beforeOptions: List<RefreshableOnComponent>,
  override val afterOptions: List<RefreshableOnComponent>
) : CommitOptions

class MutableCommitOptions : CommitOptions {
  override val vcsOptions: MutableMap<AbstractVcs, RefreshableOnComponent> = mutableMapOf()
  override val beforeOptions: MutableList<RefreshableOnComponent> = mutableListOf()
  override val afterOptions: MutableList<RefreshableOnComponent> = mutableListOf()

  fun add(options: CommitOptions) {
    vcsOptions += options.vcsOptions
    beforeOptions += options.beforeOptions
    afterOptions += options.afterOptions
  }

  fun clear() {
    vcsOptions.clear()
    beforeOptions.clear()
    afterOptions.clear()
  }

  fun toUnmodifiableOptions(): CommitOptionsImpl {
    return CommitOptionsImpl(java.util.Map.copyOf(vcsOptions),
                             java.util.List.copyOf(beforeOptions),
                             java.util.List.copyOf(afterOptions))
  }
}