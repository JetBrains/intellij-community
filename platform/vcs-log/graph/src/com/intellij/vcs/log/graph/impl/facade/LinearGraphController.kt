// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.facade

import com.intellij.vcs.log.graph.actions.GraphAction
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.api.printer.GraphPrintElement
import org.jetbrains.annotations.ApiStatus
import java.awt.Cursor

@ApiStatus.Internal
interface LinearGraphController {
  val compiledGraph: LinearGraph

  fun performLinearGraphAction(action: LinearGraphAction): LinearGraphAnswer

  interface LinearGraphAction : GraphAction {
    override val affectedElement: GraphPrintElement?
  }

  // Integer = nodeId
  data class LinearGraphAnswer(val graphChanges: GraphChanges<Int>?, val cursorToSet: Cursor?, val selectedNodeIds: Set<Int>?,
                               val graphUpdater: Runnable?) {
    constructor(cursor: Cursor?, selectedNodeIds: Set<Int>?) : this(null, cursor, selectedNodeIds, null)

    @JvmOverloads
    constructor(graphChanges: GraphChanges<Int>?, graphUpdater: Runnable? = null) : this(graphChanges, null, null, graphUpdater)
  }
}
