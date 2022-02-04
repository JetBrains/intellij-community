// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.gridLayout.*
import com.intellij.ui.layout.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.JComponent
import javax.swing.JLabel

@ApiStatus.Internal
internal const val DSL_INT_TEXT_RANGE_PROPERTY = "dsl.intText.range"

enum class LabelPosition {
  LEFT,

  TOP
}

interface Cell<out T : JComponent> : CellBase<Cell<T>> {

  override fun horizontalAlign(horizontalAlign: HorizontalAlign): Cell<T>

  override fun verticalAlign(verticalAlign: VerticalAlign): Cell<T>

  override fun resizableColumn(): Cell<T>

  override fun gap(rightGap: RightGap): Cell<T>

  override fun customize(customGaps: Gaps): Cell<T>

  /**
   * Component that occupies the cell
   */
  val component: T

  fun focused(): Cell<T>

  fun applyToComponent(task: T.() -> Unit): Cell<T>

  override fun enabled(isEnabled: Boolean): Cell<T>

  override fun enabledIf(predicate: ComponentPredicate): Cell<T>

  override fun visible(isVisible: Boolean): Cell<T>

  override fun visibleIf(predicate: ComponentPredicate): Cell<T>

  /**
   * Changes [component] font to bold
   */
  fun bold(): Cell<T>

  @Deprecated("Use overloaded comment(...) instead", level = DeprecationLevel.HIDDEN)
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  fun comment(@NlsContexts.DetailedDescription comment: String?,
              maxLineLength: Int = DEFAULT_COMMENT_WIDTH): Cell<T>

  /**
   * Adds comment under the cell aligned by left edge with appropriate color and font size (macOS and Linux use smaller font).
   * [comment] can contain HTML tags except &lt;html&gt;, which is added automatically.
   * \n does not work as new line in html, use &lt;br&gt; instead.
   * Links with href to http/https are automatically marked with additional arrow icon.
   * The comment occupies the available width before the next comment (if present) or
   * whole remaining width. Visibility and enabled state of the cell affects comment as well.
   *
   * For layout [RowLayout.LABEL_ALIGNED] comment after second columns is placed in second column (there are technical problems,
   * can be implemented later)
   *
   * @see MAX_LINE_LENGTH_WORD_WRAP
   * @see MAX_LINE_LENGTH_NO_WRAP
   */
  fun comment(@NlsContexts.DetailedDescription comment: String?,
              maxLineLength: Int = DEFAULT_COMMENT_WIDTH,
              action: HyperlinkEventAction = HyperlinkEventAction.HTML_HYPERLINK_INSTANCE): Cell<T>

  @Deprecated("Use comment(...) instead")
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  fun commentHtml(@NlsContexts.DetailedDescription comment: String?,
                  action: HyperlinkEventAction = HyperlinkEventAction.HTML_HYPERLINK_INSTANCE): Cell<T>

  /**
   * See doc for overloaded method
   */
  fun label(@NlsContexts.Label label: String, position: LabelPosition = LabelPosition.LEFT): Cell<T>

  /**
   * Adds label at specified [position]. [LabelPosition.TOP] labels occupy available width before the next top label (if present) or
   * whole remaining width. Visibility and enabled state of the cell affects the label as well.
   *
   * For layout [RowLayout.LABEL_ALIGNED] labels for two first columns are supported only (there are technical problems,
   * can be implemented later)
   */
  fun label(label: JLabel, position: LabelPosition = LabelPosition.LEFT): Cell<T>

  /**
   * All components from the same width group will have the same width equals to maximum width from the group.
   */
  fun widthGroup(group: String): Cell<T>

  /**
   * If this method is called, the value of the component will be stored to the backing property only if the component is enabled
   */
  fun applyIfEnabled(): Cell<T>

  fun accessibleName(@Nls name: String): Cell<T>

  fun accessibleDescription(@Nls description: String): Cell<T>

  /**
   * Binds component value that is provided by [componentGet] and [componentSet] methods to specified [binding] property.
   * The property is applied only when [DialogPanel.apply] is invoked. Methods [DialogPanel.isModified] and [DialogPanel.reset]
   * are also supported automatically for bound properties.
   * This method is rarely used directly, see [Cell] extension methods named like "bindXXX" for specific components
   */
  fun <V> bind(componentGet: (T) -> V, componentSet: (T, V) -> Unit, binding: PropertyBinding<V>): Cell<T>

  /**
   * Installs [property] as validation requestor.
   * @deprecated use [validationRequestor] instead
   */
  @Deprecated("Use validationRequestor instead", ReplaceWith("validationRequestor(property::afterPropagation)"))
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  fun graphProperty(property: GraphProperty<*>): Cell<T> =
    validationRequestor(property::afterPropagation)

  /**
   * Registers custom validation requestor for current [component].
   * @param validationRequestor gets callback (component validator) that should be subscribed on custom event.
   */
  fun validationRequestor(validationRequestor: (() -> Unit) -> Unit): Cell<T>

  /**
   * Adds [component] validation
   */
  fun validationOnApply(callback: ValidationInfoBuilder.(T) -> ValidationInfo?): Cell<T>

  /**
   * Shows error [message] if [condition] is true. Short version for particular case of [validationOnApply]
   */
  fun errorOnApply(@NlsContexts.DialogMessage message: String, condition: (T) -> Boolean): Cell<T>

  /**
   * Adds [component] validation
   */
  fun validationOnInput(callback: ValidationInfoBuilder.(T) -> ValidationInfo?): Cell<T>

  /**
   * Registers [callback] that will be called for [component] from [DialogPanel.apply] method
   */
  fun onApply(callback: () -> Unit): Cell<T>

  /**
   * Registers [callback] that will be called for [component] from [DialogPanel.reset] method
   */
  fun onReset(callback: () -> Unit): Cell<T>

  /**
   * Registers [callback] that will be called for [component] from [DialogPanel.isModified] method
   */
  fun onIsModified(callback: () -> Boolean): Cell<T>

}
