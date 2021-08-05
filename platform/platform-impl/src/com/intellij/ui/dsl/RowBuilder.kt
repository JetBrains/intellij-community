// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl

import com.intellij.BundleBase
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.JBCheckBox
import org.jetbrains.annotations.ApiStatus
import java.awt.event.ActionEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel

@DslMarker
private annotation class RowBuilderMarker

@ApiStatus.Experimental
@RowBuilderMarker
class RowBuilder(val builder: PanelBuilder, val label: JLabel?) {

  internal val cells = mutableListOf<CellBuilder<JComponent>>()

  init {
    label?.let { cell(it) }
  }

  fun <T : JComponent> cell(component: T): CellBuilder<T> {
    val result = CellBuilder(builder, component)
    cells.add(result)
    return result
  }

  fun checkBox(@NlsContexts.Checkbox text: String): CellBuilder<JBCheckBox> {
    return cell(JBCheckBox(text))
  }

  fun button(@NlsContexts.Button text: String, actionListener: (event: ActionEvent) -> Unit): CellBuilder<JButton> {
    val button = JButton(BundleBase.replaceMnemonicAmpersand(text))
    button.addActionListener(actionListener)
    return cell(button)
  }
}
