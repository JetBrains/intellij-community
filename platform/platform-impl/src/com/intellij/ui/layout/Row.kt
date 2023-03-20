// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.layout

import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.Label
import com.intellij.ui.components.noteComponent
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JLabel

@JvmDefaultWithCompatibility
@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2")
interface BaseBuilder {
  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun withButtonGroup(title: @NlsContexts.BorderTitle String?, buttonGroup: ButtonGroup, body: () -> Unit)

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun withButtonGroup(buttonGroup: ButtonGroup, body: () -> Unit) {
    withButtonGroup(null, buttonGroup, body)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun buttonGroup(init: () -> Unit) {
    withButtonGroup(null, ButtonGroup(), init)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun buttonGroup(@NlsContexts.BorderTitle title:String? = null, init: () -> Unit) {
    withButtonGroup(title, ButtonGroup(), init)
  }
}

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
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun checkBoxGroup(@Nls title: String?, body: () -> Unit)

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun row(label: JLabel? = null, separated: Boolean = false, init: Row.() -> Unit): Row {
    return createChildRow(label = label, isSeparated = separated).apply(init)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun row(label: @Nls String?, separated: Boolean = false, init: Row.() -> Unit): Row {
    return row(label?.let { Label(it) }, separated = separated, init)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun titledRow(@NlsContexts.BorderTitle title: String, init: Row.() -> Unit): Row

  /**
   * Creates row with a huge gap after it, that can be used to group related components.
   * Think of [titledRow] without a title and additional indent.
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun blockRow(init: Row.() -> Unit): Row

  /**
   * Creates row with hideable decorator.
   * It allows to hide some information under the titled decorator
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun hideableRow(@NlsContexts.Separator title: String, incrementsIndent: Boolean = true, init: Row.() -> Unit): Row

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun hideableRow(@NlsContexts.Separator title: String, init: Row.() -> Unit): Row {
    return hideableRow(title = title, true, init)
  }

  /**
   * Hyperlinks are supported (`<a href=""></a>`), new lines and `<br>` are supported only if no links (file issue if need).
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun noteRow(@Nls text: String, linkHandler: ((url: String) -> Unit)? = null) {
    createNoteOrCommentRow(noteComponent(text, linkHandler))
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun commentRow(@Nls text: String) {
    createNoteOrCommentRow(ComponentPanelBuilder.createCommentComponent(text, true, -1, true))
  }

  /**
   * Creates a nested UI DSL panel, with a grid which is independent of this pane.
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun nestedPanel(@NlsContexts.BorderTitle title: String? = null, init: LayoutBuilder.() -> Unit): CellBuilder<DialogPanel>

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun onGlobalApply(callback: () -> Unit): Row

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun onGlobalReset(callback: () -> Unit): Row

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun onGlobalIsModified(callback: () -> Boolean): Row
}

@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2")
abstract class Row : Cell(), RowBuilder {
  @get:Deprecated("Use Kotlin UI DSL Version 2")
  @get:ApiStatus.ScheduledForRemoval
  @set:Deprecated("Use Kotlin UI DSL Version 2")
  @set:ApiStatus.ScheduledForRemoval
  abstract var enabled: Boolean

  @get:Deprecated("Use Kotlin UI DSL Version 2")
  @get:ApiStatus.ScheduledForRemoval
  @set:Deprecated("Use Kotlin UI DSL Version 2")
  @set:ApiStatus.ScheduledForRemoval
  abstract var visible: Boolean

  @get:Deprecated("Use Kotlin UI DSL Version 2")
  @get:ApiStatus.ScheduledForRemoval
  @set:Deprecated("Use Kotlin UI DSL Version 2")
  @set:ApiStatus.ScheduledForRemoval
  abstract var subRowsEnabled: Boolean

  @get:Deprecated("Use Kotlin UI DSL Version 2")
  @get:ApiStatus.ScheduledForRemoval
  @set:Deprecated("Use Kotlin UI DSL Version 2")
  @set:ApiStatus.ScheduledForRemoval
  abstract var subRowsVisible: Boolean

  /**
   * Indent for child rows of this row, expressed in steps (multiples of [SpacingConfiguration.indentLevel]). Replaces indent
   * calculated from row nesting.
   */
  @get:Deprecated("Use Kotlin UI DSL Version 2")
  @get:ApiStatus.ScheduledForRemoval
  @set:Deprecated("Use Kotlin UI DSL Version 2")
  @set:ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  abstract var subRowIndent: Int

  @get:Deprecated("Use Kotlin UI DSL Version 2")
  @get:ApiStatus.ScheduledForRemoval
  protected abstract val builder: LayoutBuilderImpl

  /**
   * Specifies the right alignment for the component if the cell is larger than the component plus its gaps.
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  inline fun right(init: Row.() -> Unit) {
    alignRight()
    init()
  }

  @PublishedApi
  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  internal abstract fun alignRight()

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
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

@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2")
fun Row.enableIf(predicate: ComponentPredicate) {
  enabled = predicate()
  predicate.addListener { enabled = it }
}

@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2")
fun RowBuilder.fullRow(init: InnerCell.() -> Unit): Row = row { cell(isFullWidth = true, init = init) }
