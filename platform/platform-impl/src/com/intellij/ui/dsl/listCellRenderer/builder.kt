// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer

import com.intellij.ui.dsl.listCellRenderer.impl.LcrRowImpl
import org.jetbrains.annotations.ApiStatus
import javax.swing.ListCellRenderer

@DslMarker
internal annotation class LcrDslMarker

/**
 * Builds [ListCellRenderer], which can contains several cells with texts or icons placed in one row.
 * Covers most common kinds of renderers and provides all necessary functionality:
 *
 * * Rectangular selection and correct insets for old UI
 * * Rounded selection, correct insets and height for new UI
 * * Uses correct colors for text in selected/unselected state
 * * Gray color has different color in selected state
 * * Supports IDE scaling and compact mode
 * * Provides accessibility details for rows: by default it is concatenation of accessible names of all visible cells
 *
 * Because of all described nuances it is hard to write correct own render, so using Kotlin UI DSL is highly recommended
 */
@ApiStatus.Experimental
fun <T> listCellRenderer(init: LcrRow<T>.() -> Unit): ListCellRenderer<T> {
  val result = LcrRowImpl<T>()
  result.init()
  result.onInitFinished()

  return result
}

/**
 * Simplified version of [listCellRenderer] with one text cell
 */
@ApiStatus.Experimental
fun <T> simpleListCellRenderer(textExtractor: (T) -> String): ListCellRenderer<T> {
  return listCellRenderer {
    val text = text()
    renderer { value -> text.text = textExtractor(value) }
  }
}
