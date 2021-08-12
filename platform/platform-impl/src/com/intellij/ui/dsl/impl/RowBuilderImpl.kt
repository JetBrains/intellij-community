// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.impl

import com.intellij.BundleBase
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.UIBundle
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.Label
import com.intellij.ui.dsl.*
import com.intellij.util.MathUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*

@ApiStatus.Experimental
internal class RowBuilderImpl(private val dialogPanelConfig: DialogPanelConfig,
                              private val indentCount: Int,
                              val label: JLabel? = null) : RowBuilder {

  var rowLayout = if (label == null) RowLayout.INDEPENDENT else RowLayout.LABEL_ALIGNED
    private set

  var comment: JComponent? = null
    private set

  var topGap: TopGap? = null
    private set

  val cells: List<CellBuilderBaseImpl<*>>
    get() = _cells

  private val _cells = mutableListOf<CellBuilderBaseImpl<*>>()

  init {
    label?.let { cell(it) }
  }

  override fun layout(rowLayout: RowLayout): RowBuilderImpl {
    this.rowLayout = rowLayout
    return this
  }

  override fun comment(comment: String, maxLineLength: Int): RowBuilderImpl {
    this.comment = ComponentPanelBuilder.createCommentComponent(comment, true, maxLineLength, true)
    return this
  }

  override fun <T : JComponent> cell(component: T): CellBuilderImpl<T> {
    val result = CellBuilderImpl(dialogPanelConfig, component)
    _cells.add(result)
    return result
  }

  override fun visible(isVisible: Boolean): RowBuilderImpl {
    _cells.forEach {
      when (it) {
        is CellBuilderImpl<*> -> it.visibleFromParent(isVisible)
        is PanelBuilderImpl -> it.visible(isVisible)
      }
    }
    comment?.let { it.isVisible = isVisible }
    return this
  }

  override fun gap(topGap: TopGap): RowBuilderImpl {
    this.topGap = topGap
    return this
  }

  override fun panel(init: PanelBuilder.() -> Unit): PanelBuilderImpl {
    val result = PanelBuilderImpl(dialogPanelConfig)
    result.init()
    _cells.add(result)
    return result
  }

  override fun checkBox(@NlsContexts.Checkbox text: String): CellBuilderImpl<JBCheckBox> {
    return cell(JBCheckBox(text))
  }

  override fun button(@NlsContexts.Button text: String, actionListener: (event: ActionEvent) -> Unit): CellBuilderImpl<JButton> {
    val button = JButton(BundleBase.replaceMnemonicAmpersand(text))
    button.addActionListener(actionListener)
    return cell(button)
  }

  override fun actionButton(action: AnAction, dimension: Dimension): CellBuilderImpl<ActionButton> {
    val component = ActionButton(action, action.templatePresentation, ActionPlaces.UNKNOWN, dimension)
    return cell(component)
  }

  override fun label(text: String): CellBuilderImpl<JLabel> {
    return cell(Label(text))
  }

  override fun textField(columns: Int): CellBuilderImpl<JBTextField> {
    return cell(JBTextField(columns))
  }

  override fun intTextField(columns: Int, range: IntRange?, keyboardStep: Int?): CellBuilderImpl<JBTextField> {
    val result = textField(columns)
      .onValidationOnInput {
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

  override fun <T> comboBox(model: ComboBoxModel<T>, renderer: ListCellRenderer<T?>?): CellBuilder<ComboBox<T>> {
    val component = ComboBox(model)
    component.renderer = renderer ?: SimpleListCellRenderer.create("") { it.toString() }
    return cell(component)
  }

  fun getIndent(): Int {
    return indentCount * dialogPanelConfig.spacing.horizontalIndent
  }
}
