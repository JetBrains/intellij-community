// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.ui.components.Label
import com.intellij.ui.components.noteComponent
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JLabel
import kotlin.reflect.KMutableProperty0

interface BaseBuilder {
  fun withButtonGroup(buttonGroup: ButtonGroup, body: () -> Unit)

  fun buttonGroup(init: () -> Unit) {
    withButtonGroup(ButtonGroup(), init)
  }
}

interface RowBuilder : BaseBuilder {
  fun createChildRow(label: JLabel? = null,
                     isSeparated: Boolean = false,
                     noGrid: Boolean = false,
                     title: String? = null): Row

  fun createNoteOrCommentRow(component: JComponent): Row

  fun row(label: JLabel? = null, separated: Boolean = false, init: Row.() -> Unit): Row {
    return createChildRow(label = label, isSeparated = separated).apply(init)
  }

  fun row(label: String?, separated: Boolean = false, init: Row.() -> Unit): Row {
    return createChildRow(label?.let { Label(it) }, isSeparated = separated).apply(init)
  }

  fun titledRow(title: String, init: Row.() -> Unit): Row {
    return createChildRow(isSeparated = true, title = title).apply(init)
  }

  /**
   * Creates row with hideable decorator
   * It allows to hide some information under the titled decorator
   */
  fun hideableRow(title: String, init: Row.() -> Unit): Row

  /**
   * Hyperlinks are supported (`<a href=""></a>`), new lines and <br> are supported only if no links (file issue if need).
   */
  fun noteRow(text: String, linkHandler: ((url: String) -> Unit)? = null) {
    createNoteOrCommentRow(noteComponent(text, linkHandler))
  }

  fun commentRow(text: String) {
    createNoteOrCommentRow(ComponentPanelBuilder.createCommentComponent(text, true, -1))
  }
}

inline fun <reified T : Any> InnerCell.buttonGroup(prop: KMutableProperty0<T>, crossinline init: CellBuilderWithButtonGroupProperty<T>.() -> Unit) {
  buttonGroup(prop.toBinding(), init)
}

inline fun <reified T : Any> InnerCell.buttonGroup(noinline getter: () -> T, noinline setter: (T) -> Unit, crossinline init: CellBuilderWithButtonGroupProperty<T>.() -> Unit) {
  buttonGroup(PropertyBinding(getter, setter), init)
}

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

  protected abstract val builder: LayoutBuilderImpl

  /**
   * Specifies the right alignment for the component if the cell is larger than the component plus its gaps.
   */
  inline fun right(init: Row.() -> Unit) {
    alignRight()
    init()
  }

  @PublishedApi
  internal abstract fun alignRight()

  abstract fun largeGapAfter()

  /**
   * Shares cell between components.
   *
   * @param isFullWidth If true, the cell occupies the full width of the enclosing component.
   */
  inline fun cell(isVerticalFlow: Boolean = false, isFullWidth: Boolean = false, init: InnerCell.() -> Unit) {
    setCellMode(true, isVerticalFlow, isFullWidth)
    InnerCell(this).init()
    setCellMode(false, isVerticalFlow, isFullWidth)
  }

  @PublishedApi
  internal abstract fun createRow(label: String?): Row

  @PublishedApi
  internal abstract fun setCellMode(value: Boolean, isVerticalFlow: Boolean, fullWidth: Boolean)

  // backward compatibility
  @Deprecated(level = DeprecationLevel.HIDDEN, message = "deprecated")
  operator fun JComponent.invoke(vararg constraints: CCFlags, gapLeft: Int = 0, growPolicy: GrowPolicy? = null) {
    invoke(constraints = *constraints, gapLeft = gapLeft, growPolicy = growPolicy, comment = null)
  }

  @Deprecated(level = DeprecationLevel.ERROR,
              message = "Do not create standalone panel, if you want layout components in vertical flow mode, use cell(isVerticalFlow = true)")
  fun panel(vararg constraints: LCFlags, title: String? = null, init: LayoutBuilder.() -> Unit) {
  }
}

enum class GrowPolicy {
  SHORT_TEXT, MEDIUM_TEXT
}

fun Row.enableIf(predicate: ComponentPredicate) {
  enabled = predicate()
  predicate.addListener { enabled = it }
}
