// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION")

package com.intellij.ui.layout

import com.intellij.BundleBase
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsContexts.Button
import com.intellij.openapi.util.NlsContexts.DialogMessage
import org.jetbrains.annotations.ApiStatus
import java.awt.event.ActionEvent
import javax.swing.JButton
import javax.swing.JComponent

@DslMarker
@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2")
annotation class CellMarker

class ValidationInfoBuilder(val component: JComponent) {
  fun error(@DialogMessage message: String): ValidationInfo = ValidationInfo(message, component)
  fun warning(@DialogMessage message: String): ValidationInfo = ValidationInfo(message, component).asWarning().withOKEnabled()
}

@JvmDefaultWithCompatibility
@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2")
interface CellBuilder<out T : JComponent> {
  @get:Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.ERROR)
  @get:ApiStatus.ScheduledForRemoval
  val component: T

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun focused(): CellBuilder<T>

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun withValidationOnApply(callback: ValidationInfoBuilder.(T) -> ValidationInfo?): CellBuilder<T>

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun withValidationOnInput(callback: ValidationInfoBuilder.(T) -> ValidationInfo?): CellBuilder<T>

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.ERROR)
  fun growPolicy(growPolicy: GrowPolicy): CellBuilder<T>

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.ERROR)
  fun constraints(vararg constraints: CCFlags): CellBuilder<T>

}

// separate class to avoid row related methods in the `cell { } `
@CellMarker
@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2")
abstract class Cell : BaseBuilder {
  /**
   * Sets how keen the component should be to grow in relation to other component **in the same cell**. Use `push` in addition if need.
   * If this constraint is not set the grow weight is set to 0 and the component will not grow (unless some automatic rule is not applied.
   * Grow weight will only be compared against the weights for the same cell.
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  val growX: CCFlags = CCFlags.growX

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  val grow: CCFlags = CCFlags.grow

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  fun button(text: @Button String, actionListener: (event: ActionEvent) -> Unit): CellBuilder<JButton> {
    val button = JButton(BundleBase.replaceMnemonicAmpersand(text))
    button.addActionListener(actionListener)
    return component(button)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  abstract fun <T : JComponent> component(component: T): CellBuilder<T>

}

@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
class InnerCell(val cell: Cell) : Cell() {
  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  override fun <T : JComponent> component(component: T): CellBuilder<T> {
    return cell.component(component)
  }
}
