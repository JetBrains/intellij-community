// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.util

import com.intellij.openapi.util.NlsActions
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.graph.PermanentGraph

object GraphSortPresentationUtil {

  @JvmStatic
  val PermanentGraph.SortType.localizedName: String
    @NlsActions.ActionText get() = when (this) {
      PermanentGraph.SortType.Normal -> VcsLogBundle.message("graph.sort.off")
      PermanentGraph.SortType.Bek -> VcsLogBundle.message("graph.sort.standard")
      PermanentGraph.SortType.LinearBek -> VcsLogBundle.message("graph.sort.linear")
    }

  @JvmStatic
  val PermanentGraph.SortType.LocalizedDescription: String
    @NlsActions.ActionDescription get() = when (this) {
      PermanentGraph.SortType.Normal -> VcsLogBundle.message("graph.sort.off.description")
      PermanentGraph.SortType.Bek -> VcsLogBundle.message("graph.sort.standard.description")
      PermanentGraph.SortType.LinearBek -> VcsLogBundle.message("graph.sort.linear.description")
    }
}

