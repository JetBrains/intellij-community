// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.gridLayout.*
import com.intellij.ui.layout.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.JComponent
import javax.swing.JLabel

internal const val DSL_INT_TEXT_RANGE_PROPERTY = "dsl.intText.range"

enum class LabelPosition {
  LEFT,

  TOP
}

@ApiStatus.Experimental
interface Cell<out T : JComponent> : CellBase<Cell<T>> {

  /**
   * @see [Constraints.horizontalAlign]
   */
  override fun horizontalAlign(horizontalAlign: HorizontalAlign): Cell<T>

  /**
   * @see [Constraints.verticalAlign]
   */
  override fun verticalAlign(verticalAlign: VerticalAlign): Cell<T>

  /**
   * Marks column with the cell as a resizable one. Size and placement of component in columns are managed by [horizontalAlign]
   *
   * @see [Grid.resizableColumns]
   */
  override fun resizableColumn(): Cell<T>

  override fun gap(rightGap: RightGap): Cell<T>

  val component: T

  fun focused(): Cell<T>

  fun applyToComponent(task: T.() -> Unit): Cell<T>

  override fun enabled(isEnabled: Boolean): Cell<T>

  fun enabledIf(predicate: ComponentPredicate): Cell<T>

  override fun visible(isVisible: Boolean): Cell<T>

  fun visibleIf(predicate: ComponentPredicate): Cell<T>

  /**
   * Changes [component] font to bold
   */
  fun bold(): Cell<T>

  /**
   * Adds comment under the cell aligned by left edge. The comment occupies available width before next comment (if present) or
   * whole remaining width. Visibility and enabled state of the cell affects comment as well.
   * [comment] can contain html tags except <html>, which is added automatically in this method
   *
   * For layout [RowLayout.LABEL_ALIGNED] comment after second columns is placed in second column (there are technical problems,
   * can be implemented later)
   */
  fun comment(@NlsContexts.DetailedDescription comment: String?,
              maxLineLength: Int = ComponentPanelBuilder.MAX_COMMENT_WIDTH,
              action: HyperlinkEventAction = HyperlinkEventAction.HTML_HYPERLINK_INSTANCE): Cell<T>

  /**
   * See doc for overloaded method
   */
  fun label(@NlsContexts.Label label: String, position: LabelPosition = LabelPosition.LEFT): Cell<T>

  /**
   * Adds label at specified [position]. [LabelPosition.TOP] labels occupy available width before next top label (if present) or
   * whole remaining width. Visibility and enabled state of the cell affects label as well.
   *
   * For layout [RowLayout.LABEL_ALIGNED] labels for two first columns are supported only (there are technical problems,
   * can be implemented later)
   */
  fun label(label: JLabel, position: LabelPosition = LabelPosition.LEFT): Cell<T>

  /**
   * If this method is called, the value of the component will be stored to the backing property only if the component is enabled
   */
  fun applyIfEnabled(): Cell<T>

  fun accessibleName(@Nls name: String): Cell<T>

  fun accessibleDescription(@Nls description: String): Cell<T>

  fun <V> bind(componentGet: (T) -> V, componentSet: (T, V) -> Unit, binding: PropertyBinding<V>): Cell<T>

  fun graphProperty(property: GraphProperty<*>): Cell<T>

  fun validationOnApply(callback: ValidationInfoBuilder.(T) -> ValidationInfo?): Cell<T>

  /**
   * Shows [message] if [condition] is true
   */
  fun errorOnApply(@NlsContexts.DialogMessage message: String, condition: (T) -> Boolean): Cell<T>

  fun validationOnInput(callback: ValidationInfoBuilder.(T) -> ValidationInfo?): Cell<T>

  fun onApply(callback: () -> Unit): Cell<T>

  fun onReset(callback: () -> Unit): Cell<T>

  fun onIsModified(callback: () -> Boolean): Cell<T>

  /**
   * Overrides all gaps around cell by [customGaps]. Should be used for very specific cases
   */
  fun customize(customGaps: Gaps): Cell<T>

}
