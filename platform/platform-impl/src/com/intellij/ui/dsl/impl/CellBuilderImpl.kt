// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.impl

import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.CellBuilder
import com.intellij.ui.dsl.RightGap
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.layout.*
import com.intellij.util.SmartList
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Experimental
internal class CellBuilderImpl<T : JComponent>(
  private val dialogPanelConfig: DialogPanelConfig,
  component: T,
  val viewComponent: JComponent = component) : CellBuilderBaseImpl<CellBuilder<T>>(dialogPanelConfig), CellBuilder<T> {

  override var component: T = component
    private set

  private var property: GraphProperty<*>? = null
  private var applyIfEnabled = false

  /**
   * Not null if parent is hidden and the cell should not be visible. While parent is hidden
   * value contains visibility of the cell, which will be restored when parent becomes visible
   */
  private var parentHiddenComponentVisible: Boolean? = null

  override fun horizontalAlign(horizontalAlign: HorizontalAlign): CellBuilderImpl<T> {
    super.horizontalAlign(horizontalAlign)
    return this
  }

  override fun verticalAlign(verticalAlign: VerticalAlign): CellBuilderImpl<T> {
    super.verticalAlign(verticalAlign)
    return this
  }

  override fun resizableColumn(): CellBuilderImpl<T> {
    super.resizableColumn()
    return this
  }

  override fun comment(@NlsContexts.DetailedDescription comment: String, maxLineLength: Int): CellBuilderImpl<T> {
    super.comment(comment, maxLineLength)
    return this
  }

  override fun gap(rightGap: RightGap): CellBuilderImpl<T> {
    super.gap(rightGap)
    return this
  }

  override fun applyToComponent(task: T.() -> Unit): CellBuilderImpl<T> {
    component.task()
    return this
  }

  override fun enabled(isEnabled: Boolean): CellBuilderImpl<T> {
    viewComponent.isEnabled = isEnabled
    return this
  }

  fun visibleFromParent(isVisible: Boolean) {
    if (isVisible) {
      parentHiddenComponentVisible?.let {
        doVisible(it)
        parentHiddenComponentVisible = null
      }
    }
    else {
      if (parentHiddenComponentVisible == null) {
        parentHiddenComponentVisible = viewComponent.isVisible
        doVisible(false)
      }
    }
  }

  override fun visible(isVisible: Boolean): CellBuilderImpl<T> {
    if (parentHiddenComponentVisible == null) {
      doVisible(isVisible)
    } else {
      parentHiddenComponentVisible = isVisible
    }
    return this
  }

  override fun visibleIf(predicate: ComponentPredicate): CellBuilderImpl<T> {
    visible(predicate())
    predicate.addListener { visible(it) }
    return this
  }

  override fun applyIfEnabled(): CellBuilderImpl<T> {
    applyIfEnabled = true
    return this
  }

  override fun <V> bind(componentGet: (T) -> V, componentSet: (T, V) -> Unit, binding: PropertyBinding<V>): CellBuilderImpl<T> {
    onApply { if (shouldSaveOnApply()) binding.set(componentGet(component)) }
    onReset { componentSet(component, binding.get()) }
    onIsModified { shouldSaveOnApply() && componentGet(component) != binding.get() }
    return this
  }

  private fun shouldSaveOnApply(): Boolean {
    return !(applyIfEnabled && !viewComponent.isEnabled)
  }

  fun onValidationOnInput(callback: ValidationInfoBuilder.(T) -> ValidationInfo?): CellBuilderImpl<T> {
    val origin = component.origin
    dialogPanelConfig.componentValidateCallbacks[origin] = { callback(ValidationInfoBuilder(origin), component) }
    property?.let { dialogPanelConfig.customValidationRequestors.getOrPut(origin, { SmartList() }).add(it::afterPropagation) }
    return this
  }

  private fun onApply(callback: () -> Unit): CellBuilderImpl<T> {
    dialogPanelConfig.applyCallbacks.register(component, callback)
    return this
  }

  private fun onReset(callback: () -> Unit): CellBuilderImpl<T> {
    dialogPanelConfig.resetCallbacks.register(component, callback)
    return this
  }

  private fun onIsModified(callback: () -> Boolean): CellBuilderImpl<T> {
    dialogPanelConfig.isModifiedCallbacks.register(component, callback)
    return this
  }

  private fun doVisible(isVisible: Boolean) {
    viewComponent.isVisible = isVisible
    comment?.let { it.isVisible = isVisible }
  }
}

private val JComponent.origin: JComponent
  get() {
    return when (this) {
      is TextFieldWithBrowseButton -> textField
      else -> this
    }
  }