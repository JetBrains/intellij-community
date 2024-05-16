// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph

/**
 */
interface PrintElement {
  val rowIndex: Int
  val positionInCurrentRow: Int
  val colorId: Int
  val isSelected: Boolean
}
