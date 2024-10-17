// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder.impl

import com.intellij.BundleBase
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.UIBundle
import com.intellij.ui.components.*
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.ui.dsl.UiDslException
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.components.DslLabel
import com.intellij.ui.dsl.builder.components.DslLabelType
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.UnscaledGapsY
import com.intellij.ui.dsl.gridLayout.VerticalGaps
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.util.Function
import com.intellij.util.IconUtil
import com.intellij.util.MathUtil
import com.intellij.util.ui.*
import org.jetbrains.annotations.ApiStatus
import java.awt.event.ActionEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.util.*
import javax.swing.*

@Suppress("OVERRIDE_DEPRECATION")
@ApiStatus.Internal
internal open class RowImpl(private val dialogPanelConfig: DialogPanelConfig,
                            private val panelContext: PanelContext,
                            private val parent: PanelImpl,
                            rowLayout: RowLayout) : Row {

  var rowLayout: RowLayout = rowLayout
    private set

  var resizableRow: Boolean = false
    private set

  var rowComment: DslLabel? = null
    private set

  var topGap: TopGap? = null
    private set

  /**
   * Used if topGap is not set, skipped for first row
   */
  var internalTopGap: Int = 0

  var bottomGap: BottomGap? = null
    private set

  /**
   * Used if bottomGap is not set, skipped for last row
   */
  var internalBottomGap: Int = 0

  val cells: MutableList<CellBaseImpl<*>?> = mutableListOf()

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

  override fun <T : JComponent> cell(component: T): CellImpl<T> {
    return cellImpl(component, component)
  }

  override fun cell() {
    cells.add(null)
  }

  override fun <T : JComponent> scrollCell(component: T): CellImpl<T> {
    return cellImpl(component, JBScrollPane(component))
  }

  override fun placeholder(): PlaceholderImpl {
    val result = PlaceholderImpl(this)
    cells.add(result)
    return result
  }

  override fun enabled(isEnabled: Boolean): RowImpl {
    enabled = isEnabled
    if (parent.isEnabled(this)) {
      doEnabled(enabled)
    }
    return this
  }

  fun enabledFromParent(parentEnabled: Boolean): RowImpl {
    doEnabled(parentEnabled && enabled)
    return this
  }

  fun isEnabled(): Boolean {
    return enabled && parent.isEnabled(this)
  }

  override fun enabledIf(predicate: ComponentPredicate): RowImpl {
    enabled(predicate())
    predicate.addListener { enabled(it) }
    return this
  }

  override fun enabledIf(property: ObservableProperty<Boolean>): RowImpl {
    return enabledIf(ComponentPredicate.fromObservableProperty(property))
  }

  override fun visible(isVisible: Boolean): RowImpl {
    visible = isVisible
    if (parent.isVisible(this)) {
      doVisible(visible)
    }
    return this
  }

  override fun visibleIf(predicate: ComponentPredicate): RowImpl {
    visible(predicate())
    predicate.addListener { visible(it) }
    return this
  }

  override fun visibleIf(property: ObservableProperty<Boolean>): RowImpl {
    return visibleIf(ComponentPredicate.fromObservableProperty(property))
  }

  fun visibleFromParent(parentVisible: Boolean): RowImpl {
    doVisible(parentVisible && visible)
    return this
  }

  fun isVisible(): Boolean {
    return visible && parent.isVisible(this)
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
    val result = PanelImpl(dialogPanelConfig, parent.spacingConfiguration, this)
    result.init()
    cells.add(result)
    return result
  }

  override fun checkBox(@NlsContexts.Checkbox text: String): CellImpl<JBCheckBox> {
    return cell(JBCheckBox(text)).applyToComponent {
      isOpaque = false
    }
  }

  override fun threeStateCheckBox(@NlsContexts.Checkbox text: String): Cell<ThreeStateCheckBox> {
    return cell(ThreeStateCheckBox(text)).applyToComponent {
      isOpaque = false
    }
  }

  override fun radioButton(text: String, value: Any?): Cell<JBRadioButton> {
    val result = cell(JBRadioButton(text)).applyToComponent {
      isOpaque = false
    }
    registerRadioButton(result, value)
    return result
  }

  override fun button(@NlsContexts.Button text: String, actionListener: (event: ActionEvent) -> Unit): CellImpl<JButton> {
    val button = JButton(BundleBase.replaceMnemonicAmpersand(text))
    button.addActionListener(actionListener)
    button.isOpaque = false
    return cell(button)
  }

  override fun button(text: String, action: AnAction, actionPlace: String): Cell<JButton> {
    lateinit var result: CellImpl<JButton>
    result = button(text) {
      ActionUtil.invokeAction(action, result.component, actionPlace, null, null)
    }
    return result
  }

  override fun <T> segmentedButton(items: Collection<T>, renderer: SegmentedButton.ItemPresentation.(T) -> Unit): SegmentedButton<T> {
    val result = SegmentedButtonImpl(dialogPanelConfig, this, renderer)
    result.items = items
    cells.add(result)
    return result
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
    return cell(JLabel(text))
  }

  override fun text(@NlsContexts.Label text: String, maxLineLength: Int, action: HyperlinkEventAction): Cell<JEditorPane> {
    val dslLabel = DslLabel(DslLabelType.LABEL)
    dslLabel.action = action
    dslLabel.maxLineLength = maxLineLength
    dslLabel.text = text
    val result = cell(dslLabel)
    if (maxLineLength == MAX_LINE_LENGTH_WORD_WRAP) {
      result.align(AlignX.FILL)
    }
    return result
  }
  
  override fun comment(comment: String, maxLineLength: Int, action: HyperlinkEventAction): CellImpl<JEditorPane> {
    val result: CellImpl<JEditorPane> = cell(createComment(comment, maxLineLength, action))
    if (maxLineLength == MAX_LINE_LENGTH_WORD_WRAP) {
      result.align(AlignX.FILL)
    }
    return result
  }

  override fun link(text: String, action: (ActionEvent) -> Unit): CellImpl<ActionLink> {
    return cell(ActionLink(text, action))
  }

  override fun browserLink(text: String, url: String): CellImpl<BrowserLink> {
    return cell(BrowserLink(text, url))
  }

  override fun <T> dropDownLink(item: T, items: List<T>): Cell<DropDownLink<T>> {
    return cell(DropDownLink(item, items, onSelect = { }, updateText = true))
  }

  override fun icon(icon: Icon): CellImpl<JLabel> {
    val label = JBLabel(icon)
    label.disabledIcon = IconUtil.desaturate(icon)
    return cell(label)
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

  override fun textFieldWithBrowseButton(
    fileChooserDescriptor: FileChooserDescriptor,
    project: Project?,
    fileChosen: ((chosenFile: VirtualFile) -> String)?
  ): Cell<TextFieldWithBrowseButton> {
    val result = cell(com.intellij.ui.components.textFieldWithBrowseButton(project, fileChooserDescriptor, fileChosen)).applyToComponent {
      isOpaque = false
      textField.isOpaque = false
    }
    result.columns(COLUMNS_SHORT)
    return result
  }

  override fun passwordField(): CellImpl<JBPasswordField> {
    val result = cell(JBPasswordField())
    result.columns(COLUMNS_SHORT)
    return result
  }

  override fun expandableTextField(parser: Function<in String, out MutableList<String>>,
                                   joiner: Function<in MutableList<String>, String>): Cell<ExpandableTextField> {
    val result = cell(ExpandableTextField(parser, joiner))
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
    result.component.putClientProperty(DslComponentPropertyInternal.INT_TEXT_RANGE, range)

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
    return cell(JBIntSpinner(range.first, range.first, range.last, step)).applyToComponent {
      isOpaque = false
    }
  }

  override fun spinner(range: ClosedRange<Double>, step: Double): Cell<JSpinner> {
    return cell(JSpinner(SpinnerNumberModel(range.start, range.start, range.endInclusive, step))).applyToComponent {
      isOpaque = false
    }
  }

  override fun textArea(): Cell<JBTextArea> {
    val textArea = JBTextArea()
    // Text area should have same margins as TextField. When margin is TestArea used then border is MarginBorder and margins are taken
    // into account twice, which is hard to workaround in current API. So use border instead
    textArea.border = JBEmptyBorder(3, 5, 3, 5)
    textArea.columns = COLUMNS_SHORT
    textArea.font = JBFont.regular()
    textArea.emptyText.setFont(JBFont.regular())
    textArea.putClientProperty(DslComponentProperty.VISUAL_PADDINGS, UnscaledGaps.EMPTY)
    return scrollCell(textArea)
  }

  override fun <T> comboBox(model: ComboBoxModel<T>, renderer: ListCellRenderer<in T?>?): Cell<ComboBox<T>> {
    val component = ComboBox(model)
    // todo check usage of com.intellij.ui.dsl.builder.UtilsKt#listCellRenderer here
    component.renderer = renderer ?: SimpleListCellRenderer.create("") { it.toString() }
    return cell(component)
  }

  override fun <T> comboBox(items: Collection<T>, renderer: ListCellRenderer<in T?>?): Cell<ComboBox<T>> {
    return comboBox(DefaultComboBoxModel(Vector(items)), renderer)
  }

  override fun customize(customRowGaps: VerticalGaps): Row {
    return customize(UnscaledGapsY(JBUI.unscale(customRowGaps.top), JBUI.unscale(customRowGaps.bottom)))
  }

  override fun customize(customRowGaps: UnscaledGapsY): Row {
    internalTopGap = customRowGaps.top
    internalBottomGap = customRowGaps.bottom
    topGap = null
    bottomGap = null

    return this
  }

  fun getIndent(): Int {
    return panelContext.indentCount * parent.spacingConfiguration.horizontalIndent
  }

  private fun doVisible(isVisible: Boolean) {
    for (cell in cells) {
      cell?.visibleFromParent(isVisible)
    }
    rowComment?.let { it.isVisible = isVisible }
  }

  private fun doEnabled(isEnabled: Boolean) {
    for (cell in cells) {
      cell?.enabledFromParent(isEnabled)
    }
    rowComment?.let { it.isEnabled = isEnabled }
  }

  private fun registerRadioButton(cell: CellImpl<out JRadioButton>, value: Any?) {
    val buttonsGroup = dialogPanelConfig.context.getButtonsGroup() ?: throw UiDslException(
      "Button group must be defined before using radio button")
    buttonsGroup.add(cell, value)
  }

  private fun <T : JComponent> cellImpl(component: T, viewComponent: JComponent): CellImpl<T> {
    val result = CellImpl(dialogPanelConfig, component, this, viewComponent)
    cells.add(result)

    registerCreationStacktrace(component)

    if (component is JRadioButton) {
      @Suppress("UNCHECKED_CAST")
      registerRadioButton(result as CellImpl<JRadioButton>, null)
    }

    return result
  }
}
