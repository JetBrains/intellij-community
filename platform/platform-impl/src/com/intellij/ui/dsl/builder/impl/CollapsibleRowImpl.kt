// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.UiSwitcher
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.TextWithMnemonic
import com.intellij.ui.Expandable
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.Gaps
import org.jetbrains.annotations.ApiStatus
import java.awt.Font
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder

@ApiStatus.Internal
internal class CollapsibleRowImpl(dialogPanelConfig: DialogPanelConfig,
                                  panelContext: PanelContext,
                                  parent: PanelImpl,
                                  @NlsContexts.BorderTitle title: String,
                                  init: Panel.() -> Unit) :
  RowImpl(dialogPanelConfig, panelContext, parent, RowLayout.INDEPENDENT), CollapsibleRow {

  private val collapsibleTitledSeparator = CollapsibleTitledSeparatorImpl(title)
  private var registeredKeyStroke: KeyStroke? = null

  override var expanded by collapsibleTitledSeparator::expanded

  override var packWindowHeight = false

  override fun setTitle(title: String) {
    collapsibleTitledSeparator.text = title
    updateMnemonicRegistration()
  }

  override fun setTitleFont(font: Font) {
    collapsibleTitledSeparator.titleFont = font
  }

  override fun addExpandedListener(action: (Boolean) -> Unit) {
    collapsibleTitledSeparator.expandedProperty.afterChange { action(it) }
  }

  init {
    collapsibleTitledSeparator.setLabelFocusable(true)
    (collapsibleTitledSeparator.label.border as? EmptyBorder)?.borderInsets?.let {
      collapsibleTitledSeparator.putClientProperty(DslComponentProperty.VISUAL_PADDINGS,
                                                   Gaps(top = it.top, left = it.left, bottom = it.bottom))
    }

    collapsibleTitledSeparator.label.putClientProperty(Expandable::class.java, object : Expandable {
      override fun expand() {
        expanded = true
      }

      override fun collapse() {
        expanded = false
      }

      override fun isExpanded(): Boolean {
        return expanded
      }
    })

    val action = DumbAwareAction.create { expanded = !expanded }
    action.registerCustomShortcutSet(ActionUtil.getShortcutSet("CollapsiblePanel-toggle"), collapsibleTitledSeparator.label)

    val collapsibleTitledSeparator = this.collapsibleTitledSeparator
    lateinit var expandablePanel: Panel
    panel {
      row {
        cell(collapsibleTitledSeparator).align(AlignX.FILL)
      }
      row {
        expandablePanel = panel(init).align(AlignY.FILL)
      }.resizableRow()
      collapsibleTitledSeparator.onAction {
        expandablePanel.visible(it)
      }
    }.align(AlignY.FILL)
    applyUiSwitcher(expandablePanel as PanelImpl, CollapsibleRowUiSwitcher(this))

    collapsibleTitledSeparator.actionMap.put(ACTION_KEY, object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        expanded = !expanded
      }
    })
    updateMnemonicRegistration()

    addExpandedListener {
      packWindowHeight()
    }
  }

  private fun packWindowHeight() {
    if (packWindowHeight && collapsibleTitledSeparator.isShowing) {
      SwingUtilities.invokeLater {
        val window = SwingUtilities.windowForComponent(collapsibleTitledSeparator)
        if (window != null && window.isShowing && collapsibleTitledSeparator.isShowing) {
          val height = window.preferredSize.height
          window.setSize(window.width, height)
        }
      }
    }
  }

  private fun updateMnemonicRegistration() {
    val inputMap = collapsibleTitledSeparator.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
    registeredKeyStroke?.let { inputMap.remove(it) }

    val text = TextWithMnemonic.parse(collapsibleTitledSeparator.text)
    val mnemonic = text.mnemonicCode
    if (mnemonic == KeyEvent.VK_UNDEFINED) {
      registeredKeyStroke = null
    }
    else {
      registeredKeyStroke = KeyStroke.getKeyStroke(mnemonic, InputEvent.ALT_DOWN_MASK, false)
      inputMap.put(registeredKeyStroke, ACTION_KEY)
    }
  }

  private fun applyUiSwitcher(panel: PanelImpl, uiSwitcher: UiSwitcher) {
    for (row in panel.rows) {
      for (cell in row.cells) {
        when (cell) {
          is CellImpl<*> -> UiSwitcher.append(cell.viewComponent, uiSwitcher)
          is PanelImpl -> applyUiSwitcher(cell, uiSwitcher)
          else -> {}
        }
      }
    }
  }

  private class CollapsibleRowUiSwitcher(private val collapsibleRow: CollapsibleRowImpl) : UiSwitcher {

    override fun show(): Boolean {
      collapsibleRow.expanded = true
      return true
    }
  }
}

private const val ACTION_KEY = "[CollapsibleRow] Collapse/Expand on mnemonic"
