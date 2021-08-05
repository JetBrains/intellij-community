// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl

import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.layout.*
import com.intellij.util.SmartList
import org.jetbrains.annotations.ApiStatus
import javax.swing.AbstractButton
import javax.swing.JComponent
import kotlin.reflect.KMutableProperty0

@DslMarker
private annotation class CellBuilderMarker

@ApiStatus.Experimental
@CellBuilderMarker
open class CellBuilder<out T : JComponent>(
  private val builder: PanelBuilder,
  val component: T,
  val viewComponent: JComponent = component) {

  var horizontalAlign = HorizontalAlign.LEFT
    private set
  var verticalAlign = VerticalAlign.CENTER
    private set
  var comment: JComponent? = null
    private set
  var rightGap = 0
    private set
  private var applyIfEnabled = false

  fun applyToComponent(task: T.() -> Unit): CellBuilder<T> {
    component.task()
    return this
  }

  fun enabled(isEnabled: Boolean): CellBuilder<T> {
    viewComponent.isEnabled = isEnabled
    return this
  }

  /**
   * If this method is called, the value of the component will be stored to the backing property only if the component is enabled
   */
  fun applyIfEnabled(): CellBuilder<T> {
    applyIfEnabled = true
    return this
  }

  fun alignHorizontal(horizontalAlign: HorizontalAlign): CellBuilder<T> {
    this.horizontalAlign = horizontalAlign
    return this
  }

  fun alignVertical(verticalAlign: VerticalAlign): CellBuilder<T> {
    this.verticalAlign = verticalAlign
    return this
  }

  /**
   * Separates next cell in current row with [SpacingConfiguration.horizontalUnrelatedGap]. Should not be used for last cell in a row
   */
  fun rightUnrelatedGap(): CellBuilder<T> {
    rightGap = builder.spacing.horizontalUnrelatedGap
    return this
  }

  fun comment(@NlsContexts.DetailedDescription comment: String,
              maxLineLength: Int = ComponentPanelBuilder.MAX_COMMENT_WIDTH): CellBuilder<T> {
    this.comment = ComponentPanelBuilder.createCommentComponent(comment, true, maxLineLength, true)
    return this
  }

  private fun shouldSaveOnApply(): Boolean {
    return !(applyIfEnabled && !viewComponent.isEnabled)
  }

  private fun onApply(callback: () -> Unit): CellBuilder<T> {
    builder.applyCallbacks.getOrPut(component) { SmartList() }.add(callback)
    return this
  }

  private fun onReset(callback: () -> Unit): CellBuilder<T> {
    builder.resetCallbacks.getOrPut(component) { SmartList() }.add(callback)
    return this
  }

  private fun onIsModified(callback: () -> Boolean): CellBuilder<T> {
    builder.isModifiedCallbacks.getOrPut(component) { SmartList() }.add(callback)
    return this
  }

  fun <V> bind(
    componentGet: (T) -> V,
    componentSet: (T, V) -> Unit,
    modelBinding: PropertyBinding<V>
  ): CellBuilder<T> {
    onApply { if (shouldSaveOnApply()) modelBinding.set(componentGet(component)) }
    onReset { componentSet(component, modelBinding.get()) }
    onIsModified { shouldSaveOnApply() && componentGet(component) != modelBinding.get() }
    return this
  }
}

fun <T : AbstractButton> CellBuilder<T>.bind(modelBinding: PropertyBinding<Boolean>): CellBuilder<T> {
  return bind(AbstractButton::isSelected, AbstractButton::setSelected, modelBinding)
}

fun <T : AbstractButton> CellBuilder<T>.bind(prop: KMutableProperty0<Boolean>): CellBuilder<T> {
  return bind(prop.toBinding())
}

fun <T : AbstractButton> CellBuilder<T>.bind(getter: () -> Boolean, setter: (Boolean) -> Unit): CellBuilder<T> {
  return bind(PropertyBinding(getter, setter))
}
