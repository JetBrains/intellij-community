// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import kotlin.reflect.KMutableProperty0

interface BaseBuilder {
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun withButtonGroup(@NlsContexts.BorderTitle title: String?, buttonGroup: ButtonGroup, body: () -> Unit)

  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun withButtonGroup(buttonGroup: ButtonGroup, body: () -> Unit) {
    withButtonGroup(null, buttonGroup, body)
  }

  fun buttonGroup(init: () -> Unit) {
    buttonGroup(null, init)
  }

  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun buttonGroup(@NlsContexts.BorderTitle title:String? = null, init: () -> Unit) {
    withButtonGroup(title, ButtonGroup(), init)
  }
}

interface RowBuilder : BaseBuilder {
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun createChildRow(label: JLabel? = null,
                     isSeparated: Boolean = false,
                     noGrid: Boolean = false,
                     @Nls title: String? = null): Row

  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun createNoteOrCommentRow(component: JComponent): Row

  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun checkBoxGroup(@Nls title: String?, body: () -> Unit)

  fun row(label: JLabel? = null, separated: Boolean = false, init: Row.() -> Unit): Row {
    return createChildRow(label = label, isSeparated = separated).apply(init)
  }

  fun row(@Nls label: String?, separated: Boolean = false, init: Row.() -> Unit): Row {
    return row(label?.let { Label(it) }, separated = separated, init)
  }

  fun titledRow(@NlsContexts.BorderTitle title: String, init: Row.() -> Unit): Row

  /**
   * Creates row with a huge gap after it, that can be used to group related components.
   * Think of [titledRow] without a title and additional indent.
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun blockRow(init: Row.() -> Unit): Row

  /**
   * Creates row with hideable decorator.
   * It allows to hide some information under the titled decorator
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun hideableRow(@NlsContexts.Separator title: String, incrementsIndent: Boolean = true, init: Row.() -> Unit): Row

  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun hideableRow(@NlsContexts.Separator title: String, init: Row.() -> Unit): Row {
    return hideableRow(title = title, true, init)
  }

  /**
   * Hyperlinks are supported (`<a href=""></a>`), new lines and `<br>` are supported only if no links (file issue if need).
   */
  fun noteRow(@Nls text: String, linkHandler: ((url: String) -> Unit)? = null) {
    createNoteOrCommentRow(noteComponent(text, linkHandler))
  }

  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun commentRow(@Nls text: String) {
    createNoteOrCommentRow(ComponentPanelBuilder.createCommentComponent(text, true, -1, true))
  }

  /**
   * Creates a nested UI DSL panel, with a grid which is independent of this pane.
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun nestedPanel(@NlsContexts.BorderTitle title: String? = null, init: LayoutBuilder.() -> Unit): CellBuilder<DialogPanel>

  fun onGlobalApply(callback: () -> Unit): Row
  fun onGlobalReset(callback: () -> Unit): Row
  fun onGlobalIsModified(callback: () -> Boolean): Row
}

@ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
@Deprecated("Use Kotlin UI DSL Version 2")
inline fun <reified T : Any> InnerCell.buttonGroup(prop: KMutableProperty0<T>, crossinline init: CellBuilderWithButtonGroupProperty<T>.() -> Unit) {
  buttonGroup(prop.toBinding(), init)
}

@ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
@Deprecated("Use Kotlin UI DSL Version 2")
inline fun <reified T : Any> InnerCell.buttonGroup(noinline getter: () -> T, noinline setter: (T) -> Unit, crossinline init: CellBuilderWithButtonGroupProperty<T>.() -> Unit) {
  buttonGroup(PropertyBinding(getter, setter), init)
}

@ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
@Deprecated("Use Kotlin UI DSL Version 2")
inline fun <reified T : Any> InnerCell.buttonGroup(binding: PropertyBinding<T>, crossinline init: CellBuilderWithButtonGroupProperty<T>.() -> Unit) {
  withButtonGroup(ButtonGroup()) {
    CellBuilderWithButtonGroupProperty(binding).init()
  }
}

inline fun <reified T : Any> RowBuilder.buttonGroup(prop: KMutableProperty0<T>, crossinline init: RowBuilderWithButtonGroupProperty<T>.() -> Unit) {
  buttonGroup(prop.toBinding(), init)
}

inline fun <reified T : Any> RowBuilder.buttonGroup(noinline getter: () -> T, noinline setter: (T) -> Unit, crossinline init: RowBuilderWithButtonGroupProperty<T>.() -> Unit) {
  buttonGroup(PropertyBinding(getter, setter), init)
}

inline fun <reified T : Any> RowBuilder.buttonGroup(binding: PropertyBinding<T>, crossinline init: RowBuilderWithButtonGroupProperty<T>.() -> Unit) {
  withButtonGroup(ButtonGroup()) {
    RowBuilderWithButtonGroupProperty(this, binding).init()
  }
}

abstract class Row : Cell(), RowBuilder {
  abstract var enabled: Boolean

  abstract var visible: Boolean

  abstract var subRowsEnabled: Boolean

  abstract var subRowsVisible: Boolean

  /**
   * Indent for child rows of this row, expressed in steps (multiples of [SpacingConfiguration.indentLevel]). Replaces indent
   * calculated from row nesting.
   */
  abstract var subRowIndent: Int

  protected abstract val builder: LayoutBuilderImpl

  /**
   * Specifies the right alignment for the component if the cell is larger than the component plus its gaps.
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  @Deprecated("Use Kotlin UI DSL Version 2")
  inline fun right(init: Row.() -> Unit) {
    alignRight()
    init()
  }

  @PublishedApi
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  @Deprecated("Use Kotlin UI DSL Version 2")
  internal abstract fun alignRight()

  abstract fun largeGapAfter()

  /**
   * Shares cell between components.
   *
   * @param isFullWidth If `true`, the cell occupies the full width of the enclosing component.
   */
  inline fun cell(isVerticalFlow: Boolean = false, isFullWidth: Boolean = false, init: InnerCell.() -> Unit) {
    setCellMode(true, isVerticalFlow, isFullWidth)
    InnerCell(this).init()
    setCellMode(false, isVerticalFlow, isFullWidth)
  }

  @PublishedApi
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  @Deprecated("Use Kotlin UI DSL Version 2")
  internal abstract fun createRow(label: String?): Row

  @PublishedApi
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  @Deprecated("Use Kotlin UI DSL Version 2")
  internal abstract fun setCellMode(value: Boolean, isVerticalFlow: Boolean, fullWidth: Boolean)

  // backward compatibility
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  @Deprecated(level = DeprecationLevel.HIDDEN, message = "deprecated")
  operator fun JComponent.invoke(vararg constraints: CCFlags, gapLeft: Int = 0, growPolicy: GrowPolicy? = null) {
    invoke(constraints = *constraints, growPolicy = growPolicy).withLeftGap(gapLeft)
  }
}

enum class GrowPolicy {
  SHORT_TEXT, MEDIUM_TEXT
}

fun Row.enableIf(predicate: ComponentPredicate) {
  enabled = predicate()
  predicate.addListener { enabled = it }
}

@ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
@Deprecated("Use Kotlin UI DSL Version 2")
fun Row.enableSubRowsIf(predicate: ComponentPredicate) {
  subRowsEnabled = predicate()
  predicate.addListener { subRowsEnabled = it }
}

fun RowBuilder.fullRow(init: InnerCell.() -> Unit): Row = row { cell(isFullWidth = true, init = init) }
