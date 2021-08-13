// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.*

/**
 * Determines relation between row grid and parent's grid
 */
enum class RowLayout {
  /**
   * All cells of the row including label independent of parent grid.
   * That means the row has own grid
   */
  INDEPENDENT,

  /**
   * Label is aligned, other components independent of parent grid. If label is not provided
   * then first cell (sometimes can be [JCheckBox] for example) is considered as a label.
   * That means label is in parent grid, other components have own grid
   */
  LABEL_ALIGNED,

  /**
   * All components including label are in parent grid
   * That means label and other components are in parent grid
   */
  PARENT_GRID
}

enum class TopGap {
  /**
   * See [SpacingConfiguration.verticalGroupTopGap]
   */
  GROUP
}

@DslMarker
private annotation class RowBuilderMarker

@ApiStatus.Experimental
@RowBuilderMarker
interface Row {

  /**
   * Layout of the row.
   * Default value is [RowLayout.LABEL_ALIGNED] when label is provided for the row, [RowLayout.INDEPENDENT] otherwise
   */
  fun layout(rowLayout: RowLayout): Row

  fun comment(@NlsContexts.DetailedDescription comment: String,
              maxLineLength: Int = ComponentPanelBuilder.MAX_COMMENT_WIDTH): Row

  fun <T : JComponent> cell(component: T): Cell<T>

  /**
   * Sets visibility for all components inside row including comment [Row.comment].
   * See also [Cell.visible] description
   */
  fun visible(isVisible: Boolean): Row

  fun gap(topGap: TopGap): Row

  /**
   * Creates subpanel inside cell of the row
   */
  fun panel(init: Panel.() -> Unit): Panel

  fun checkBox(@NlsContexts.Checkbox text: String): Cell<JBCheckBox>

  fun button(@NlsContexts.Button text: String, actionListener: (event: ActionEvent) -> Unit): Cell<JButton>

  fun actionButton(action: AnAction, dimension: Dimension = ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE): Cell<ActionButton>

  fun label(@NlsContexts.Label text: String): Cell<JLabel>

  fun textField(columns: Int = 0): Cell<JBTextField>

  fun intTextField(columns: Int = 0, range: IntRange? = null, keyboardStep: Int? = null): Cell<JBTextField>

  fun <T> comboBox(model: ComboBoxModel<T>, renderer: ListCellRenderer<T?>? = null): Cell<ComboBox<T>>

}
