// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import com.intellij.ide.TooltipTitle
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.*
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.ui.dsl.gridLayout.Grid
import com.intellij.ui.dsl.gridLayout.UnscaledGapsY
import com.intellij.ui.dsl.gridLayout.VerticalGaps
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.util.Function
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.ui.ThreeStateCheckBox
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.awt.event.ActionEvent
import javax.swing.*

/**
 * Determines relation between row grid and parent's grid
 */
enum class RowLayout {
  /**
   * All cells of the row (including label if present) independent of parent grid.
   * That means this row has its own grid.
   */
  INDEPENDENT,

  /**
   * Label is aligned, other components independent of parent grid. If label is not provided
   * then first cell (sometimes can be [JCheckBox] for example) is considered as a label.
   * That means the label is in parent grid; other components have their own grid.
   */
  LABEL_ALIGNED,

  /**
   * All components including label are in parent grid.
   * That means label and other components are in the parent grid.
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

@ApiStatus.NonExtendable
@LayoutDslMarker
@JvmDefaultWithCompatibility
interface Row {
  /**
   * Layout of the row.
   * Default value is [RowLayout.LABEL_ALIGNED] when label is provided for the row, [RowLayout.INDEPENDENT] otherwise
   */
  fun layout(rowLayout: RowLayout): Row

  /**
   * Marks the row as resizable: the row occupies all extra vertical space in parent (for example in [Panel.group] or [Panel.panel])
   * and changes size together with parent.
   * When resizable is necessary in whole [DialogPanel] all row parents should be marked as [resizableRow] as well.
   * It's possible to have several resizable rows, which means extra space is shared between them.
   * Note that alignment inside the cell is managed by [CellBase.align] method.
   *
   * @see [Grid.resizableRows]
   */
  fun resizableRow(): Row

  /**
   * Adds comment after the row with the appropriate color and font size (macOS and Linux use smaller font).
   * * [comment] can contain HTML tags except &lt;html&gt;, which is added automatically
   * * \n does not work as new line in HTML, use &lt;br&gt; instead
   * * Links with href to http/https are automatically marked with additional arrow icon
   * * Use bundled icons with `<code>` tag, for example `<icon src='AllIcons.General.Information'>`
   *
   * Visibility and enabled state of the row affects row comment as well.
   *
   * @see MAX_LINE_LENGTH_WORD_WRAP
   * @see MAX_LINE_LENGTH_NO_WRAP
   */
  fun rowComment(@NlsContexts.DetailedDescription comment: String,
                 maxLineLength: Int = DEFAULT_COMMENT_WIDTH,
                 action: HyperlinkEventAction = HyperlinkEventAction.HTML_HYPERLINK_INSTANCE): Row

  /**
   * Adds [component].
   * Use this method only for custom specific components; all standard components like label, button,
   * checkbox, etc. are covered by dedicated [Row] factory methods
   */
  fun <T : JComponent> cell(component: T): Cell<T>

  /**
   * Adds an empty cell in the grid
   */
  fun cell()

  /**
   * Adds [component] wrapped by [JBScrollPane]
   */
  fun <T : JComponent> scrollCell(component: T): Cell<T>

  /**
   * Adds a reserved cell in layout which can be populated by content later
   */
  fun placeholder(): Placeholder

  /**
   * Sets visibility of the row including comment [Row.rowComment] and all children recursively.
   * The row is invisible if there is an invisible parent
   */
  fun visible(isVisible: Boolean): Row

  /**
   * Binds row visibility to provided [predicate]
   */
  fun visibleIf(predicate: ComponentPredicate): Row

  /**
   * Binds row visibility to provided [property] predicate.
   */
  fun visibleIf(property: ObservableProperty<Boolean>): Row

  /**
   * Sets enabled state of the row including comment [Row.rowComment] and all children recursively.
   * The row is disabled if there is a disabled parent
   */
  fun enabled(isEnabled: Boolean): Row

  /**
   * Binds the enabled state of row to provided [predicate]
   */
  fun enabledIf(predicate: ComponentPredicate): Row

  /**
   * Binds enabled state of row to provided [property] predicate.
   */
  fun enabledIf(property: ObservableProperty<Boolean>): Row

  /**
   * Adds a gap above the current row.
   * It is visible together with the row.
   * Only the greatest gap of top and bottom gaps is used between two rows (or top gap if equal)
   */
  fun topGap(topGap: TopGap): Row

  /**
   * Adds a gap below the current row.
   * It is visible together with the row.
   * Only the greatest gap of top and bottom gaps is used between two rows (or top gap if equal)
   */
  fun bottomGap(bottomGap: BottomGap): Row

  /**
   * Creates subpanel inside the cell of the row. The panel contains its own rows and cells.
   */
  fun panel(init: Panel.() -> Unit): Panel

