// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.impl

import com.intellij.BundleBase
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.UIBundle
import com.intellij.ui.components.*
import com.intellij.ui.dsl.UiDslException
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.components.DslLabel
import com.intellij.ui.dsl.builder.components.DslLabelType
import com.intellij.ui.dsl.builder.components.SegmentedButtonAction
import com.intellij.ui.dsl.builder.components.SegmentedButtonToolbar
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.ui.dsl.gridLayout.VerticalGaps
import com.intellij.ui.layout.*
import com.intellij.ui.popup.PopupState
import com.intellij.util.MathUtil
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.event.ActionEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*

@ApiStatus.Internal
internal class RowImpl(private val dialogPanelConfig: DialogPanelConfig,
                       private val panelContext: PanelContext,
                       private val parent: PanelImpl,
                       val firstCellLabel: Boolean,
                       rowLayout: RowLayout) : Row {

  var rowLayout = rowLayout
    private set

  var resizableRow = false
    private set

  var rowComment: JComponent? = null
    private set

  var topGap: TopGap? = null
    private set

  /**
   * Used if topGap is not set, skipped for first row
   */
  var internalTopGap = 0

  var bottomGap: BottomGap? = null
    private set

  /**
   * Used if bottomGap is not set, skipped for last row
   */
  var internalBottomGap = 0

  val cells = mutableListOf<CellBaseImpl<*>?>()

  private var visible = true
  private var enabled = true

  override fun layout(rowLayout: RowLayout): RowImpl {
    this.rowLayout = rowLayout
    return this
  }

  override fun resizableRow(): RowImpl {
    resizableRow = true
    return this
  }

  override fun rowComment(@NlsContexts.DetailedDescription comment: String, maxLineLength: Int, action: HyperlinkEventAction): RowImpl {
    this.rowComment = createComment(comment, maxLineLength, action)
    return this
  }

  override fun <T : JComponent> cell(component: T, viewComponent: JComponent): CellImpl<T> {
    return cell(component, viewComponent, null)
  }

  override fun cell() {
    cells.add(null)
  }

  fun cell(cell: CellBaseImpl<*>) {
    cells.add(cell)
  }

  override fun placeholder(): Placeholder {
    TODO("Not yet implemented")
  }

  override fun enabled(isEnabled: Boolean): RowImpl {
    enabled = isEnabled
    if (parent.isEnabled()) {
      doEnabled(enabled)
    }
    return this
  }

  fun enabledFromParent(parentEnabled: Boolean): RowImpl {
    doEnabled(parentEnabled && enabled)
    return this
  }

  fun isEnabled(): Boolean {
    return enabled && parent.isEnabled()
  }

  override fun enabledIf(predicate: ComponentPredicate): RowImpl {
    enabled(predicate())
    predicate.addListener { enabled(it) }
    return this
  }

  override fun visible(isVisible: Boolean): RowImpl {
    visible = isVisible
    if (parent.isVisible()) {
      doVisible(visible)
    }
    return this
  }

  override fun visibleIf(predicate: ComponentPredicate): Row {
    visible(predicate())
    predicate.addListener { visible(it) }
    return this
  }

  fun visibleFromParent(parentVisible: Boolean): RowImpl {
    doVisible(parentVisible && visible)
    return this
  }

  fun isVisible(): Boolean {
    return visible && parent.isVisible()
  }

  override fun topGap(topGap: TopGap): RowImpl {
    this.topGap = topGap
    return this
  }

  override fun bottomGap(bottomGap: BottomGap): RowImpl {
    this.bottomGap = bottomGap
    return this
  }

  override fun panel(init: Panel.() -> Unit): PanelImpl {
    val result = PanelImpl(dialogPanelConfig, this)
    result.init()
    cells.add(result)
    return result
  }

  override fun checkBox(@NlsContexts.Checkbox text: String): CellImpl<JBCheckBox> {
    return cell(JBCheckBox(text))
  }

  override fun radioButton(text: String): Cell<JBRadioButton> {
    val group = dialogPanelConfig.context.getButtonGroup() ?: throw UiDslException(
      "Button group must be defined before using radio button")
    if (group is BindButtonGroup<*>) {
      throw UiDslException("Parent button group provides binding but value for radioButton is not provided")
    }
    val result = cell(JBRadioButton(text))
    group.add(result.component)
    return result
  }

  override fun radioButton(text: String, value: Any): Cell<JBRadioButton> {
    val group = dialogPanelConfig.context.getButtonGroup() ?: throw UiDslException(
      "Button group must be defined before using radio button with value")
    if (group !is BindButtonGroup<*>) {
      throw UiDslException("Parent button group doesn't provide binding for $value")
    }
    if (value::class.java != group.type) {
      throw UiDslException("Value $value is incompatible with button group binding class ${group.type.simpleName}")
    }
    val binding = group.binding
    val result = cell(JBRadioButton(text))
    val component = result.component
    group.add(component)
    component.isSelected = binding.get() == value
    result.onApply { if (component.isSelected) group.set(value) }
    result.onReset { component.isSelected = binding.get() == value }
    result.onIsModified { component.isSelected != (binding.get() == value) }
    return result
  }

  override fun button(@NlsContexts.Button text: String, actionListener: (event: ActionEvent) -> Unit): CellImpl<JButton> {
    val button = JButton(BundleBase.replaceMnemonicAmpersand(text))
    button.addActionListener(actionListener)
    return cell(button)
  }

  override fun button(text: String, action: AnAction, actionPlace: String): Cell<JButton> {
    lateinit var result: CellImpl<JButton>
    result = button(text) {
      ActionUtil.invokeAction(action, result.component, actionPlace, null, null)
    }
    return result
  }

  override fun actionButton(action: AnAction, actionPlace: String): Cell<ActionButton> {
    val component = ActionButton(action, action.templatePresentation, actionPlace, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
    return cell(component)
  }

  override fun actionsButton(vararg actions: AnAction, actionPlace: String, icon: Icon): Cell<ActionButton> {
    val actionGroup = PopupActionGroup(arrayOf(*actions))
    actionGroup.templatePresentation.icon = icon
    return cell(ActionButton(actionGroup, actionGroup.templatePresentation, actionPlace, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE))
  }

  override fun <T> segmentedButton(options: Collection<T>, property: GraphProperty<T>, renderer: (T) -> String): Cell<SegmentedButtonToolbar> {
    val actionGroup = DefaultActionGroup(options.map { SegmentedButtonAction(it, property, renderer(it)) })
    val toolbar = SegmentedButtonToolbar(actionGroup, true, dialogPanelConfig.spacing)
    toolbar.targetComponent = null // any data context is supported, suppress warning
    return cell(toolbar)
  }

  override fun slider(min: Int, max: Int, minorTickSpacing: Int, majorTickSpacing: Int): Cell<JSlider> {
    val slider = JSlider()
    UIUtil.setSliderIsFilled(slider, true)
    slider.paintLabels = true
    slider.paintTicks = true
    slider.paintTrack = true
    slider.minimum = min
    slider.maximum = max
    slider.minorTickSpacing = minorTickSpacing
    slider.majorTickSpacing = majorTickSpacing
    return cell(slider)
  }

  override fun label(text: String): CellImpl<JLabel> {
    return cell(Label(text))
  }

  override fun text(@NlsContexts.Label text: String, maxLineLength: Int, action: HyperlinkEventAction): Cell<JEditorPane> {
    val dslLabel = DslLabel(DslLabelType.LABEL)
    dslLabel.action = action
    dslLabel.setHtmlText(text, maxLineLength)
    return cell(dslLabel)
  }

  override fun comment(comment: String, maxLineLength: Int, action: HyperlinkEventAction): CellImpl<JEditorPane> {
    return cell(createComment(comment, maxLineLength, action))
  }

  override fun link(text: String, action: (ActionEvent) -> Unit): CellImpl<ActionLink> {
    return cell(ActionLink(text, action))
  }

  override fun browserLink(text: String, url: String): CellImpl<BrowserLink> {
    return cell(BrowserLink(text, url))
  }

  override fun <T> dropDownLink(item: T, items: List<T>, onSelected: ((T) -> Unit)?, updateText: Boolean): Cell<DropDownLink<T>> {
    return cell(DropDownLink(item, items, onSelect = { t -> onSelected?.let { it(t) } }, updateText = updateText))
  }

  override fun icon(icon: Icon): CellImpl<JLabel> {
    return cell(JBLabel(icon))
  }

  override fun contextHelp(description: String, title: String?): CellImpl<JLabel> {
    val result = if (title == null) ContextHelpLabel.create(description)
    else ContextHelpLabel.create(title, description)
    return cell(result)
  }

  override fun textField(): CellImpl<JBTextField> {
    val result = cell(JBTextField())
    result.columns(COLUMNS_SHORT)
    return result
  }

  override fun textFieldWithBrowseButton(browseDialogTitle: String?,
                                         project: Project?,
                                         fileChooserDescriptor: FileChooserDescriptor,
                                         fileChosen: ((chosenFile: VirtualFile) -> String)?): Cell<TextFieldWithBrowseButton> {
    val result = cell(textFieldWithBrowseButton(project, browseDialogTitle, fileChooserDescriptor, fileChosen))
    result.columns(COLUMNS_SHORT)
    return result
  }

  override fun intTextField(range: IntRange?, keyboardStep: Int?): CellImpl<JBTextField> {
    val result = cell(JBTextField())
      .validationOnInput {
        val value = it.text.toIntOrNull()
        when {
          value == null -> error(UIBundle.message("please.enter.a.number"))
          range != null && value !in range -> error(UIBundle.message("please.enter.a.number.from.0.to.1", range.first, range.last))
          else -> null
        }
      }
    result.columns(COLUMNS_TINY)
    result.component.putClientProperty(DSL_INT_TEXT_RANGE_PROPERTY, range)

    keyboardStep?.let {
      result.component.addKeyListener(object : KeyAdapter() {
        override fun keyPressed(e: KeyEvent?) {
          val increment: Int = when (e?.keyCode) {
            KeyEvent.VK_UP -> keyboardStep
            KeyEvent.VK_DOWN -> -keyboardStep
            else -> return
          }

          var value = result.component.text.toIntOrNull()
          if (value != null) {
            value += increment
            if (range != null) {
              value = MathUtil.clamp(value, range.first, range.last)
            }
            result.component.text = value.toString()
            e.consume()
          }
        }
      })
    }
    return result
  }

  override fun spinner(range: IntRange, step: Int): CellImpl<JBIntSpinner> {
    return cell(JBIntSpinner(range.first, range.first, range.last, step))
  }

  override fun spinner(range: ClosedRange<Double>, step: Double): Cell<JSpinner> {
    return cell(JSpinner(SpinnerNumberModel(range.start, range.start, range.endInclusive, step)))
  }

  override fun textArea(): Cell<JBTextArea> {
    val textArea = JBTextArea()
    // Text area should have same margins as TextField. When margin is TestArea used then border is MarginBorder and margins are taken
    // into account twice, which is hard to workaround in current API. So use border instead
    textArea.border = JBEmptyBorder(3, 5, 3, 5)
    textArea.columns = COLUMNS_SHORT
    textArea.font = JBFont.regular()
    textArea.emptyText.setFont(JBFont.regular())
    return cell(textArea, JBScrollPane(textArea), Gaps.EMPTY)
  }

  override fun <T> comboBox(model: ComboBoxModel<T>, renderer: ListCellRenderer<T?>?): Cell<ComboBox<T>> {
    val component = ComboBox(model)
    component.renderer = renderer ?: SimpleListCellRenderer.create("") { it.toString() }
    return cell(component)
  }

  override fun <T> comboBox(items: Array<T>, renderer: ListCellRenderer<T?>?): Cell<ComboBox<T>> {
    val component = ComboBox(items)
    component.renderer = renderer ?: SimpleListCellRenderer.create("") { it.toString() }
    return cell(component)
  }

  override fun customize(customRowGaps: VerticalGaps): Row {
    internalTopGap = customRowGaps.top
    internalBottomGap = customRowGaps.bottom
    topGap = null
    bottomGap = null

    return this
  }

  fun getIndent(): Int {
    return panelContext.indentCount * dialogPanelConfig.spacing.horizontalIndent
  }

  private fun doVisible(isVisible: Boolean) {
    for (cell in cells) {
      when (cell) {
        is CellImpl<*> -> cell.visibleFromParent(isVisible)
        is PanelImpl -> cell.visibleFromParent(isVisible)
      }
    }
    rowComment?.let { it.isVisible = isVisible }
  }

  private fun doEnabled(isEnabled: Boolean) {
    cells.forEach {
      when (it) {
        is CellImpl<*> -> it.enabledFromParent(isEnabled)
        is PanelImpl -> it.enabledFromParent(isEnabled)
      }
    }
    rowComment?.let { it.isEnabled = isEnabled }
  }

  private fun <T : JComponent> cell(component: T, viewComponent: JComponent, visualPaddings: Gaps?): CellImpl<T> {
    val result = CellImpl(dialogPanelConfig, component, this, viewComponent, visualPaddings = visualPaddings)
    cells.add(result)
    return result
  }
}

private class PopupActionGroup(private val actions: Array<AnAction>): ActionGroup() {

  private val popupState = PopupState.forPopup()

  init {
    isPopup = true
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> =
    actions

  override fun isDumbAware(): Boolean =
    true

  override fun canBePerformed(context: DataContext): Boolean =
    actions.isNotEmpty()

  override fun actionPerformed(e: AnActionEvent) {
    if (popupState.isRecentlyHidden) {
      return
    }

    val popup = JBPopupFactory.getInstance().createActionGroupPopup(null, this, e.dataContext,
      JBPopupFactory.ActionSelectionAid.MNEMONICS, true)
    popupState.prepareToShow(popup)
    PopupUtil.showForActionButtonEvent(popup, e)
  }
}
