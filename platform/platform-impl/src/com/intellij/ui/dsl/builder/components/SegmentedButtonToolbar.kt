// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.components

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.actionSystem.impl.ActionToolbarBorder
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.NlsActions
import com.intellij.ui.dsl.builder.SpacingConfiguration
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension

/**
 * todo
 * Implement focus
 * https://www.figma.com/file/IT8EVCtINhpc59ECCbBGdM/IntelliJ-Platform-UI-Kit?node-id=79165%3A16120
 */
@ApiStatus.Experimental
class SegmentedButtonToolbar(actionGroup: ActionGroup, horizontal: Boolean, private val spacingConfiguration: SpacingConfiguration) :
  ActionToolbarImpl("ButtonSelector", actionGroup, horizontal, true) {

  init {
    setForceMinimumSize(true)
    // Buttons preferred size is calculated in SegmentedButton.getPreferredSize, so reset default size
    setMinimumButtonSize(Dimension(0, 0))
    ActionToolbarBorder.setOutlined(this, true)
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
    minimumSize: Dimension): ActionButton {
    return SegmentedButton(action as SegmentedButtonAction<*>, presentation, place, minimumSize, spacingConfiguration)
  }
}

@ApiStatus.Experimental
internal class SegmentedButtonAction<T>(private val option: T,
                                        private val property: GraphProperty<T>,
                                        @NlsActions.ActionText optionText: String,
                                        @NlsActions.ActionDescription optionDescription: String? = null)
  : ToggleAction(optionText, optionDescription, null), DumbAware {

  override fun isSelected(e: AnActionEvent): Boolean {
    return property.get() == option
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (state) {
      property.set(option)
    }
  }
}

private class SegmentedButton(
  action: SegmentedButtonAction<*>,
  presentation: Presentation,
  place: String?,
  minimumSize: Dimension,
  private val spacingConfiguration: SpacingConfiguration
) : ActionButtonWithText(action, presentation, place, minimumSize) {
  init {
    isFocusable = true
  }

  override fun getPreferredSize(): Dimension {
    val preferredSize = super.getPreferredSize()
    return Dimension(preferredSize.width + spacingConfiguration.segmentedButtonHorizontalGap * 2,
      preferredSize.height + spacingConfiguration.segmentedButtonVerticalGap * 2)
  }
}
