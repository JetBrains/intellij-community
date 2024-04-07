// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.facade

import com.intellij.vcs.log.graph.actions.GraphAction
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.impl.print.elements.PrintElementWithGraphElement
import java.awt.Cursor

interface LinearGraphController {
  val compiledGraph: LinearGraph

  fun performLinearGraphAction(action: LinearGraphAction): LinearGraphAnswer

  interface LinearGraphAction : GraphAction {
    override val affectedElement: PrintElementWithGraphElement?
  }

  // Integer = nodeId
  open class LinearGraphAnswer @JvmOverloads constructor(val graphChanges: GraphChanges<Int>?,
                                                         val cursorToSet: Cursor? = null,
                                                         val selectedNodeIds: Set<Int>? = null) {
    constructor(cursor: Cursor?, selectedNodeIds: Set<Int>?) : this(null, cursor, selectedNodeIds)

    open val graphUpdater: Runnable? get() = null
  }
}
