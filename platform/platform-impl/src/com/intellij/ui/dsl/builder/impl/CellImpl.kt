// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.ui.*
import com.intellij.openapi.ui.validation.*
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.Label
import com.intellij.ui.dsl.UiDslException
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.components.DslLabel
import com.intellij.ui.dsl.gridLayout.*
import com.intellij.ui.dsl.validation.CellValidation
import com.intellij.ui.layout.*
import com.intellij.util.containers.map2Array
import com.intellij.util.ui.JBFont
import org.jetbrains.annotations.ApiStatus
import java.awt.Font
import java.awt.ItemSelectable
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.text.JTextComponent

@ApiStatus.Internal
internal class CellImpl<T : JComponent>(
  private val dialogPanelConfig: DialogPanelConfig,
  component: T,
  private val parent: RowImpl,
  val viewComponent: JComponent = component) : CellBaseImpl<Cell<T>>(), Cell<T> {

  override var component: T = component
    private set

  override var comment: DslLabel? = null
    private set

  var label: JLabel? = null
    private set

  var labelPosition: LabelPosition = LabelPosition.LEFT
    private set

  var widthGroup: String? = null
    private set

  private var property: ObservableProperty<*>? = null
  private var applyIfEnabled = false

  private var visible = viewComponent.isVisible
  private var enabled = viewComponent.isEnabled

  private val cellValidation = CellValidationImpl(dialogPanelConfig, component, component.interactiveComponent)

  val onChangeManager = OnChangeManager(component)

  @Deprecated("Use align method instead", level = DeprecationLevel.HIDDEN)
  @ApiStatus.ScheduledForRemoval
  override fun horizontalAlign(horizontalAlign: HorizontalAlign): CellImpl<T> {
    super.horizontalAlign(horizontalAlign)
    return this
  }

  @Deprecated("Use align method instead", level = DeprecationLevel.HIDDEN)
  @ApiStatus.ScheduledForRemoval
  override fun verticalAlign(verticalAlign: VerticalAlign): CellImpl<T> {
    super.verticalAlign(verticalAlign)
    return this
  }

  override fun align(align: Align): CellImpl<T> {
    super.align(align)

    (component as? DslLabel)?.let {
      if (it.maxLineLength == MAX_LINE_LENGTH_WORD_WRAP) {
        it.limitPreferredSize = horizontalAlign == HorizontalAlign.FILL
      }
    }

    return this
  }

  override fun resizableColumn(): CellImpl<T> {
    super.resizableColumn()
    return this
  }

  override fun gap(rightGap: RightGap): CellImpl<T> {
    super.gap(rightGap)
    return this
  }

  override fun focused(): CellImpl<T> {
    dialogPanelConfig.preferredFocusedComponent = component
    return this
  }

  override fun applyToComponent(task: T.() -> Unit): CellImpl<T> {
    component.task()
    return this
  }

  override fun enabledFromParent(parentEnabled: Boolean) {
    doEnabled(parentEnabled && enabled)
  }

  override fun enabled(isEnabled: Boolean): CellImpl<T> {
    enabled = isEnabled
    if (parent.isEnabled()) {
      doEnabled(enabled)
    }
    return this
  }

  override fun enabledIf(predicate: ComponentPredicate): Cell<T> {
    super.enabledIf(predicate)
    return this
  }

  override fun enabledIf(property: ObservableProperty<Boolean>): Cell<T> {
    super.enabledIf(property)
    return this
  }

  override fun visibleFromParent(parentVisible: Boolean) {
    doVisible(parentVisible && visible)
  }

  override fun visible(isVisible: Boolean): CellImpl<T> {
    visible = isVisible
    if (parent.isVisible()) {
      doVisible(visible)
    }
    return this
  }

  override fun visibleIf(predicate: ComponentPredicate): CellImpl<T> {
    super.visibleIf(predicate)
    return this
  }

  override fun visibleIf(property: ObservableProperty<Boolean>): Cell<T> {
    super.visibleIf(property)
    return this
  }

  override fun bold(): CellImpl<T> {
    component.font = JBFont.create(component.font.deriveFont(Font.BOLD), false)
    return this
  }

  override fun comment(@NlsContexts.DetailedDescription comment: String?, maxLineLength: Int, action: HyperlinkEventAction): CellImpl<T> {
    this.comment = if (comment == null) null else createComment(comment, maxLineLength, action)
    return this
  }

  override fun label(label: String, position: LabelPosition): CellImpl<T> {
    return label(Label(label), position)
  }

  override fun label(label: JLabel, position: LabelPosition): CellImpl<T> {
    this.label = label
    labelPosition = position
    label.putClientProperty(DslComponentPropertyInternal.CELL_LABEL, true)
    return this
  }

  override fun widthGroup(group: String): CellImpl<T> {
    widthGroup = group
    return this
  }

  override fun applyIfEnabled(): CellImpl<T> {
    applyIfEnabled = true
    return this
  }

  override fun accessibleName(name: String): CellImpl<T> {
    component.accessibleContext.accessibleName = name
    return this
  }

  override fun accessibleDescription(description: String): CellImpl<T> {
    component.accessibleContext.accessibleDescription = description
    return this
  }

  override fun <V> bind(componentGet: (T) -> V, componentSet: (T, V) -> Unit, prop: MutableProperty<V>): CellImpl<T> {
    onChangeManager.applyBinding {
      componentSet(component, prop.get())
    }

    onApply {
      if (shouldSaveOnApply()) prop.set(componentGet(component))
    }
    onReset {
      onChangeManager.applyBinding {
        componentSet(component, prop.get())
      }
    }
    onIsModified {
      shouldSaveOnApply() && componentGet(component) != prop.get()
    }
    return this
  }

  @Suppress("OVERRIDE_DEPRECATION")
  override fun <V> bind(componentGet: (T) -> V, componentSet: (T, V) -> Unit, binding: PropertyBinding<V>): CellImpl<T> {
    return bind(componentGet, componentSet, MutableProperty(binding.get, binding.set))
  }

  override fun validationRequestor(validationRequestor: (() -> Unit) -> Unit): CellImpl<T> {
    return validationRequestor(DialogValidationRequestor { _, it -> validationRequestor(it) })
  }

  override fun validationRequestor(validationRequestor: DialogValidationRequestor): CellImpl<T> {
    val interactiveComponent = component.interactiveComponent
    dialogPanelConfig.validationRequestors.list(interactiveComponent).add(validationRequestor)
    return this
  }

  override fun validationRequestor(validationRequestor: DialogValidationRequestor.WithParameter<T>): CellImpl<T> {
    return validationRequestor(validationRequestor(component))
  }

  @Deprecated("Use identical validationInfo method, validation method is reserved for new API")
  @ApiStatus.ScheduledForRemoval
  override fun validation(validation: ValidationInfoBuilder.(T) -> ValidationInfo?): CellImpl<T> {
    return validationInfo(validation)
  }

  override fun validationInfo(validation: ValidationInfoBuilder.(T) -> ValidationInfo?): CellImpl<T> {
    validationOnInput(validation)
    validationOnApply(validation)
    return this
  }

  override fun validation(vararg validations: DialogValidation): CellImpl<T> {
    validationOnInput(*validations)
    validationOnApply(*validations)
    return this
  }

  override fun validation(vararg validations: DialogValidation.WithParameter<T>): CellImpl<T> {
    validationOnInput(*validations)
    validationOnApply(*validations)
    return this
  }

  override fun cellValidation(init: CellValidation<T>.(T) -> Unit): CellImpl<T> {
    cellValidation.init(component)
    return this
  }

  override fun validationOnInput(validation: ValidationInfoBuilder.(T) -> ValidationInfo?): CellImpl<T> {
    val interactiveComponent = component.interactiveComponent
    return validationOnInput(DialogValidation { ValidationInfoBuilder(interactiveComponent).validation(component) })
  }

  override fun validationOnInput(vararg validations: DialogValidation): CellImpl<T> {
    val interactiveComponent = component.interactiveComponent
    dialogPanelConfig.validationsOnInput.list(interactiveComponent)
      .addAll(validations.map { it.forComponentIfNeeded(interactiveComponent) })

    // Fallback in case if no validation requestors is defined
    guessAndInstallValidationRequestor()

    return this
  }

  override fun validationOnInput(vararg validations: DialogValidation.WithParameter<T>): CellImpl<T> {
    return validationOnInput(*validations.map2Array { it(component) })
  }

  override fun validationOnApply(validation: ValidationInfoBuilder.(T) -> ValidationInfo?): CellImpl<T> {
    val origin = component.interactiveComponent
    return validationOnApply(DialogValidation { ValidationInfoBuilder(origin).validation(component) })
  }

  override fun validationOnApply(vararg validations: DialogValidation): CellImpl<T> {
    val interactiveComponent = component.interactiveComponent
    dialogPanelConfig.validationsOnApply.list(interactiveComponent)
      .addAll(validations.map { it.forComponentIfNeeded(interactiveComponent) })
    return this
  }

  override fun validationOnApply(vararg validations: DialogValidation.WithParameter<T>): CellImpl<T> {
    return validationOnApply(*validations.map2Array { it(component) })
  }

  override fun addValidationRule(message: String, condition: (T) -> Boolean): Cell<T> {
    return validationOnApply { if (condition(it)) error(message) else null }
  }

  override fun errorOnApply(message: String, condition: (T) -> Boolean) = addValidationRule(message, condition)

  private fun guessAndInstallValidationRequestor() {
    val stackTrace = Throwable()
    val interactiveComponent = component.interactiveComponent
    val validationRequestors = dialogPanelConfig.validationRequestors.list(interactiveComponent)
    if (validationRequestors.isNotEmpty()) return

    validationRequestors.add(object : DialogValidationRequestor {
      override fun subscribe(parentDisposable: Disposable?, validate: () -> Unit) {
        if (validationRequestors.size > 1) return

        val property = property
        val requestor = when {
          property != null -> WHEN_PROPERTY_CHANGED(property)
          interactiveComponent is JTextComponent -> WHEN_TEXT_CHANGED(interactiveComponent)
          interactiveComponent is ItemSelectable -> WHEN_STATE_CHANGED(interactiveComponent)
          interactiveComponent is EditorTextField -> WHEN_TEXT_FIELD_TEXT_CHANGED(interactiveComponent)
          else -> null
        }
        if (requestor != null) {
          requestor.subscribe(parentDisposable, validate)
        }
        else {
          logger<Cell<*>>().warn("Please, install Cell.validationRequestor", stackTrace)
        }
      }
    })
  }

  override fun onApply(callback: () -> Unit): CellImpl<T> {
    dialogPanelConfig.applyCallbacks.list(component).add(callback)
    return this
  }

  override fun onReset(callback: () -> Unit): CellImpl<T> {
    dialogPanelConfig.resetCallbacks.list(component).add(callback)
    return this
  }

  override fun onIsModified(callback: () -> Boolean): CellImpl<T> {
    dialogPanelConfig.isModifiedCallbacks.list(component).add(callback)
    return this
  }

  @Throws(UiDslException::class)
  override fun onChangedContext(listener: (component: T, context: ChangeContext) -> Unit): CellImpl<T> {
    onChangeManager.register(listener)
    return this
  }

  @Throws(UiDslException::class)
  override fun onChanged(listener: (component: T) -> Unit): Cell<T> {
    onChangeManager.register { component, _ -> listener(component) }
    return this
  }

  @Deprecated("Use customize(UnscaledGaps) instead")
  override fun customize(customGaps: Gaps): CellImpl<T> {
    return customize(customGaps.toUnscaled())
  }

  override fun customize(customGaps: UnscaledGaps): CellImpl<T> {
    super.customize(customGaps)
    return this
  }

  private fun shouldSaveOnApply(): Boolean {
    return !(applyIfEnabled && !viewComponent.isEnabled)
  }

  private fun doVisible(isVisible: Boolean) {
    if (viewComponent.isVisible != isVisible) {
      viewComponent.isVisible = isVisible
      comment?.let { it.isVisible = isVisible }
      label?.let { it.isVisible = isVisible }

      // Force parent to re-layout
      viewComponent.parent?.revalidate()
    }
  }

  private fun doEnabled(isEnabled: Boolean) {
    viewComponent.isEnabled = isEnabled
    comment?.let { it.isEnabled = isEnabled }
    label?.let { it.isEnabled = isEnabled }
  }

  companion object {
    internal fun Cell<*>.installValidationRequestor(property: ObservableProperty<*>) {
      if (this is CellImpl) {
        this.property = property
      }
    }

    private fun DialogValidation.forComponentIfNeeded(component: JComponent) =
      transformResult { if (this.component == null) forComponent(component) else this }
  }
}
