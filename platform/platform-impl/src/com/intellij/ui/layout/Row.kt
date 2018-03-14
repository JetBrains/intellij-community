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

internal const val COMPONENT_TAG_HINT = "kotlin.dsl.hint.component"

abstract class Row {
  abstract var enabled: Boolean

  abstract var visible: Boolean

  abstract var subRowsEnabled: Boolean

  abstract var subRowsVisible: Boolean

  abstract val subRows: List<Row>

  protected abstract val builder: LayoutBuilderImpl

  fun label(text: String, gapLeft: Int = 0, style: ComponentStyle? = null, fontColor: FontColor? = null, bold: Boolean = false): JLabel {
    val label = Label(text, style, fontColor, bold)
    label(gapLeft = gapLeft)
    return label
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

  fun hint(text: String) {
    val component = label(text, style = ComponentStyle.SMALL, fontColor = FontColor.BRIGHTER)
    component.putClientProperty(COMPONENT_TAG_HINT, true)
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

  @PublishedApi
  internal abstract fun alignRight()

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

  @PublishedApi
  internal abstract fun createRow(label: String?): Row

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