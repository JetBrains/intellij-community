// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.impl

import com.intellij.BundleBase
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.CellBuilder
import com.intellij.ui.dsl.CellBuilderBase
import com.intellij.ui.dsl.PanelBuilder
import com.intellij.ui.dsl.RowBuilder
import org.jetbrains.annotations.ApiStatus
import java.awt.event.ActionEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel

@ApiStatus.Experimental
internal class RowBuilderImpl(private val dialogPanelConfig: DialogPanelConfig, val label: JLabel?) : RowBuilder {

  var independent = false
    private set

  val cells: List<CellBuilderBase<*>>
    get() = _cells

  private val _cells = mutableListOf<CellBuilderBase<*>>()

  init {
    label?.let { cell(it) }
  }

  override fun independent(): RowBuilder {
    independent = true
    return this
  }

  override fun <T : JComponent> cell(component: T): CellBuilder<T> {
    val result = CellBuilderImpl(dialogPanelConfig, component)
    _cells.add(result)
    return result
  }

  override fun panel(init: PanelBuilder.() -> Unit): PanelBuilder {
    val result = PanelBuilderImpl(dialogPanelConfig)
    result.init()
    _cells.add(result)
    return result
  }

  override fun checkBox(@NlsContexts.Checkbox text: String): CellBuilder<JBCheckBox> {
    return cell(JBCheckBox(text))
  }

  override fun button(@NlsContexts.Button text: String, actionListener: (event: ActionEvent) -> Unit): CellBuilder<JButton> {
    val button = JButton(BundleBase.replaceMnemonicAmpersand(text))
    button.addActionListener(actionListener)
    return cell(button)
  }
}
