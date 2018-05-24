// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ClickListener
import com.intellij.ui.TextFieldWithHistoryWithBrowseButton
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.Link
import com.intellij.ui.components.Panel
import com.intellij.ui.components.textFieldWithHistoryWithBrowseButton
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel

@DslMarker
annotation class CellMarker

// separate class to avoid row related methods in the `cell { } `
@CellMarker
abstract class Cell {
  /**
   * Sets how keen the component should be to grow in relation to other component **in the same cell**. Use `push` in addition if need.
   * If this constraint is not set the grow weight is set to 0 and the component will not grow (unless some automatic rule is not applied (see [com.intellij.ui.layout.panel])).
   * Grow weight will only be compared against the weights for the same cell.
   */
  val growX: CCFlags = CCFlags.growX
  @Suppress("unused")
  val growY: CCFlags = CCFlags.growY
  val grow: CCFlags = CCFlags.grow

  /**
   * Makes the column that the component is residing in grow with `weight`.
   */
  val pushX: CCFlags = CCFlags.pushX

  /**
   * Makes the row that the component is residing in grow with `weight`.
   */
  @Suppress("unused")
  val pushY: CCFlags = CCFlags.pushY
  val push: CCFlags = CCFlags.push

  fun link(text: String, style: UIUtil.ComponentStyle? = null, action: () -> Unit) {
    val result = Link(text, action = action)
    style?.let { UIUtil.applyStyle(it, result) }
    result()
  }

  fun button(text: String, vararg constraints: CCFlags, actionListener: (event: ActionEvent) -> Unit) {
    val button = JButton(BundleBase.replaceMnemonicAmpersand(text))
    button.addActionListener(actionListener)
    button(*constraints)
  }

  fun checkBox(text: String, isSelected: Boolean = false, vararg constraints: CCFlags, actionListener: (event: ActionEvent, component: JCheckBox) -> Unit) {
    val component = JCheckBox(text)
    component.isSelected = isSelected
    component.addActionListener(ActionListener { actionListener(it, component) })
    component(*constraints)
  }

  fun textFieldWithBrowseButton(browseDialogTitle: String,
                                value: String? = null,
                                project: Project? = null,
                                fileChooserDescriptor: FileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(),
                                historyProvider: (() -> List<String>)? = null,
                                fileChosen: ((chosenFile: VirtualFile) -> String)? = null): TextFieldWithHistoryWithBrowseButton {
    val component = textFieldWithHistoryWithBrowseButton(project, browseDialogTitle, fileChooserDescriptor, historyProvider, fileChosen)
    value?.let { component.text = it }
    component()
    return component
  }

  fun gearButton(vararg actions: AnAction) {
    val label = JLabel()
    label.icon = AllIcons.General.Gear

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

  fun panel(title: String, wrappedComponent: Component, vararg constraints: CCFlags) {
    val panel = Panel(title)
    panel.add(wrappedComponent)
    panel(*constraints)
  }

  fun scrollPane(component: Component, vararg constraints: CCFlags) {
    JBScrollPane(component)(*constraints)
  }

  abstract operator fun JComponent.invoke(vararg constraints: CCFlags, gapLeft: Int = 0, growPolicy: GrowPolicy? = null, comment: String? = null)
}