// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.ui.*
import com.intellij.openapi.ui.validation.*
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.Label
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.layout.*
import com.intellij.util.SmartList
import com.intellij.util.containers.map2Array
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Font
import java.awt.ItemSelectable
import javax.swing.JComponent
import javax.swing.JEditorPane
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

  override var comment: JEditorPane? = null
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

  override fun horizontalAlign(horizontalAlign: HorizontalAlign): CellImpl<T> {
    super.horizontalAlign(horizontalAlign)
    return this
  }

  override fun verticalAlign(verticalAlign: VerticalAlign): CellImpl<T> {
    super.verticalAlign(verticalAlign)
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

  override fun bold(): CellImpl<T> {
    component.font = component.font.deriveFont(Font.BOLD)
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
    componentSet(component, prop.get())

    onApply { if (shouldSaveOnApply()) prop.set(componentGet(component)) }
    onReset { componentSet(component, prop.get()) }
    onIsModified { shouldSaveOnApply() && componentGet(component) != prop.get() }
    return this
  }

  @Deprecated("Use overloaded method")
  override fun <V> bind(componentGet: (T) -> V, componentSet: (T, V) -> Unit, binding: PropertyBinding<V>): CellImpl<T> {
    return bind(componentGet, componentSet, MutableProperty(binding.get, binding.set))
  }

  override fun validationRequestor(validationRequestor: (() -> Unit) -> Unit): CellImpl<T> {
    return validationRequestor(DialogValidationRequestor { _, it -> validationRequestor(it) })
  }

  override fun validationRequestor(validationRequestor: DialogValidationRequestor): CellImpl<T> {
    val origin = component.origin
    dialogPanelConfig.validationRequestors.getOrPut(origin) { SmartList() }
      .add(validationRequestor)
    return this
  }

  override fun validationRequestor(validationRequestor: DialogValidationRequestor.WithParameter<T>): CellImpl<T> {
    return validationRequestor(validationRequestor(component))
  }

  override fun validation(validation: ValidationInfoBuilder.(T) -> ValidationInfo?): CellImpl<T> {
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

  override fun validationOnInput(validation: ValidationInfoBuilder.(T) -> ValidationInfo?): CellImpl<T> {
    val origin = component.origin
    return validationOnInput(DialogValidation { ValidationInfoBuilder(origin).validation(component) })
  }

  override fun validationOnInput(vararg validations: DialogValidation): CellImpl<T> {
    val origin = component.origin
    dialogPanelConfig.validationsOnInput.getOrPut(origin) { SmartList() }
      .addAll(validations.map { it.forComponentIfNeeded(origin) })

    // Fallback in case if no validation requestors is defined
    guessAndInstallValidationRequestor()

    return this
  }

  override fun validationOnInput(vararg validations: DialogValidation.WithParameter<T>): CellImpl<T> {
    return validationOnInput(*validations.map2Array { it(component) })
  }

  override fun validationOnApply(validation: ValidationInfoBuilder.(T) -> ValidationInfo?): CellImpl<T> {
    val origin = component.origin
    return validationOnApply(DialogValidation { ValidationInfoBuilder(origin).validation(component) })
  }

  override fun validationOnApply(vararg validations: DialogValidation): CellImpl<T> {
    val origin = component.origin
    dialogPanelConfig.validationsOnApply.getOrPut(origin) { SmartList() }
      .addAll(validations.map { it.forComponentIfNeeded(origin) })

    // Fallback in case if no validation requestors is defined
    guessAndInstallValidationRequestor()

    return this
  }

  override fun validationOnApply(vararg validations: DialogValidation.WithParameter<T>): CellImpl<T> {
    return validationOnApply(*validations.map2Array { it(component) })
  }

  override fun errorOnApply(message: String, condition: (T) -> Boolean): CellImpl<T> {
    return validationOnApply { if (condition(it)) error(message) else null }
  }

  private fun guessAndInstallValidationRequestor() {
    val stackTrace = Throwable()
    val origin = component.origin
    val validationRequestors = dialogPanelConfig.validationRequestors.getOrPut(origin) { SmartList() }
    if (validationRequestors.isNotEmpty()) return

    validationRequestors.add(object : DialogValidationRequestor {
      override fun subscribe(parentDisposable: Disposable?, validate: () -> Unit) {
        if (validationRequestors.size > 1) return

        val property = property
        val requestor = when {
          property != null -> AFTER_PROPERTY_CHANGE(property)
          origin is JTextComponent -> WHEN_TEXT_CHANGED(origin)
          origin is ItemSelectable -> WHEN_STATE_CHANGED(origin)
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
    dialogPanelConfig.applyCallbacks.register(component, callback)
    return this
  }

  override fun onReset(callback: () -> Unit): CellImpl<T> {
    dialogPanelConfig.resetCallbacks.register(component, callback)
    return this
  }

  override fun onIsModified(callback: () -> Boolean): CellImpl<T> {
    dialogPanelConfig.isModifiedCallbacks.register(component, callback)
    return this
  }

  override fun customize(customGaps: Gaps): CellImpl<T> {
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

private const val HTML = "<html>"

@Deprecated("Not needed in the future")
@ApiStatus.ScheduledForRemoval
internal fun removeHtml(text: @Nls String): @Nls String {
  return if (text.startsWith(HTML, ignoreCase = true)) text.substring(HTML.length) else text
}
