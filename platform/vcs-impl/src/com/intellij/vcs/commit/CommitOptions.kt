// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import org.jetbrains.annotations.ApiStatus

interface CommitOptions {
  val vcsOptions: Map<AbstractVcs, RefreshableOnComponent>
  val beforeCommitChecksOptions: List<RefreshableOnComponent>
  val postCommitChecksOptions: List<RefreshableOnComponent>
  val afterOptions: List<RefreshableOnComponent>
  val extensionOptions: List<RefreshableOnComponent>
}

internal val CommitOptions.allOptions: Sequence<RefreshableOnComponent>
  get() = sequenceOf(vcsOptions.values, beforeCommitChecksOptions, postCommitChecksOptions, afterOptions, extensionOptions).flatten()
internal val CommitOptions.isEmpty: Boolean
  get() = allOptions.none()

@ApiStatus.Internal
class CommitOptionsImpl(
  override val vcsOptions: Map<AbstractVcs, RefreshableOnComponent>,
  override val beforeCommitChecksOptions: List<RefreshableOnComponent>,
  override val postCommitChecksOptions: List<RefreshableOnComponent>,
  override val afterOptions: List<RefreshableOnComponent>,
  override val extensionOptions: List<RefreshableOnComponent>,
) : CommitOptions

@ApiStatus.Internal
class MutableCommitOptions : CommitOptions {
  override val vcsOptions: MutableMap<AbstractVcs, RefreshableOnComponent> = mutableMapOf()
  override val beforeCommitChecksOptions: MutableList<RefreshableOnComponent> = mutableListOf()
  override val postCommitChecksOptions: MutableList<RefreshableOnComponent> = mutableListOf()
  override val afterOptions: MutableList<RefreshableOnComponent> = mutableListOf()
  override val extensionOptions: MutableList<RefreshableOnComponent> = mutableListOf()

  fun add(options: CommitOptions) {
    vcsOptions += options.vcsOptions
    beforeCommitChecksOptions += options.beforeCommitChecksOptions
    postCommitChecksOptions += options.postCommitChecksOptions
    afterOptions += options.afterOptions
    extensionOptions += options.extensionOptions
  }

  fun clear() {
    vcsOptions.clear()
    beforeCommitChecksOptions.clear()
    postCommitChecksOptions.clear()
    afterOptions.clear()
    extensionOptions.clear()
  }

  fun toUnmodifiableOptions(): CommitOptionsImpl {
    return CommitOptionsImpl(java.util.Map.copyOf(vcsOptions),
                             java.util.List.copyOf(beforeCommitChecksOptions),
                             java.util.List.copyOf(postCommitChecksOptions),
                             java.util.List.copyOf(afterOptions),
                             java.util.List.copyOf(extensionOptions))
  }

}
