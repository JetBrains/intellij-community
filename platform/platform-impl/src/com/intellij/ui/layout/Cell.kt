// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.BundleBase
import com.intellij.icons.AllIcons
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
import com.intellij.ui.ClickListener
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.ListCellRendererWrapper
import com.intellij.ui.TextFieldWithHistoryWithBrowseButton
import com.intellij.ui.components.*
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.MouseEvent
import java.util.function.Consumer
import java.util.function.Supplier
import javax.swing.*
import kotlin.reflect.KMutableProperty0

@DslMarker
annotation class CellMarker

interface CellBuilder<T : JComponent> {
  fun focused(): CellBuilder<T>
  fun withValidation(callback: (T) -> ValidationInfo?): CellBuilder<T>
  fun onApply(callback: () -> Unit): CellBuilder<T>

  fun withErrorIf(message: String, callback: (T) -> Boolean): CellBuilder<T> {
    withValidation { if (callback(it)) ValidationInfo(message, it) else null }
    return this
  }
}

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
                      propertyUiManager: BooleanPropertyUiManager? = null,
                      vararg constraints: CCFlags,
                      crossinline actionListener: (event: ActionEvent, component: JCheckBox) -> Unit): JCheckBox {
    val component = checkBox(text, isSelected, comment, propertyUiManager, *constraints)
    component.addActionListener(ActionListener {
      actionListener(it, component)
    })
    return component
  }

  @JvmOverloads
  fun checkBox(text: String, isSelected: Boolean = false, comment: String? = null, propertyUiManager: BooleanPropertyUiManager? = null, vararg constraints: CCFlags = emptyArray()): JCheckBox {
    val component = JCheckBox(text)
    component.isSelected = isSelected
    propertyUiManager?.registerCheckBox(component)
    component(*constraints, comment = comment)
    return component
  }

  inline fun <T> comboBox(propertyUiManager: BooleanPropertyWithListUiManager<T, out ComboBoxModel<T>>, growPolicy: GrowPolicy? = null, crossinline renderer: ListCellRendererWrapper<T?>.(value: T, index: Int, isSelected: Boolean) -> Unit) {
    comboBox(propertyUiManager.listModel, propertyUiManager, growPolicy, object : ListCellRendererWrapper<T?>() {
      override fun customize(list: JList<*>, value: T?, index: Int, isSelected: Boolean, hasFocus: Boolean) {
        if (value != null) {
          renderer(value, index, isSelected)
        }
      }
    })
  }

  fun <T> comboBox(model: ComboBoxModel<T>, propertyUiManager: BooleanPropertyWithListUiManager<*, *>? = null, growPolicy: GrowPolicy? = null, renderer: ListCellRenderer<T?>? = null) {
    val component = ComboBox(model)
    propertyUiManager?.manage(component)
    if (renderer != null) {
      component.renderer = renderer
    }
    component(growPolicy = growPolicy)
  }

  fun textField(prop: KMutableProperty0<String>, columns: Int? = null): CellBuilder<JTextField> {
    val component = JTextField(prop.get(),columns ?: 0)
    val builder = component()
    builder.onApply { prop.set(component.text) }
    return builder
  }

  fun intTextField(prop: KMutableProperty0<Int>, columns: Int? = null): CellBuilder<JTextField> {
    return textField(Supplier { prop.get().toString() }, Consumer { value -> value.toIntOrNull()?.let { prop.set(it) } }, columns)
  }

  fun textField(getter: Supplier<String>, setter: Consumer<String>, columns: Int? = null): CellBuilder<JTextField> {
    val component = JTextField(getter.get(),columns ?: 0)
    val builder = component()
    builder.onApply { setter.accept(component.text) }
    return builder
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
    object : ClickListener() {
      override fun onClick(e: MouseEvent, clickCount: Int): Boolean {
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

  abstract operator fun <T : JComponent> T.invoke(vararg constraints: CCFlags, gapLeft: Int = 0, growPolicy: GrowPolicy? = null, comment: String? = null): CellBuilder<T>
}