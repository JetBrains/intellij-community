// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl

import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.layout.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

const val COLUMNS_MEDIUM = 25

internal const val DSL_INT_TEXT_RANGE_PROPERTY = "dsl.intText.range"

@ApiStatus.Experimental
interface Cell<out T : JComponent> : CellBase<Cell<T>> {

  override fun horizontalAlign(horizontalAlign: HorizontalAlign): Cell<T>

  override fun verticalAlign(verticalAlign: VerticalAlign): Cell<T>

  override fun resizableColumn(): Cell<T>

  override fun gap(rightGap: RightGap): Cell<T>

  val component: T

  fun applyToComponent(task: T.() -> Unit): Cell<T>

  override fun enabled(isEnabled: Boolean): Cell<T>

  fun enabledIf(predicate: ComponentPredicate): Cell<T>

  override fun visible(isVisible: Boolean): Cell<T>

  fun visibleIf(predicate: ComponentPredicate): Cell<T>

  /**
   * Adds comment under the cell. Visibility and enabled state of the cell affects comment as well.
   * Only one comment for a row is supported now
   */
  fun comment(@NlsContexts.DetailedDescription comment: String?,
              maxLineLength: Int = ComponentPanelBuilder.MAX_COMMENT_WIDTH): Cell<T>

  /**
   * Adds label before the cell. Visibility and enabled state of the cell affects label as well
   */
  fun label(@NlsContexts.Label label: String): Cell<T>

  /**
   * If this method is called, the value of the component will be stored to the backing property only if the component is enabled
   */
  fun applyIfEnabled(): Cell<T>

  fun accessibleName(@Nls name: String): Cell<T>

  fun accessibleDescription(@Nls description: String): Cell<T>

  fun <V> bind(componentGet: (T) -> V, componentSet: (T, V) -> Unit, binding: PropertyBinding<V>): Cell<T>

  fun graphProperty(property: GraphProperty<*>): Cell<T>

  fun onApply(callback: () -> Unit): Cell<T>

  fun onReset(callback: () -> Unit): Cell<T>

  fun onIsModified(callback: () -> Boolean): Cell<T>
}
