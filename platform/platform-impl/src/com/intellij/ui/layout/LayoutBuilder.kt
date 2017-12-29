/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ui.layout

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.Label
import java.awt.event.ActionListener
import javax.swing.ButtonGroup
import javax.swing.JLabel

class LayoutBuilder(val `$`: LayoutBuilderImpl, val buttonGroup: ButtonGroup? = null) {
  inline fun row(label: String, init: Row.() -> Unit) = row(label = Label(label), init = init)

  inline fun row(label: JLabel? = null, separated: Boolean = false, init: Row.() -> Unit): Row {
    val row = `$`.newRow(label, buttonGroup, separated)
    row.init()
    return row
  }

  /**
   * Hyperlinks are supported (`<a href=""></a>`), new lines and <br> are supported only if no links (file issue if need).
   */
  fun noteRow(text: String) {
    `$`.noteRow(text)
  }

  inline fun buttonGroup(init: LayoutBuilder.() -> Unit) {
    LayoutBuilder(`$`, ButtonGroup()).init()
  }

  inline fun buttonGroup(crossinline elementActionListener: () -> Unit, init: LayoutBuilder.() -> Unit): ButtonGroup {
    val group = ButtonGroup()
    LayoutBuilder(`$`, group).init()

    val listener = ActionListener { elementActionListener() }
    for (button in group.elements) {
      button.addActionListener(listener)
    }
    return group
  }

  fun chooseFile(descriptor: FileChooserDescriptor, event: AnActionEvent, fileChosen: (chosenFile: VirtualFile) -> Unit) {
    FileChooser.chooseFile(descriptor, event.getData(PlatformDataKeys.PROJECT), event.getData(PlatformDataKeys.CONTEXT_COMPONENT), null, fileChosen)
  }
}