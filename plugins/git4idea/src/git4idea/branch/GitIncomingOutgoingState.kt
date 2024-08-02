// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch

import git4idea.repo.GitRepository

internal data class IncomingOutgoingState(private val inOutRepoState: Map<GitRepository, InOutRepoState>) {
  fun hasOutgoing(): Boolean = inOutRepoState.values.any { it.hasOutgoing() }
  fun hasIncoming(): Boolean = inOutRepoState.values.any { it.hasIncoming() }
  fun hasUnfetched(): Boolean = inOutRepoState.values.any { it.hasUnfetched() }

  fun totalOutgoing(): Int = inOutRepoState.values.sumOf { it.outgoing ?: 0 }
  fun totalIncoming(): Int = inOutRepoState.values.sumOf { it.incoming ?: 0 }

  fun reposWithIncoming(): Int = inOutRepoState.values.count { it.hasIncoming() }
  fun reposWithOutgoing(): Int = inOutRepoState.values.count { it.hasOutgoing() }
  fun reposWithUnfetched(): Int = inOutRepoState.values.count { it.hasUnfetched() }

  fun repositories() = inOutRepoState.keys

  companion object {
    @JvmField
    val EMPTY = IncomingOutgoingState(emptyMap())
  }
}

internal data class InOutRepoState(
  val incoming: Int? = null,
  val outgoing: Int? = null,
) {
  fun hasIncoming(): Boolean = incoming != null
  fun hasOutgoing(): Boolean = outgoing != null
  fun hasUnfetched(): Boolean = incoming == 0
}