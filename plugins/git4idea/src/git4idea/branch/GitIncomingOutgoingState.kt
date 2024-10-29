// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch

import com.intellij.dvcs.ui.BranchActionGroup
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import git4idea.i18n.GitBundle.message
import git4idea.repo.GitRepository
import icons.DvcsImplIcons
import javax.swing.Icon

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

internal fun IncomingOutgoingState.getIcon() : Icon? {
  val hasIncomingIcon = hasIncoming() || hasUnfetched()
  val hasOutgoingIcon = hasOutgoing()

  return when {
    hasIncomingIcon && hasOutgoingIcon -> BranchActionGroup.getIncomingOutgoingIcon()
    hasIncomingIcon -> DvcsImplIcons.Incoming
    hasOutgoingIcon -> DvcsImplIcons.Outgoing
    else -> null
  }
}

internal fun IncomingOutgoingState.calcTooltip(): @NlsContexts.Tooltip String? {
  if (this == IncomingOutgoingState.EMPTY) return null

  val repositories = repositories()

  val html = HtmlBuilder()

  val totalIncoming = totalIncoming()
  val totalOutgoing = totalOutgoing()

  if (repositories.size == 1) {
    if (totalIncoming != 0) {
      html.append(message("branches.tooltip.number.incoming.commits", totalIncoming)).br()
    }
    else if (hasUnfetched()) {
      html.append(message("branches.tooltip.some.incoming.commits.not.fetched", totalIncoming)).br()
    }

    if (totalOutgoing != 0) {
      html.append(message("branches.tooltip.number.outgoing.commits", totalOutgoing)).br()
    }
  } else {
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
  return html.toString()
}

object GitIncomingOutgoingColors {
  internal val INCOMING_FOREGROUND
    get() = JBColor(ColorUtil.fromHex("#3574F0"), ColorUtil.fromHex("#548AF7"))

  internal val OUTGOING_FOREGROUND
    get() = JBColor(ColorUtil.fromHex("#369650"), ColorUtil.fromHex("#57965C"))
}