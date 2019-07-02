// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.ui.components.Label
import com.intellij.ui.components.noteComponent
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JLabel
import kotlin.reflect.KMutableProperty0

interface RowBuilder {
  val buttonGroup: ButtonGroup?

  fun createChildRow(label: JLabel? = null, buttonGroup: ButtonGroup? = null,
                     isSeparated: Boolean = false,
                     noGrid: Boolean = false,
                     title: String? = null): Row

  fun createNoteOrCommentRow(component: JComponent): Row

  fun row(label: JLabel? = null, separated: Boolean = false, init: Row.() -> Unit): Row {
    return createChildRow(label = label, isSeparated = separated, buttonGroup = buttonGroup).apply(init)
  }

  fun row(label: String?, separated: Boolean = false, init: Row.() -> Unit): Row {
    return createChildRow(label?.let { Label(it) }, isSeparated = separated, buttonGroup = buttonGroup).apply(init)
  }

  fun titledRow(title: String, init: Row.() -> Unit): Row {
    return createChildRow(isSeparated = true, title = title).apply(init)
  }

  /**
   * Hyperlinks are supported (`<a href=""></a>`), new lines and <br> are supported only if no links (file issue if need).
   */
  fun noteRow(text: String, linkHandler: ((url: String) -> Unit)? = null) {
    createNoteOrCommentRow(noteComponent(text, linkHandler))
  }

  fun commentRow(text: String) {
    createNoteOrCommentRow(ComponentPanelBuilder.createCommentComponent(text, true))
  }

  fun buttonGroup(init: Row.() -> Unit): Row {
    return createChildRow(buttonGroup = ButtonGroup()).apply(init)
  }
}

inline fun <reified T : Any> RowBuilder.buttonGroup(prop: KMutableProperty0<T>, init: RowBuilderWithButtonGroupProperty<T>.() -> Unit) {
  RowBuilderWithButtonGroupProperty(this, prop.toBinding()).init()
}

inline fun <reified T : Any> RowBuilder.buttonGroup(noinline getter: () -> T, noinline setter: (T) -> Unit, init: RowBuilderWithButtonGroupProperty<T>.() -> Unit) {
  RowBuilderWithButtonGroupProperty(this, PropertyBinding(getter, setter)).init()
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
   */
  inline fun cell(isVerticalFlow: Boolean = false, init: Cell.() -> Unit) {
    setCellMode(true, isVerticalFlow)
    init()
    setCellMode(false, isVerticalFlow)
  }

  @PublishedApi
  internal abstract fun createRow(label: String?, buttonGroup: ButtonGroup?): Row

  @PublishedApi
  internal abstract fun setCellMode(value: Boolean, isVerticalFlow: Boolean)

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
