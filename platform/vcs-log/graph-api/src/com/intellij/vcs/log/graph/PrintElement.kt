// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph

/**
 * Element in visible commit graph [VisibleGraph]
 */
interface PrintElement {
  val rowIndex: VcsLogVisibleGraphIndex
  val positionInCurrentRow: Int
  val colorId: Int
  val isSelected: Boolean
}
