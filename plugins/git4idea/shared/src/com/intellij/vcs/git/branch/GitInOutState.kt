// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.branch

import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.platform.vcs.impl.shared.RepositoryId
import com.intellij.util.text.DateFormatUtil
import git4idea.i18n.GitBundle.message
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import java.time.Instant

@ApiStatus.Internal
@Serializable
data class GitInOutProjectState(
  val incoming: Map<RepositoryId, Map<String, Int>>,
  val outgoing: Map<RepositoryId, Map<String, Int>>,
  private val lastFetchTimeMillis: Long? = null,
) {
  constructor(
    incoming: Map<RepositoryId, Map<String, Int>>,
    outgoing: Map<RepositoryId, Map<String, Int>>,
    lastFetchTime: Instant?,
  ) : this(incoming, outgoing, lastFetchTime?.toEpochMilli())

  val lastFetchTime: Instant?
    get() = lastFetchTimeMillis?.let { Instant.ofEpochMilli(it) }

  companion object {
    @JvmField
    val EMPTY: GitInOutProjectState = GitInOutProjectState(emptyMap(), emptyMap(), null as Long?)
  }
}

@ApiStatus.Internal
data class GitInOutCountersInProject(private val inOutRepoState: Map<RepositoryId, GitInOutCountersInRepo>) {
  fun hasOutgoing(): Boolean = inOutRepoState.values.any { it.hasOutgoing() }
  fun hasIncoming(): Boolean = inOutRepoState.values.any { it.hasIncoming() }
  fun hasUnfetched(): Boolean = inOutRepoState.values.any { it.hasUnfetched() }

  fun totalOutgoing(): Int = inOutRepoState.values.sumOf { it.outgoing ?: 0 }
  fun totalIncoming(): Int = inOutRepoState.values.sumOf { it.incoming ?: 0 }

  fun reposWithIncoming(): Int = inOutRepoState.values.count { it.hasIncoming() }
  fun reposWithOutgoing(): Int = inOutRepoState.values.count { it.hasOutgoing() }
  fun reposWithUnfetched(): Int = inOutRepoState.values.count { it.hasUnfetched() }

  fun repositories(): Set<RepositoryId> = inOutRepoState.keys

  companion object {
    @JvmField
    val EMPTY: GitInOutCountersInProject = GitInOutCountersInProject(emptyMap())
  }
}

@ApiStatus.Internal
data class GitInOutCountersInRepo(
  val incoming: Int? = null,
  val outgoing: Int? = null,
) {
  fun hasIncoming(): Boolean = incoming != null
  fun hasOutgoing(): Boolean = outgoing != null
  fun hasUnfetched(): Boolean = incoming == 0
}

@ApiStatus.Internal
fun GitInOutCountersInProject.calcTooltip(): @NlsContexts.Tooltip String? = calcTooltip(lastFetchTime = null)

@ApiStatus.Internal
fun GitInOutCountersInProject.calcTooltip(lastFetchTime: Instant?): @NlsContexts.Tooltip String? {
  if (this == GitInOutCountersInProject.EMPTY) return null

  val repositories = repositories()

  val html = HtmlBuilder()

  val totalIncoming = totalIncoming()
  val totalOutgoing = totalOutgoing()

  if (repositories.size == 1) {
    val message = when {
      hasUnfetched() -> message("branches.tooltip.some.incoming.commits.not.fetched", totalIncoming)
      totalIncoming != 0 && totalOutgoing != 0 -> message("branches.tooltip.incoming.and.outgoing.commits", totalIncoming, totalOutgoing)
      totalIncoming != 0 -> message("branches.tooltip.number.incoming.commits", totalIncoming)
      totalOutgoing != 0 -> message("branches.tooltip.number.outgoing.commits", totalOutgoing)
      else -> null
    }
    if (message != null) {
      html.append(message).br()
    }

    if (lastFetchTime != null) {
      html.append(message("branches.tooltip.last.fetch", DateFormatUtil.formatPrettyDateTime(lastFetchTime.toEpochMilli()))).br()
    }
  }
  else {
    if (totalIncoming != 0) {
      html.append(message("branches.tooltip.number.incoming.commits.in.repositories", totalIncoming(), reposWithIncoming())).br()
    }
    else if (hasUnfetched()) {
      html.append(message("branches.tooltip.some.incoming.commits.not.fetched", totalIncoming)).br()
    }

    if (totalOutgoing != 0) {
      html.append(message("branches.tooltip.number.outgoing.commits.in.repositories", totalOutgoing(), reposWithOutgoing())).br()
    }
  }

  return html.toString().ifEmpty { null }
}

