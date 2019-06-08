// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.BundleBase
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.UINumericRange
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.*
import com.intellij.ui.components.*
import com.intellij.ui.layout.migLayout.*
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.MouseEvent
import java.util.function.Consumer
import java.util.function.Supplier
import javax.swing.*
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty0

@DslMarker
annotation class CellMarker

interface CellBuilder<T : JComponent> {
  val component: T

  fun focused(): CellBuilder<T>
  fun withValidation(callback: (T) -> ValidationInfo?): CellBuilder<T>
  fun onApply(callback: () -> Unit): CellBuilder<T>
  fun onReset(callback: () -> Unit): CellBuilder<T>
  fun onIsModified(callback: () -> Boolean): CellBuilder<T>

  fun <V> withBinding(
    componentGet: () -> V,
    componentSet: (V) -> Unit,
    modelGet: () -> V,
    modelSet: (V) -> Unit
  ): CellBuilder<T> {
    onApply { modelSet(componentGet()) }
    onReset { componentSet(modelGet()) }
    onIsModified { componentGet() != modelGet() }
    return this
  }

  fun enabled(isEnabled: Boolean)
  fun enableIf(predicate: ComponentPredicate): CellBuilder<T>

  fun withErrorIf(message: String, callback: (T) -> Boolean): CellBuilder<T> {
    withValidation { if (callback(it)) ValidationInfo(message, it) else null }
    return this
  }
}

internal interface CheckboxCellBuilder {
  fun actsAsLabel()
}

fun <T : JCheckBox> CellBuilder<T>.actsAsLabel(): CellBuilder<T> {
  (this as CheckboxCellBuilder).actsAsLabel()
  return this
}

val CellBuilder<out AbstractButton>.selected
  get() = component.selected

const val UNBOUND_RADIO_BUTTON = "unbound.radio.button"

// separate class to avoid row related methods in the `cell { } `
@CellMarker
abstract class Cell {
  /**
   * Sets how keen the component should be to grow in relation to other component **in the same cell**. Use `push` in addition if need.
   * If this constraint is not set the grow weight is set to 0 and the component will not grow (unless some automatic rule is not applied (see [com.intellij.ui.layout.panel])).
   * Grow weight will only be compared against the weights for the same cell.
   */
  val growX = CCFlags.growX
  @Suppress("unused")
  val growY = CCFlags.growY
  val grow = CCFlags.grow

  /**
   * Makes the column that the component is residing in grow with `weight`.
   */
  val pushX = CCFlags.pushX

  /**
   * Makes the row that the component is residing in grow with `weight`.
   */
  @Suppress("unused")
  val pushY = CCFlags.pushY
  val push = CCFlags.push

  // backward compatibility - return type should be void
  fun label(text: String, gapLeft: Int = 0, style: UIUtil.ComponentStyle? = null, fontColor: UIUtil.FontColor? = null, bold: Boolean = false) {
    val label = Label(text, style, fontColor, bold)
    label(gapLeft = gapLeft)
  }

  fun link(text: String, style: UIUtil.ComponentStyle? = null, action: () -> Unit) {
    val result = Link(text, action = action)
    style?.let { UIUtil.applyStyle(it, result) }
    result()
  }

  fun browserLink(text: String, url: String) {
    val result = HyperlinkLabel()
    result.setHyperlinkText(text)
    result.setHyperlinkTarget(url)
    result()
  }

  fun button(text: String, vararg constraints: CCFlags, actionListener: (event: ActionEvent) -> Unit) {
    val button = JButton(BundleBase.replaceMnemonicAmpersand(text))
    button.addActionListener(actionListener)
    button(*constraints)
  }

  inline fun checkBox(text: String,
                      isSelected: Boolean = false,
                      comment: String? = null,
                      vararg constraints: CCFlags,
                      crossinline actionListener: (event: ActionEvent, component: JCheckBox) -> Unit): JCheckBox {
    val component = checkBox(text, isSelected, comment, *constraints)
    component.addActionListener(ActionListener {
      actionListener(it, component)
    })
    return component
  }

  @JvmOverloads
  fun checkBox(text: String, isSelected: Boolean = false, comment: String? = null, vararg constraints: CCFlags = emptyArray()): JCheckBox {
    val component = JCheckBox(text)
    component.isSelected = isSelected
    component(*constraints, comment = comment)
    return component
  }

  fun checkBox(text: String, prop: KMutableProperty0<Boolean>, comment: String? = null): CellBuilder<JBCheckBox> {
    return checkBox(text, prop.getter, prop.setter, comment)
  }

  fun checkBox(text: String, getter: () -> Boolean, setter: (Boolean) -> Unit, comment: String? = null): CellBuilder<JBCheckBox> {
    val component = JBCheckBox(text, getter())
    return component(comment = comment)
      .withBinding(component::isSelected, component::setSelected, getter, setter)
  }

