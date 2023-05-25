// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer

import com.intellij.ui.dsl.listCellRenderer.impl.LcrRowImpl
import org.jetbrains.annotations.ApiStatus
import javax.swing.ListCellRenderer

@DslMarker
internal annotation class LcrDslMarker

/**
 * Builds [ListCellRenderer], which can contains several cells with texts or icons placed in one row.
 * Covers most common cases and supports old and new UI
 */
@ApiStatus.Experimental
fun <T> listCellRenderer(init: LcrRow<T>.() -> Unit): ListCellRenderer<T> {
  val result = LcrRowImpl<T>()
  result.init()
  result.onInitFinished()

  return result
}
