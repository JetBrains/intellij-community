// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.ide.TooltipTitle
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.*
import com.intellij.ui.dsl.gridLayout.VerticalGaps
import com.intellij.ui.layout.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
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
   * No gap
   */
  NONE,

  /**
   * See [SpacingConfiguration.verticalSmallGap]
   */
  SMALL,

  /**
   * See [SpacingConfiguration.verticalMediumGap]
   */
  MEDIUM
}

enum class BottomGap {
  /**
   * No gap
   */
  NONE,

  /**
   * See [SpacingConfiguration.verticalSmallGap]
   */
  SMALL,

  /**
   * See [SpacingConfiguration.verticalMediumGap]
   */
  MEDIUM
}

@ApiStatus.Experimental
@LayoutDslMarker
interface Row {

  /**
   * Layout of the row.
   * Default value is [RowLayout.LABEL_ALIGNED] when label is provided for the row, [RowLayout.INDEPENDENT] otherwise
   */
  fun layout(rowLayout: RowLayout): Row

  /**
   * The row becomes resizable and occupies all free space. For several resizable rows extra free space is divided between rows equally
   */
  fun resizableRow(): Row

  /**
   * Adds comment after the row. Visibility and enabled state of the row affects row comment as well
   */
  fun rowComment(@NlsContexts.DetailedDescription comment: String,
                 maxLineLength: Int = ComponentPanelBuilder.MAX_COMMENT_WIDTH): Row

  fun <T : JComponent> cell(component: T, viewComponent: JComponent = component): Cell<T>

  /**
   * Adds an empty cell in the grid
   */
  fun cell()

  fun placeholder(): Placeholder

  /**
   * Sets visibility of the row including comment [Row.comment] and all children recursively.
   * The row is invisible while there is an invisible parent
   */
  fun visible(isVisible: Boolean): Row

  fun visibleIf(predicate: ComponentPredicate): Row

  /**
   * Sets enabled state of the row including comment [Row.comment] and all children recursively.
   * The row is disabled while there is a disabled parent
   */
  fun enabled(isEnabled: Boolean): Row

  fun enabledIf(predicate: ComponentPredicate): Row

  /**
   * Adds gap before current row. It is visible together with the row.
   * Only greatest gap of top and bottom gaps is used between two rows (or top gap if equal)
   */
  fun topGap(topGap: TopGap): Row

  /**
   * Adds gap after current row. It is visible together with the row.
   * Only greatest gap of top and bottom gaps is used between two rows (or top gap if equal)
   */
  fun bottomGap(bottomGap: BottomGap): Row

  /**
   * Creates subpanel inside cell of the row
   */
  fun panel(init: Panel.() -> Unit): Panel

  fun checkBox(@NlsContexts.Checkbox text: String): Cell<JBCheckBox>

  fun radioButton(@NlsContexts.RadioButton text: String): Cell<JBRadioButton>

  fun radioButton(@NlsContexts.RadioButton text: String, value: Any): Cell<JBRadioButton>

  fun button(@NlsContexts.Button text: String, actionListener: (event: ActionEvent) -> Unit): Cell<JButton>

  fun button(@NlsContexts.Button text: String, @NonNls actionPlace: String, action: AnAction): Cell<JButton>

  fun actionButton(action: AnAction, dimension: Dimension = ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE): Cell<ActionButton>

  fun gearButton(vararg actions: AnAction): Cell<JComponent>

  fun <T> segmentedButton(options: Collection<T>, property: GraphProperty<T>, renderer: (T) -> String): Cell<SegmentedButtonToolbar>

  fun slider(min: Int, max: Int, minorTickSpacing: Int, majorTickSpacing: Int): Cell<JSlider>

  /**
   * Adds a label. For label that relates to joined control [Panel.row] and [Cell.label] must be used,
   * because they set correct gap between label and component and set [JLabel.labelFor] property
   */
  fun label(@NlsContexts.Label text: String): Cell<JLabel>

  fun labelHtml(@NlsContexts.Label text: String,
                action: HyperlinkEventAction = HyperlinkEventAction.HTML_HYPERLINK_INSTANCE): Cell<JEditorPane>

  fun comment(@NlsContexts.DetailedDescription text: String, maxLineLength: Int = -1): Cell<JLabel>

  fun commentNoWrap(@NlsContexts.DetailedDescription text: String): Cell<JLabel>

  fun commentHtml(@NlsContexts.DetailedDescription text: String,
                  action: HyperlinkEventAction = HyperlinkEventAction.HTML_HYPERLINK_INSTANCE): Cell<JEditorPane>

  /**
   * Creates focusable link with text inside. Should not be used with html in [text]
   */
  fun link(@NlsContexts.LinkLabel text: String, action: (ActionEvent) -> Unit): Cell<ActionLink>

  /**
   * Creates focusable browser link with text inside. Should not be used with html in [text]
   */
  fun browserLink(@NlsContexts.LinkLabel text: String, url: String): Cell<BrowserLink>

  fun icon(icon: Icon): Cell<JLabel>

  fun contextHelp(@NlsContexts.Tooltip description: String, @TooltipTitle title: String? = null): Cell<JLabel>

  /**
   * Creates text field with [columns] set to [COLUMNS_SHORT]
   */
  fun textField(): Cell<JBTextField>

  /**
   * Creates text field with browse button and [columns] set to [COLUMNS_SHORT]
   */
  fun textFieldWithBrowseButton(@NlsContexts.DialogTitle browseDialogTitle: String? = null,
                                project: Project? = null,
                                fileChooserDescriptor: FileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(),
                                fileChosen: ((chosenFile: VirtualFile) -> String)? = null): Cell<TextFieldWithBrowseButton>

  /**
   * Creates integer text field with [columns] set to [COLUMNS_TINY]
   *
   * @param range allowed values range inclusive
   * @param keyboardStep increase/decrease step for keyboard keys up/down. The keys are not used if [keyboardStep] is null
   */
  fun intTextField(range: IntRange? = null, keyboardStep: Int? = null): Cell<JBTextField>

  fun textArea(): Cell<JBTextArea>

  fun <T> comboBox(model: ComboBoxModel<T>, renderer: ListCellRenderer<T?>? = null): Cell<ComboBox<T>>

  fun <T> comboBox(items: Array<T>, renderer: ListCellRenderer<T?>? = null): Cell<ComboBox<T>>

  /**
   * Overrides all gaps around row by [customRowGaps]. Should be used for very specific cases
   */
  fun customize(customRowGaps: VerticalGaps): Row
}
