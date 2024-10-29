// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION")

package com.intellij.ui.layout

import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.Label
import com.intellij.ui.components.noteComponent
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.JComponent
import javax.swing.JLabel

@JvmDefaultWithCompatibility
@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2")
interface BaseBuilder

@JvmDefaultWithCompatibility
@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2")
interface RowBuilder : BaseBuilder {
  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun createChildRow(label: JLabel? = null,
                     isSeparated: Boolean = false,
                     noGrid: Boolean = false,
                     @Nls title: String? = null): Row

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun createNoteOrCommentRow(component: JComponent): Row

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun row(label: JLabel? = null, separated: Boolean = false, init: Row.() -> Unit): Row {
    return createChildRow(label = label, isSeparated = separated).apply(init)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun row(label: @Nls String?, separated: Boolean = false, init: Row.() -> Unit): Row {
    return row(label?.let { Label(it) }, separated = separated, init)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun titledRow(@NlsContexts.BorderTitle title: String, init: Row.() -> Unit): Row

  /**
   * Hyperlinks are supported (`<a href=""></a>`), new lines and `<br>` are supported only if no links (file issue if need).
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun noteRow(text: @Nls String, linkHandler: ((url: String) -> Unit)? = null) {
    createNoteOrCommentRow(noteComponent(text, linkHandler))
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun onGlobalApply(callback: () -> Unit): Row
}

@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2")
abstract class Row : Cell(), RowBuilder {
  @get:Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  @get:ApiStatus.ScheduledForRemoval
  @set:Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  @set:ApiStatus.ScheduledForRemoval
  abstract var enabled: Boolean

  @get:Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  @get:ApiStatus.ScheduledForRemoval
  @set:Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  @set:ApiStatus.ScheduledForRemoval
  abstract var visible: Boolean

  @get:Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  @get:ApiStatus.ScheduledForRemoval
  @set:Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  @set:ApiStatus.ScheduledForRemoval
  abstract var subRowsVisible: Boolean

  @PublishedApi
  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  internal abstract fun alignRight()

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  abstract fun largeGapAfter()

  /**
   * Shares cell between components.
   *
   * @param isFullWidth If `true`, the cell occupies the full width of the enclosing component.
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  inline fun cell(isVerticalFlow: Boolean = false, isFullWidth: Boolean = false, init: InnerCell.() -> Unit) {
    setCellMode(true, isVerticalFlow, isFullWidth)
    InnerCell(this).init()
    setCellMode(false, isVerticalFlow, isFullWidth)
  }

  @PublishedApi
  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  internal abstract fun setCellMode(value: Boolean, isVerticalFlow: Boolean, fullWidth: Boolean)
}

@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2")
enum class GrowPolicy {
  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  SHORT_TEXT,
  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  MEDIUM_TEXT
}
