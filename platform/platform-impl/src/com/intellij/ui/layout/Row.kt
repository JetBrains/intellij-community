/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import com.intellij.ui.components.Label
import com.intellij.ui.components.Link
import com.intellij.ui.components.Panel
import com.intellij.ui.components.textFieldWithHistoryWithBrowseButton
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.UIUtil.ComponentStyle
import com.intellij.util.ui.UIUtil.FontColor
import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel

abstract class Row() {
  abstract var enabled: Boolean

  abstract var visible: Boolean

  abstract var subRowsEnabled: Boolean

  abstract var subRowsVisible: Boolean

  abstract val subRows: List<Row>

  protected abstract val builder: LayoutBuilderImpl

  fun label(text: String, gapLeft: Int = 0, style: ComponentStyle? = null, fontColor: FontColor? = null, bold: Boolean = false) {
    Label(text, style, fontColor, bold)(gapLeft = gapLeft)
  }

  fun link(text: String, style: ComponentStyle? = null, action: () -> Unit) {
    val result = Link(text, action = action)
    style?.let { UIUtil.applyStyle(it, result) }
    result()
  }

  fun button(text: String, actionListener: (event: ActionEvent) -> Unit) {
    val button = JButton(BundleBase.replaceMnemonicAmpersand(text))
    button.addActionListener(actionListener)
    button()
  }

  fun textFieldWithBrowseButton(browseDialogTitle: String,
                                value: String? = null,
                                project: Project? = null,
                                fileChooserDescriptor: FileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(),
                                historyProvider: (() -> List<String>)? = null,
                                fileChoosen: ((chosenFile: VirtualFile) -> String)? = null): TextFieldWithHistoryWithBrowseButton {
    val component = textFieldWithHistoryWithBrowseButton(project, browseDialogTitle, fileChooserDescriptor, historyProvider, fileChoosen)
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

  fun hint(text: String) {
    label(text, style = ComponentStyle.SMALL, fontColor = FontColor.BRIGHTER, gapLeft = 3 * HORIZONTAL_GAP)
  }

  fun panel(title: String, wrappedComponent: Component, vararg constraints: CCFlags) {
    val panel = Panel(title)
    panel.add(wrappedComponent)
    panel(*constraints)
  }

  abstract operator fun JComponent.invoke(vararg constraints: CCFlags, gapLeft: Int = 0, growPolicy: GrowPolicy? = null)

  inline fun right(init: Row.() -> Unit) {
    alignRight()
    init()
  }

  protected abstract fun alignRight()

  inline fun row(label: String, init: Row.() -> Unit): Row {
    val row = createRow(label)
    row.init()
    return row
  }


  inline fun row(init: Row.() -> Unit): Row {
    val row = createRow(null)
    row.init()
    return row
  }

  protected abstract fun createRow(label: String?): Row

  @Deprecated(message = "Nested row is prohibited", level = DeprecationLevel.ERROR)
  fun row(label: JLabel? = null, init: Row.() -> Unit) {
  }

  @Deprecated(message = "Nested noteRow is prohibited", level = DeprecationLevel.ERROR)
  fun noteRow(text: String) {
  }
}

enum class GrowPolicy {
  SHORT_TEXT, MEDIUM_TEXT
}