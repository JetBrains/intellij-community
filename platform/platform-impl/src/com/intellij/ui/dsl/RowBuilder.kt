// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel

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

@DslMarker
private annotation class RowBuilderMarker

@ApiStatus.Experimental
@RowBuilderMarker
interface RowBuilder {

  /**
   * Layout of the row.
   * Default value is [RowLayout.LABEL_ALIGNED] when label is provided for the row, [RowLayout.INDEPENDENT] otherwise
   */
  fun layout(rowLayout: RowLayout): RowBuilder

  fun comment(@NlsContexts.DetailedDescription comment: String,
              maxLineLength: Int = ComponentPanelBuilder.MAX_COMMENT_WIDTH): RowBuilder

  fun <T : JComponent> cell(component: T): CellBuilder<T>

  /**
   * Creates subpanel inside cell of the row
   */
  fun panel(init: PanelBuilder.() -> Unit): PanelBuilder

  fun checkBox(@NlsContexts.Checkbox text: String): CellBuilder<JBCheckBox>

  fun button(@NlsContexts.Button text: String, actionListener: (event: ActionEvent) -> Unit): CellBuilder<JButton>

  fun actionButton(action: AnAction, dimension: Dimension = ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE): CellBuilder<ActionButton>

  fun label(@NlsContexts.Label text: String): CellBuilder<JLabel>

  fun textField(columns: Int = 0): CellBuilder<JBTextField>

  fun intTextField(columns: Int = 0, range: IntRange? = null, keyboardStep: Int? = null): CellBuilder<JBTextField>

}