  fun checkBox(@NlsContexts.Checkbox text: String): Cell<JBCheckBox>

  fun threeStateCheckBox(@NlsContexts.Checkbox text: String): Cell<ThreeStateCheckBox>

  /**
   * Adds radio button. [Panel.buttonsGroup] must be defined above hierarchy before adding radio buttons (and therefore there is no need
   * to create [ButtonGroup] and register the radio button there).
   *
   *
   * If there is a binding [ButtonsGroup.bind] for the buttons group then:
   * * [value] must be provided with correspondent to a binding type for all radio buttons in the group
   * * it's possible to mark default radio button by [JRadioButton.isSelected] = true. Such a button will be selected by default in case
   * the initial value of bound variable doesn't equal any values of radio buttons in the group.
   *
   * If there is no binding, then the values of all radio buttons in the group must be null.
   */
  fun radioButton(@NlsContexts.RadioButton text: String, value: Any? = null): Cell<JBRadioButton>

  fun button(@NlsContexts.Button text: String, actionListener: (event: ActionEvent) -> Unit): Cell<JButton>

  fun button(@NlsContexts.Button text: String, action: AnAction, @NonNls actionPlace: String = ActionPlaces.UNKNOWN): Cell<JButton>

  /**
   * [renderer] converts values to visual presentation. Every presentation must have non-empty text, other properties are optional.
   * Use [SegmentedButton.update] if text, hint, or other properties in model are changed and should be re-rendered
   */
  fun <T> segmentedButton(items: Collection<T>, renderer: SegmentedButton.ItemPresentation.(T) -> Unit): SegmentedButton<T>

  fun slider(min: Int, max: Int, minorTickSpacing: Int, majorTickSpacing: Int): Cell<JSlider>

  /**
   * Adds a label. For label that relates to joined control [Panel.row] and [Cell.label] must be used,
   * because they set a correct gap between label and component and set [JLabel.labelFor] property
   */
  fun label(@NlsContexts.Label text: String): Cell<JLabel>

  /**
   * Adds text
   * * [text] can contain HTML tags except &lt;html&gt;, which is added automatically
   * * \n does not work as new line in html, use &lt;br&gt; instead
   * * Links with href to http/https are automatically marked with additional arrow icon
   * * Use bundled icons with `<code>` tag, for example `<icon src='AllIcons.General.Information'>`
   * * MAX_LINE_LENGTH_WORD_WRAP sets AlignX.FILL, with other horizontal aligns word wrap is not supported
   *
   * @see DEFAULT_COMMENT_WIDTH
   * @see MAX_LINE_LENGTH_WORD_WRAP
   * @see MAX_LINE_LENGTH_NO_WRAP
   */
  fun text(@NlsContexts.Label text: String, maxLineLength: Int = MAX_LINE_LENGTH_WORD_WRAP,
           action: HyperlinkEventAction = HyperlinkEventAction.HTML_HYPERLINK_INSTANCE): Cell<JEditorPane>

  /**
   * Adds comment with the appropriate color and font size (macOS and Linux use smaller font).
   * * [comment] can contain HTML tags except &lt;html&gt;, which is added automatically
   * * \n does not work as new line in html, use &lt;br&gt; instead
   * * Links with href to http/https are automatically marked with additional arrow icon
   * * Use bundled icons with `<code>` tag, for example `<icon src='AllIcons.General.Information'>`
   * * MAX_LINE_LENGTH_WORD_WRAP sets AlignX.FILL, with other horizontal aligns word wrap is not supported
   *
   * @see DEFAULT_COMMENT_WIDTH
   * @see MAX_LINE_LENGTH_WORD_WRAP
   * @see MAX_LINE_LENGTH_NO_WRAP
   */
  fun comment(@NlsContexts.DetailedDescription comment: String, maxLineLength: Int = MAX_LINE_LENGTH_WORD_WRAP,
              action: HyperlinkEventAction = HyperlinkEventAction.HTML_HYPERLINK_INSTANCE): Cell<JEditorPane>

  /**
   * Creates a focusable link with text inside. Should not be used with HTML in [text].
   */
  fun link(@NlsContexts.LinkLabel text: String, action: (ActionEvent) -> Unit): Cell<ActionLink>

  /**
   * Creates a focusable browser link with text inside. Should not be used with HTML in [text].
   */
  fun browserLink(@NlsContexts.LinkLabel text: String, url: String): Cell<BrowserLink>

  /**
   * Use [Cell.onChanged] to listen to selection changes.
   *
   * @param item current item
   * @param items list of all available items in popup
   */
  fun <T> dropDownLink(item: T, items: List<T>): Cell<DropDownLink<T>>

  fun icon(icon: Icon): Cell<JLabel>

