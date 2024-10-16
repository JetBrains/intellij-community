// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.validation.DialogValidation
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.UiDslException
import com.intellij.ui.dsl.gridLayout.*
import com.intellij.ui.dsl.validation.CellValidation
import com.intellij.ui.layout.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel

enum class LabelPosition {
  LEFT,

  TOP
}

@ApiStatus.NonExtendable
@JvmDefaultWithCompatibility
interface Cell<out T : JComponent> : CellBase<Cell<T>> {

  @Deprecated("Use align(AlignX.LEFT/CENTER/RIGHT/FILL) method instead")
  @ApiStatus.ScheduledForRemoval
  override fun horizontalAlign(horizontalAlign: HorizontalAlign): Cell<T>

  @Deprecated("Use align(AlignY.TOP/CENTER/BOTTOM/FILL) method instead")
  @ApiStatus.ScheduledForRemoval
  override fun verticalAlign(verticalAlign: VerticalAlign): Cell<T>

  override fun align(align: Align): Cell<T>

  override fun resizableColumn(): Cell<T>

  override fun gap(rightGap: RightGap): Cell<T>

  @Deprecated("Use customize(UnscaledGaps) instead", level = DeprecationLevel.HIDDEN)
  @ApiStatus.ScheduledForRemoval
  override fun customize(customGaps: Gaps): Cell<T>

  override fun customize(customGaps: UnscaledGaps): Cell<T>

  /**
   * Component that occupies the cell.
   */
  val component: T

  /**
   * Comment assigned to the cell.
   */
  val comment: JEditorPane?

  fun focused(): Cell<T>

  fun applyToComponent(task: T.() -> Unit): Cell<T>

  override fun enabled(isEnabled: Boolean): Cell<T>

  override fun enabledIf(predicate: ComponentPredicate): Cell<T>

  override fun enabledIf(property: ObservableProperty<Boolean>): Cell<T>

  override fun visible(isVisible: Boolean): Cell<T>

  override fun visibleIf(predicate: ComponentPredicate): Cell<T>

  override fun visibleIf(property: ObservableProperty<Boolean>): Cell<T>

  /**
   * Changes [component] font to bold.
   */
  fun bold(): Cell<T>

  /**
   * Adds comment under the cell aligned by left edge with appropriate color and font size (macOS and Linux use smaller font).
   * * [comment] can contain HTML tags except `<html>`, which is added automatically
   * * `\n` does not work as new line in html, use `<br>` instead
   * * Links with href to http/https are automatically marked with additional arrow icon
   * * Use bundled icons with `<code>` tag, for example `<icon src='AllIcons.General.Information'>`
   *
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

  /**
   * Adds the label with optional mnemonic related to the cell component.
   * See also doc for overloaded method
   */
  fun label(@NlsContexts.Label label: String, position: LabelPosition = LabelPosition.LEFT): Cell<T>

  /**
   * Adds the label related to the cell component at specified [position].
   * [LabelPosition.TOP] labels occupy available width before the next top label (if present) or
   * whole remaining width. Visibility and enabled state of the cell affects the label as well.
   *
   * For layout [RowLayout.LABEL_ALIGNED] labels for two first columns are supported only (there are technical problems,
   * can be implemented later).
   */
  fun label(label: JLabel, position: LabelPosition = LabelPosition.LEFT): Cell<T>

  /**
   * All components from the same width group will have the same width equals to maximum width from the group.
   */
  fun widthGroup(group: String): Cell<T>

  /**
   * If this method is called, the value of the component will be stored to the backing property only if the component is enabled.
   */
  fun applyIfEnabled(): Cell<T>

  fun accessibleName(@Nls name: String): Cell<T>

  fun accessibleDescription(@Nls description: String): Cell<T>

  /**
   * Binds component value that is provided by [componentGet] and [componentSet] methods to specified [prop] property.
   * The property is applied only when [DialogPanel.apply] is invoked. Methods [DialogPanel.isModified] and [DialogPanel.reset]
   * are also supported automatically for bound properties.
   * This method is rarely used directly, see [Cell] extension methods named like "bindXXX" for specific components.
   */
  fun <V> bind(componentGet: (T) -> V, componentSet: (T, V) -> Unit, prop: MutableProperty<V>): Cell<T>

  /**
   * Registers custom validation requestor for current [component].
   * @param validationRequestor gets callback (component validator) that should be subscribed on custom event.
   */
  fun validationRequestor(validationRequestor: (() -> Unit) -> Unit): Cell<T>

