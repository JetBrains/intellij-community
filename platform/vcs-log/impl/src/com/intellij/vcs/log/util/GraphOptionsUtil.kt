// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.util

import com.intellij.openapi.util.NlsActions
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.graph.PermanentGraph
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
object GraphOptionsUtil {

  @JvmStatic
  val PermanentGraph.SortType.localizedName: String
    @NlsActions.ActionText get() = when (this) {
      PermanentGraph.SortType.Normal -> VcsLogBundle.message("graph.sort.off")
      PermanentGraph.SortType.Bek -> VcsLogBundle.message("graph.sort.standard")
    }

  @JvmStatic
  val PermanentGraph.SortType.localizedDescription: String
    @NlsActions.ActionDescription get() = when (this) {
      PermanentGraph.SortType.Normal -> VcsLogBundle.message("graph.sort.off.description")
      PermanentGraph.SortType.Bek -> VcsLogBundle.message("graph.sort.standard.description")
    }

  @JvmStatic
  val PermanentGraph.Options.localizedName: String
    @NlsActions.ActionText get() = when (this) {
      is PermanentGraph.Options.Base -> sortType.localizedName
      PermanentGraph.Options.LinearBek -> VcsLogBundle.message("graph.options.linear")
      PermanentGraph.Options.FirstParent -> VcsLogBundle.message("graph.options.first.parent")
    }

  @JvmStatic
  val PermanentGraph.Options.localizedDescription: String
    @NlsActions.ActionDescription get() = when (this) {
      is PermanentGraph.Options.Base -> sortType.localizedDescription
      PermanentGraph.Options.LinearBek -> VcsLogBundle.message("graph.options.linear.description")
      PermanentGraph.Options.FirstParent -> VcsLogBundle.message("graph.options.first.parent.description")
    }

  @JvmStatic
  val PermanentGraph.Options.presentationForTabTitle: String
    @Nls get() = when (this) {
      is PermanentGraph.Options.FirstParent -> "--first-parent" // NON-NLS
      is PermanentGraph.Options.LinearBek -> VcsLogBundle.message("graph.options.linear.presentation")
      else -> ""
    }

  private const val BASE = "Base"
  private const val LINEAR_BEK = "LinearBek"
  private const val FIRST_PARENT = "FirstParent"

  @JvmStatic
  val optionKindNames = listOf(BASE, LINEAR_BEK, FIRST_PARENT)

  @JvmStatic
  val PermanentGraph.Options.kindName
    get() = when (this) {
      is PermanentGraph.Options.Base -> BASE
      PermanentGraph.Options.LinearBek -> LINEAR_BEK
      PermanentGraph.Options.FirstParent -> FIRST_PARENT
    }

  internal fun PermanentGraph.Options.toStringList(): List<String> {
    return when (this) {
      is PermanentGraph.Options.Base -> listOf(kindName, sortType.presentation)
      PermanentGraph.Options.LinearBek, PermanentGraph.Options.FirstParent -> listOf(kindName)
    }
  }

  internal fun List<String>.toGraphOptions(): PermanentGraph.Options? {
    if (isEmpty()) return null

    val kind = first()
    return when (kind) {
      LINEAR_BEK -> PermanentGraph.Options.LinearBek
      FIRST_PARENT -> PermanentGraph.Options.FirstParent
      BASE -> {
        if (size != 2) return null
        val sortType = PermanentGraph.SortType.entries.find { it.presentation == get(1) } ?: return null
        PermanentGraph.Options.Base(sortType)
      }
      else -> null
    }
  }
}