  fun contextHelp(@NlsContexts.Tooltip description: String, @TooltipTitle title: String? = null): Cell<JLabel>

  /**
   * Creates text field with [columns] set to [COLUMNS_SHORT]
   */
  fun textField(): Cell<JBTextField>

  /**
   * Creates text field with browse button and [columns] set to [COLUMNS_SHORT]
   */
  @ApiStatus.Experimental
  fun textFieldWithBrowseButton(
    project: Project? = null,
    fileChosen: ((chosenFile: VirtualFile) -> String)? = null
  ): Cell<TextFieldWithBrowseButton> =
    textFieldWithBrowseButton(FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(), project, fileChosen)

  /**
   * Creates text field with browse button and [columns] set to [COLUMNS_SHORT]
   */
  @ApiStatus.Experimental
  fun textFieldWithBrowseButton(
    browseDialogTitle: @NlsContexts.DialogTitle String,
    project: Project? = null,
    fileChosen: ((chosenFile: VirtualFile) -> String)? = null
  ): Cell<TextFieldWithBrowseButton> =
    textFieldWithBrowseButton(FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor().withTitle(browseDialogTitle), project, fileChosen)

  /**
   * Creates text field with browse button and [columns] set to [COLUMNS_SHORT]
   */
  @ApiStatus.Experimental
  fun textFieldWithBrowseButton(
    fileChooserDescriptor: FileChooserDescriptor,
    project: Project? = null,
    fileChosen: ((chosenFile: VirtualFile) -> String)? = null
  ): Cell<TextFieldWithBrowseButton>

  @Deprecated(
    "Use [Row.textFieldWithBrowseButton(String, Project?, ((VirtualFile) -> String)?)] " +
    "or [Row.textFieldWithBrowseButton(FileChooserDescriptor, Project?, ((VirtualFile) -> String)?)] together with [FileChooserDescriptor.withTitle]",
    level = DeprecationLevel.ERROR,
  )
  fun textFieldWithBrowseButton(
    @NlsContexts.DialogTitle browseDialogTitle: String? = null,
    project: Project? = null,
    fileChooserDescriptor: FileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(),
    fileChosen: ((chosenFile: VirtualFile) -> String)? = null
  ): Cell<TextFieldWithBrowseButton> =
    textFieldWithBrowseButton(fileChooserDescriptor.withTitle(browseDialogTitle), project, fileChosen)

  /**
   * Creates password field with [columns] set to [COLUMNS_SHORT]
   */
  fun passwordField(): Cell<JBPasswordField>

  /**
   * Creates expandable text field with [columns] set to [COLUMNS_SHORT]
   */
  fun expandableTextField(parser: Function<in String, out MutableList<String>> = ParametersListUtil.DEFAULT_LINE_PARSER,
                          joiner: Function<in MutableList<String>, String> = ParametersListUtil.DEFAULT_LINE_JOINER): Cell<ExpandableTextField>

  /**
   * Creates integer text field with [columns] set to [COLUMNS_TINY]
   *
   * @param range allowed value range inclusive
   * @param keyboardStep increase/decrease step for keyboard keys up/down. The keys are not used if [keyboardStep] is null
   */
  fun intTextField(range: IntRange? = null, keyboardStep: Int? = null): Cell<JBTextField>

  /**
   * Creates spinner for int values.
   *
   * @param range allowed value range inclusive
   */
  fun spinner(range: IntRange, step: Int = 1): Cell<JBIntSpinner>

  /**
   * Creates spinner for double values.
   *
   * @param range allowed value range inclusive
   */
  fun spinner(range: ClosedRange<Double>, step: Double = 1.0): Cell<JSpinner>

  /**
   * Creates text area with [columns] set to [COLUMNS_SHORT].
   */
  fun textArea(): Cell<JBTextArea>

  /**
   * @see listCellRenderer
   * @see textListCellRenderer
   */
  fun <T> comboBox(model: ComboBoxModel<T>, renderer: ListCellRenderer<in T?>? = null): Cell<ComboBox<T>>

  /**
   * @see listCellRenderer
   * @see textListCellRenderer
   */
  fun <T> comboBox(items: Collection<T>, renderer: ListCellRenderer<in T?>? = null): Cell<ComboBox<T>>

  /**
   * Overrides all gaps around row by [customRowGaps]. Should be used for very specific cases
   */
  @Deprecated("Use overloaded customize(...) with UnscaledGapsY", level = DeprecationLevel.HIDDEN)
  @ApiStatus.ScheduledForRemoval
  fun customize(customRowGaps: VerticalGaps): Row

  /**
   * Overrides all gaps around row by [customRowGaps]. Should be used for very specific cases
   */
  fun customize(customRowGaps: UnscaledGapsY): Row
}
