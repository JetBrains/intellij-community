/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

  fun chooseFile(descriptor: FileChooserDescriptor, event: AnActionEvent, fileChoosen: (chosenFile: VirtualFile) -> Unit) {
    FileChooser.chooseFile(descriptor, event.getData(PlatformDataKeys.PROJECT), event.getData(PlatformDataKeys.CONTEXT_COMPONENT), null, fileChoosen)
  }
}