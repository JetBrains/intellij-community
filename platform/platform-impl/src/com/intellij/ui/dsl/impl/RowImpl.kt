// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.impl

import com.intellij.BundleBase
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.*
import com.intellij.ui.components.*
import com.intellij.ui.dsl.*
import com.intellij.ui.dsl.Cell
import com.intellij.ui.dsl.Row
import com.intellij.ui.dsl.gridLayout.UiDslException
import com.intellij.ui.layout.*
import com.intellij.util.MathUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.event.*
import javax.swing.*

internal enum class BottomGap {
  BUTTON_GROUP_HEADER
}

@ApiStatus.Internal
internal class RowImpl(private val dialogPanelConfig: DialogPanelConfig,
                       panelContext: PanelContext,
                       val label: JLabel? = null) : Row {

  var rowLayout = if (label == null) RowLayout.INDEPENDENT else RowLayout.LABEL_ALIGNED
    private set

  var comment: JComponent? = null
    private set

  var topGap: TopGap? = null
    private set

  var bottomGap: BottomGap? = null
    private set

  val cells: List<CellBaseImpl<*>>
    get() = _cells

  private val _cells = mutableListOf<CellBaseImpl<*>>()
  private val indentCount = panelContext.indentCount

  init {
    label?.let { cell(it) }
  }

  override fun layout(rowLayout: RowLayout): RowImpl {
    this.rowLayout = rowLayout
    return this
  }

  override fun comment(comment: String, maxLineLength: Int): RowImpl {
    this.comment = ComponentPanelBuilder.createCommentComponent(comment, true, maxLineLength, true)
    return this
  }

  override fun <T : JComponent> cell(component: T): CellImpl<T> {
    val result = CellImpl(dialogPanelConfig, component)
    _cells.add(result)

    if (component is JRadioButton) {
      dialogPanelConfig.context.getButtonGroup()?.add(component)
    }

    return result
  }

  fun cell(cell: CellBaseImpl<*>) {
    _cells.add(cell)
  }

  override fun enabled(isEnabled: Boolean): RowImpl {
    _cells.forEach {
      when (it) {
        is CellImpl<*> -> it.enabledFromParent(isEnabled)
        is PanelImpl -> it.enabled(isEnabled)
      }
    }
    comment?.let { it.isEnabled = isEnabled }
    return this
  }

  override fun enabledIf(predicate: ComponentPredicate): RowImpl {
    enabled(predicate())
    predicate.addListener { enabled(it) }
    return this
  }

  override fun visible(isVisible: Boolean): RowImpl {
    _cells.forEach {
      when (it) {
        is CellImpl<*> -> it.visibleFromParent(isVisible)
        is PanelImpl -> it.visible(isVisible)
      }
    }
    comment?.let { it.isVisible = isVisible }
    return this
  }

  override fun gap(topGap: TopGap): RowImpl {
    this.topGap = topGap
    return this
  }

  fun gap(bottomGap: BottomGap): RowImpl {
    this.bottomGap = bottomGap
    return this
  }

  override fun panel(init: Panel.() -> Unit): PanelImpl {
    val result = PanelImpl(dialogPanelConfig)
    result.init()
    _cells.add(result)
    return result
  }

  override fun checkBox(@NlsContexts.Checkbox text: String): CellImpl<JBCheckBox> {
    return cell(JBCheckBox(text))
  }

  override fun radioButton(text: String): Cell<JBRadioButton> {
    return cell(JBRadioButton(text))
  }

  override fun radioButton(text: String, value: Any): Cell<JBRadioButton> {
    val group = dialogPanelConfig.context.getButtonGroup() ?: throw UiDslException(
      "Button group must be defined before using radio button with value")
    val valueType = value::class.javaPrimitiveType ?: value::class.java
    if (valueType != group.type) {
      throw UiDslException("Value $value is incompatible with button group binding class ${group.type.simpleName}")
    }
    val binding = group.binding
    val result = radioButton(text)
    val component = result.component
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

  override fun button(text: String, actionPlace: String, action: AnAction): CellImpl<JButton> {
    lateinit var result: CellImpl<JButton>
    result = button(text) {
      ActionUtil.invokeAction(action, result.component, actionPlace, null, null)
    }
    return result
  }

  override fun actionButton(action: AnAction, dimension: Dimension): CellImpl<ActionButton> {
    val component = ActionButton(action, action.templatePresentation, ActionPlaces.UNKNOWN, dimension)
    return cell(component)
  }

  override fun gearButton(vararg actions: AnAction): CellImpl<JComponent> {
    val label = JLabel(LayeredIcon.GEAR_WITH_DROPDOWN)
    label.disabledIcon = AllIcons.General.GearPlain
    object : ClickListener() {
      override fun onClick(e: MouseEvent, clickCount: Int): Boolean {
        if (!label.isEnabled) return true
        JBPopupFactory.getInstance()
          .createActionGroupPopup(null, DefaultActionGroup(*actions), DataContext { dataId ->
            when (dataId) {
              PlatformDataKeys.CONTEXT_COMPONENT.name -> label
              else -> null
            }
          }, true, null, 10)
          .showUnderneathOf(label)
        return true
      }
    }.installOn(label)

    return cell(label)
  }

  override fun actionLink(text: String, action: ActionListener): Cell<ActionLink> {
    return cell(ActionLink(text, action))
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

  override fun commentNoWrap(text: String): Cell<JLabel> {
    return cell(ComponentPanelBuilder.createNonWrappingCommentComponent(text))
  }

  override fun browserLink(text: String, url: String): Cell<BrowserLink> {
    return cell(BrowserLink(text, url))
  }

  override fun contextHelp(description: String, title: String?): Cell<JLabel> {
    val result = if (title == null) ContextHelpLabel.create(description)
    else ContextHelpLabel.create(title, description)
    return cell(result)
  }

  override fun textField(): CellImpl<JBTextField> {
    return cell(JBTextField())
  }

  override fun textFieldWithBrowseButton(browseDialogTitle: String?,
                                         project: Project?,
                                         fileChooserDescriptor: FileChooserDescriptor,
                                         fileChosen: ((chosenFile: VirtualFile) -> String)?): Cell<TextFieldWithBrowseButton> {
    val result = textFieldWithBrowseButton(project, browseDialogTitle, fileChooserDescriptor, fileChosen)
    return cell(result)
  }

  override fun intTextField(range: IntRange?, keyboardStep: Int?): CellImpl<JBTextField> {
    val result = textField()
      .validationOnInput {
        val value = it.text.toIntOrNull()
        when {
          value == null -> error(UIBundle.message("please.enter.a.number"))
          range != null && value !in range -> error(UIBundle.message("please.enter.a.number.from.0.to.1", range.first, range.last))
          else -> null
        }
      }
    result.component.putClientProperty(DSL_INT_TEXT_RANGE_PROPERTY, range)

    keyboardStep?.let {
      result.component.addKeyListener(object : KeyAdapter() {
        override fun keyPressed(e: KeyEvent?) {
          val increment: Int
          when (e?.keyCode) {
            KeyEvent.VK_UP -> increment = keyboardStep
            KeyEvent.VK_DOWN -> increment = -keyboardStep
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

  fun getIndent(): Int {
    return indentCount * dialogPanelConfig.spacing.horizontalIndent
  }
}
