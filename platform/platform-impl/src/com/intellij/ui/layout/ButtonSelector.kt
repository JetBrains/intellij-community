// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.actionSystem.impl.ActionToolbarBorder
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.NlsActions
import com.intellij.ui.dsl.builder.SegmentedButton
import com.intellij.ui.dsl.builder.impl.DialogPanelConfig
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import java.awt.Dimension
import java.util.function.Supplier

@ScheduledForRemoval(inVersion = "2022.2")
@Deprecated("Use Kotlin UI DSL Version 2")
fun <T> Row.buttonSelector(options: Collection<T>, property: GraphProperty<T>, renderer: (T) -> String): ButtonSelectorToolbar {
  val actionGroup = DefaultActionGroup(options.map { ButtonSelectorAction(it, property, renderer(it)) })
  val config = DialogPanelConfig()
  val toolbar = ButtonSelectorToolbar("ButtonSelector", actionGroup, true, config)
  toolbar.targetComponent = null // any data context is supported, suppress warning
  component(toolbar)
  return toolbar
}

/**
 * Creates segmented button or combobox if screen reader mode
 */
@Deprecated("Use Kotlin UI DSL Version 2")
fun <T> Row.segmentedButton(options: Collection<T>, property: GraphProperty<T>, renderer: (T) -> String): SegmentedButton<T> {
  lateinit var result: SegmentedButton<T>
  val panel = com.intellij.ui.dsl.builder.panel {
    row {
      result = segmentedButton(options, renderer)
        .customize(Gaps.EMPTY)
        .bind(property)
    }
  }
  panel.border = JBUI.Borders.empty(3, 3)
  component(panel)
  return result
}

@ScheduledForRemoval(inVersion = "2022.2")
@Deprecated("Use Kotlin UI DSL Version 2")
class ButtonSelectorAction<T> @JvmOverloads constructor(private val option: T,
                                                        private val property: GraphProperty<T>,
                                                        optionText: Supplier<@NlsActions.ActionText String>,
                                                        optionDescription: Supplier<@NlsActions.ActionText String>? = null)
  : ToggleAction(optionText, optionDescription ?: Supplier { null }, null), DumbAware {

  @JvmOverloads
  constructor(option: T,
              property: GraphProperty<T>,
              @NlsActions.ActionText optionText: String,
              @NlsActions.ActionDescription optionDescription: String? = null) :
    this(option, property, Supplier { optionText }, optionDescription?.let { Supplier { optionDescription } })

  override fun isSelected(e: AnActionEvent): Boolean {
    return property.get() == option
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (state) {
      property.set(option)
    }
  }
}

private class ButtonSelector(
  action: ButtonSelectorAction<*>,
  presentation: Presentation,
  place: String?,
  minimumSize: Dimension,
  private val config: DialogPanelConfig
) : ActionButtonWithText(action, presentation, place, minimumSize) {
  init {
    isFocusable = true
  }

  override fun getPreferredSize(): Dimension {
    val preferredSize = super.getPreferredSize()
    return Dimension(preferredSize.width + config.spacing.segmentedButtonHorizontalGap * 2,
                     preferredSize.height + config.spacing.segmentedButtonVerticalGap * 2)
  }
}

@ScheduledForRemoval(inVersion = "2022.2")
@Deprecated("Use Kotlin UI DSL Version 2")
class ButtonSelectorToolbar internal constructor(
  place: String,
  actionGroup: ActionGroup,
  horizontal: Boolean,
  private val config: DialogPanelConfig
) : ActionToolbarImpl(place, actionGroup, horizontal, true) {

  init {
    setForceMinimumSize(true)
    // Buttons preferred size is calculated in ButtonSelector.getPreferredSize, so reset default size
    setMinimumButtonSize(Dimension(0, 0))
    ActionToolbarBorder.setOutlined(this, true)
  }

  override fun addNotify() {
    super.addNotify()

    // Create actions immediately, otherwise first ButtonSelectorToolbar preferred size calculation can be done without actions.
    // In such case ButtonSelectorToolbar will keep narrow width for preferred size because of ActionToolbar.WRAP_LAYOUT_POLICY
    updateActionsImmediately(true)
  }

  init {
    layoutPolicy = ActionToolbar.WRAP_LAYOUT_POLICY
    isFocusable = false
  }

  override fun createToolbarButton(
    action: AnAction,
    look: ActionButtonLook?,
    place: String,
    presentation: Presentation,
    minimumSize: Dimension
  ): ActionButton = ButtonSelector(action as ButtonSelectorAction<*>, presentation, place, minimumSize, config)
}