  /**
   * Registers custom [validationRequestor] for current [component].
   * It allows showing validation waring/error on custom [component] event (e.g., on text change).
   */
  fun validationRequestor(validationRequestor: DialogValidationRequestor): Cell<T>

  /**
   * Registers custom [validationRequestor] for current [component].
   * It allows showing validation waring/error on custom [component] event (e.g., on text change).
   */
  fun validationRequestor(validationRequestor: DialogValidationRequestor.WithParameter<T>): Cell<T>

  /**
   * Registers custom component data [validation].
   * [validation] will be called on [validationRequestor] events and
   * when [DialogPanel.apply] event happens.
   *
   * Will be renamed into `validation` (currently [cellValidation]) in the future.
   */
  @ApiStatus.Experimental
  fun validationInfo(validation: ValidationInfoBuilder.(T) -> ValidationInfo?): Cell<T>

  /**
   * Registers custom component data [validations].
   * [validations] will be called on [validationRequestor] events and
   * when [DialogPanel.apply] event happens.
   */
  fun validation(vararg validations: DialogValidation): Cell<T>

  /**
   * Registers custom component data [validations].
   * [validations] will be called on [validationRequestor] events and
   * when [DialogPanel.apply] event happens.
   */
  @ApiStatus.Internal
  @ApiStatus.Experimental
  fun validation(vararg validations: DialogValidation.WithParameter<T>): Cell<T>

  /**
   * Adds cell validation.
   *
   * todo: will be renamed into `validation` after removing existing overloaded [validation] method
   */
  @ApiStatus.Internal
  @ApiStatus.Experimental
  fun cellValidation(init: CellValidation<T>.(T) -> Unit): Cell<T>

  /**
   * Registers custom component data [validation].
   * [validation] will be called on [validationRequestor] events.
   */
  fun validationOnInput(validation: ValidationInfoBuilder.(T) -> ValidationInfo?): Cell<T>

  /**
   * Registers custom component data [validations].
   * [validations] will be called on [validationRequestor] events.
   */
  fun validationOnInput(vararg validations: DialogValidation): Cell<T>

  /**
   * Registers custom component data [validations].
   * [validations] will be called on [validationRequestor] events.
   */
  fun validationOnInput(vararg validations: DialogValidation.WithParameter<T>): Cell<T>

  /**
   * Registers custom component data [validation].
   * [validation] will be called when [DialogPanel.apply] event happens.
   */
  fun validationOnApply(validation: ValidationInfoBuilder.(T) -> ValidationInfo?): Cell<T>

  /**
   * Registers custom component data [validations].
   * [validations] will be called when [DialogPanel.apply] event happens.
   */
  fun validationOnApply(vararg validations: DialogValidation): Cell<T>

  /**
   * Registers custom component data [validations].
   * [validations] will be called when [DialogPanel.apply] event happens.
   */
  fun validationOnApply(vararg validations: DialogValidation.WithParameter<T>): Cell<T>

  /**
   * Shows error [message] if [condition] is true. Short version for particular case of [validationOnApply].
   */
  fun errorOnApply(@NlsContexts.DialogMessage message: String, condition: (T) -> Boolean): Cell<T>

  /**
   * Shows error [message] if [condition] is true. Short version for particular case of [validationOnApply].
   */
  fun addValidationRule(@NlsContexts.DialogMessage message: String, condition: (T) -> Boolean): Cell<T>

  /**
   * Registers [callback] that will be called for [component] from [DialogPanel.apply] method.
   */
  fun onApply(callback: () -> Unit): Cell<T>

  /**
   * Registers [callback] that will be called for [component] from [DialogPanel.reset] method.
   */
  fun onReset(callback: () -> Unit): Cell<T>

  /**
   * Registers [callback] that will be called for [component] from [DialogPanel.isModified] method.
   */
  fun onIsModified(callback: () -> Boolean): Cell<T>

  /**
   * Adds [listener] to cell component data modification.
   * If the component is not supported yet, UiDslException is thrown.
   *
   * See below description of some non-trivial cases:
   * * Non-editable [JComboBox] sets selected item to the first element while initialization,
   * so for this event onChange is not called (because not installed yet)
   * * Editable [JComboBox] sets selected item after focus is lost, so there are no onChange events while typing
   */
  @Throws(UiDslException::class)
  fun onChangedContext(listener: (component: T, context: ChangeContext) -> Unit): Cell<T>

  /**
   * Simplified version of [onChangedContext] method, which doesn't provide context of notification.
   */
  @Throws(UiDslException::class)
  fun onChanged(listener: (component: T) -> Unit): Cell<T>
}
