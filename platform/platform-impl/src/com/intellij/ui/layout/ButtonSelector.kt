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
import java.awt.Dimension
import java.awt.Insets
import java.util.function.Supplier
import kotlin.math.max

fun <T> Row.buttonSelector(options: Collection<T>, property: GraphProperty<T>, renderer: (T) -> String): ButtonSelectorToolbar {
  val actionGroup = DefaultActionGroup(options.map { ButtonSelectorAction(it, property, renderer(it)) })
  val toolbar = ButtonSelectorToolbar("ButtonSelector", actionGroup, true)
  toolbar.targetComponent = null // any data context is supported, suppress warning
  component(toolbar)
  return toolbar
}

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

private const val LEFT_RIGHT_PADDING: Int = 8
private const val TOP_BOTTOM_PADDING: Int = 2
private const val BUTTONS_MARGIN: Int = 0

private class ButtonSelector(
  action: ButtonSelectorAction<*>,
  presentation: Presentation,
  place: String?,
  minimumSize: Dimension,
  private val forceFieldHeight: Boolean
) : ActionButtonWithText(action, presentation, place, minimumSize) {
  init {
    isFocusable = true
  }

  override fun getInsets(): Insets = super.getInsets().apply {
    right += left + BUTTONS_MARGIN
    left = 0
  }

  override fun getPreferredSize(): Dimension {
    val old = super.getPreferredSize()
    val proposedHeight = old.height + TOP_BOTTOM_PADDING * 2
    return Dimension(old.width + LEFT_RIGHT_PADDING * 2, calcHeight(forceFieldHeight, proposedHeight))
  }
}

class ButtonSelectorToolbar @JvmOverloads constructor(
  place: String,
  actionGroup: ActionGroup,
  horizontal: Boolean,
  private val forceFieldHeight: Boolean = false
) : ActionToolbarImpl(place, actionGroup, horizontal, true) {

  init {
    setForceMinimumSize(true)
    ActionToolbarBorder.setOutlined(this, true)
  }

  override fun addNotify() {
    super.addNotify()

    // Create actions immediately, otherwise first ButtonSelectorToolbar preferred size calculation can be done without actions.
    // In such case ButtonSelectorToolbar will keep narrow width for preferred size because of ActionToolbar.WRAP_LAYOUT_POLICY
    updateActionsImmediately(true)
  }

  override fun getPreferredSize(): Dimension {
    val size = super.getPreferredSize()
    return Dimension(size.width, calcHeight(forceFieldHeight, size.height)) // there can be non-default font-size
  }

  override fun getMinimumSize(): Dimension {
    val size = super.getMinimumSize()
    return Dimension(size.width, calcHeight(forceFieldHeight, size.height)) // there can be non-default font-size
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
  ): ActionButton = ButtonSelector(action as ButtonSelectorAction<*>, presentation, place, minimumSize, forceFieldHeight)
}

private fun calcHeight(forceFieldHeight: Boolean, height: Int): Int {
  return if (forceFieldHeight) max(30, height) else height
}