// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.impl

import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.Cell
import com.intellij.ui.dsl.RightGap
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.layout.*
import com.intellij.util.SmartList
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Experimental
internal class CellImpl<T : JComponent>(
  private val dialogPanelConfig: DialogPanelConfig,
  component: T,
  val viewComponent: JComponent = component) : CellBaseImpl<Cell<T>>(dialogPanelConfig), Cell<T> {

  override var component: T = component
    private set

  private var property: GraphProperty<*>? = null
  private var applyIfEnabled = false

  /**
   * Not null if parent is hidden and the cell should not be visible. While parent is hidden
   * value contains visibility of the cell, which will be restored when parent becomes visible
   */
  private var parentManagedComponentVisible: Boolean? = null

  /**
   * Not null if parent is disabled and the cell should not be enabled. While parent is disabled
   * value contains enable state of the cell, which will be restored when parent becomes enabled
   */
  private var parentManagedComponentEnabled: Boolean? = null

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

  override fun comment(@NlsContexts.DetailedDescription comment: String, maxLineLength: Int): CellImpl<T> {
    super.comment(comment, maxLineLength)
    return this
  }

  override fun gap(rightGap: RightGap): CellImpl<T> {
    super.gap(rightGap)
    return this
  }

  override fun applyToComponent(task: T.() -> Unit): CellImpl<T> {
    component.task()
    return this
  }

  fun enabledFromParent(isEnabled: Boolean) {
    if (isEnabled) {
      parentManagedComponentEnabled?.let {
        doEnabled(it)
        parentManagedComponentEnabled = null
      }
    }
    else {
      if (parentManagedComponentEnabled == null) {
        parentManagedComponentEnabled = viewComponent.isEnabled
        doEnabled(false)
      }
    }
  }

  override fun enabled(isEnabled: Boolean): CellImpl<T> {
    if (parentManagedComponentEnabled == null) {
      doEnabled(isEnabled)
    }
    else {
      parentManagedComponentEnabled = isEnabled
    }
    return this
  }

  override fun enableIf(predicate: ComponentPredicate): Cell<T> {
    viewComponent.isEnabled = predicate()
    predicate.addListener { viewComponent.isEnabled = it }
    return this
  }

  fun visibleFromParent(isVisible: Boolean) {
    if (isVisible) {
      parentManagedComponentVisible?.let {
        doVisible(it)
        parentManagedComponentVisible = null
      }
    }
    else {
      if (parentManagedComponentVisible == null) {
        parentManagedComponentVisible = viewComponent.isVisible
        doVisible(false)
      }
    }
  }

  override fun visible(isVisible: Boolean): CellImpl<T> {
    if (parentManagedComponentVisible == null) {
      doVisible(isVisible)
    }
    else {
      parentManagedComponentVisible = isVisible
    }
    return this
  }

  override fun visibleIf(predicate: ComponentPredicate): CellImpl<T> {
    visible(predicate())
    predicate.addListener { visible(it) }
    return this
  }

  override fun applyIfEnabled(): CellImpl<T> {
    applyIfEnabled = true
    return this
  }

  override fun <V> bind(componentGet: (T) -> V, componentSet: (T, V) -> Unit, binding: PropertyBinding<V>): CellImpl<T> {
    onApply { if (shouldSaveOnApply()) binding.set(componentGet(component)) }
    onReset { componentSet(component, binding.get()) }
    onIsModified { shouldSaveOnApply() && componentGet(component) != binding.get() }
    return this
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

  private fun shouldSaveOnApply(): Boolean {
    return !(applyIfEnabled && !viewComponent.isEnabled)
  }

  fun onValidationOnInput(callback: ValidationInfoBuilder.(T) -> ValidationInfo?): CellImpl<T> {
    val origin = component.origin
    dialogPanelConfig.componentValidateCallbacks[origin] = { callback(ValidationInfoBuilder(origin), component) }
    property?.let { dialogPanelConfig.customValidationRequestors.getOrPut(origin, { SmartList() }).add(it::afterPropagation) }
    return this
  }

  private fun doVisible(isVisible: Boolean) {
    viewComponent.isVisible = isVisible
    comment?.let { it.isVisible = isVisible }
  }

  private fun doEnabled(isEnabled: Boolean) {
    viewComponent.isEnabled = isEnabled
    comment?.let { it.isEnabled = isEnabled }
  }
}

private val JComponent.origin: JComponent
  get() {
    return when (this) {
      is TextFieldWithBrowseButton -> textField
      else -> this
    }
  }