  fun radioButton(text: String, comment: String? = null): CellBuilder<JBRadioButton> {
    val component = JBRadioButton(text)
    component.putClientProperty(UNBOUND_RADIO_BUTTON, true)
    return component(comment = comment)
  }

  fun radioButton(text: String, prop: KMutableProperty0<Boolean>, comment: String? = null): CellBuilder<JBRadioButton> {
    val component = JBRadioButton(text, prop.get())
    return component(comment = comment)
      .withBinding(component::isSelected, component::setSelected, prop.getter, prop.setter)
  }

  fun <T> comboBox(model: ComboBoxModel<T>, getter: () -> T?, setter: (T?) -> Unit, growPolicy: GrowPolicy? = null, renderer: ListCellRenderer<T?>? = null): CellBuilder<ComboBox<T>> {
    val component = ComboBox(model)
    if (renderer != null) {
      component.renderer = renderer
    }
    else {
      component.renderer = SimpleListCellRenderer.create("") { it.toString() }
    }
    val builder = component(growPolicy = growPolicy)
    return builder.withBinding({ component.selectedItem as T? }, { component.setSelectedItem(it) }, getter, setter)
  }

  fun <T> comboBox(model: ComboBoxModel<T>, prop: KMutableProperty0<T>, growPolicy: GrowPolicy? = null, renderer: ListCellRenderer<T?>? = null): CellBuilder<ComboBox<T>> {
    return comboBox(model, prop.getter, { prop.set(it!!) }, growPolicy, renderer)
  }

  fun textField(prop: KMutableProperty0<String>, columns: Int? = null): CellBuilder<JTextField> {
    val component = JTextField(prop.get(),columns ?: 0)
    val builder = component()
    return builder.withBinding(component::getText, component::setText, prop.getter, prop.setter)
  }

  fun intTextField(prop: KMutableProperty0<Int>, columns: Int? = null, range: UINumericRange? = null): CellBuilder<JTextField> {
    return textField(
      { prop.get().toString() },
      { value -> value.toIntOrNull()?.let { prop.set(range?.fit(it) ?: it) } },
      columns
    )
  }

  fun textField(getter: () -> String, setter: (String) -> Unit, columns: Int? = null): CellBuilder<JTextField> {
    val component = JTextField(getter(),columns ?: 0)
    val builder = component()
    return builder.withBinding(component::getText, component::setText, getter, setter)
  }

  fun spinner(prop: KMutableProperty0<Int>, minValue: Int, maxValue: Int, step: Int = 1): CellBuilder<JBIntSpinner> {
    val component = JBIntSpinner(prop.get(), minValue, maxValue, step)
    return component().withBinding(component::getNumber, component::setNumber, prop.getter, prop.setter)
  }

  fun textFieldWithHistoryWithBrowseButton(browseDialogTitle: String,
                                           value: String? = null,
                                           project: Project? = null,
                                           fileChooserDescriptor: FileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(),
                                           historyProvider: (() -> List<String>)? = null,
                                           fileChosen: ((chosenFile: VirtualFile) -> String)? = null,
                                           comment: String? = null): TextFieldWithHistoryWithBrowseButton {
    val component = textFieldWithHistoryWithBrowseButton(project, browseDialogTitle, fileChooserDescriptor, historyProvider, fileChosen)
    value?.let { component.text = it }
    component(comment = comment)
    return component
  }

  fun textFieldWithBrowseButton(browseDialogTitle: String,
                                value: String? = null,
                                project: Project? = null,
                                fileChooserDescriptor: FileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(),
                                fileChosen: ((chosenFile: VirtualFile) -> String)? = null,
                                comment: String? = null): TextFieldWithBrowseButton {
    val component = textFieldWithBrowseButton(project, browseDialogTitle, fileChooserDescriptor, fileChosen)
    value?.let { component.text = it }
    component(comment = comment)
    return component
  }

  fun gearButton(vararg actions: AnAction) {
    val label = JLabel(AllIcons.General.GearPlain)
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

    label()
  }

  /**
   * @see LayoutBuilder.titledRow
   */
  fun panel(title: String, wrappedComponent: Component, vararg constraints: CCFlags) {
    val panel = Panel(title)
    panel.add(wrappedComponent)
    panel(*constraints)
  }

  fun scrollPane(component: Component, vararg constraints: CCFlags) {
    JBScrollPane(component)(*constraints)
  }

  abstract operator fun <T : JComponent> T.invoke(
    vararg constraints: CCFlags,
    gapLeft: Int = 0,
    growPolicy: GrowPolicy? = null,
    comment: String? = null
  ): CellBuilder<T>

}

fun <T> listCellRenderer(renderer: ColoredListCellRenderer<T?>.(value: T, index: Int, isSelected: Boolean) -> Unit): ColoredListCellRenderer<T?> {
  return object : ColoredListCellRenderer<T?>() {
    override fun customizeCellRenderer(list: JList<out T?>, value: T?, index: Int, selected: Boolean, hasFocus: Boolean) {
      if (value != null) {
        renderer(this, value, index, selected)
      }
    }
  }
}
