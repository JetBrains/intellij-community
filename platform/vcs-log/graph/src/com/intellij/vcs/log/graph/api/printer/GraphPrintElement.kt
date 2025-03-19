// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.api.printer

import com.intellij.vcs.log.graph.PrintElement
import com.intellij.vcs.log.graph.api.elements.GraphElement

interface GraphPrintElement: PrintElement {
  val graphElement: GraphElement
}