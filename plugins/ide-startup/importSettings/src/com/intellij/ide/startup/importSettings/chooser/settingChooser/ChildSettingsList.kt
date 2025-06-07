// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.settingChooser

import com.intellij.ide.startup.importSettings.data.ChildSetting
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.AccessibleContextDelegate
import java.awt.Component
import java.awt.Container
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.accessibility.AccessibleContext
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

class ChildSettingsList(val settings: List<ChildItem>, configurable: Boolean, changeHandler: () -> Unit) : JBList<ChildItem>(createDefaultListModel(settings)) {

  var enterHandler: (() -> Unit)? = null

  init {
    cellRenderer = CBRenderer(configurable)

    addKeyListener(object : KeyAdapter() {
      override fun keyPressed(e: KeyEvent?) {
        when (e?.keyCode) {
          KeyEvent.VK_ENTER,
          KeyEvent.VK_SPACE -> {
            if (configurable) {
              for (index in selectedIndices) {
                if (index >= 0 && settings.size > index) {
                  val settingItem = settings[index]
                  settingItem.selected = !settingItem.selected

                  repaint()
                  changeHandler()
                }
              }
              e.consume()
            } else {
              enterHandler?.invoke()
            }

          }
        }
      }
    })


    if (configurable) {
      addMouseListener(object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent) {
          val index = locationToIndex(e.point)
          if (index >= 0 && settings.size > index) {
            val settingItem = settings[index]
            settingItem.selected = !settingItem.selected
            repaint()
            changeHandler()
          }
        }
      })
    }
  }
}

private class CBRenderer(val configurable: Boolean) : ListCellRenderer<ChildItem> {
  private val hg = 3
  private val wg = 5

  private var ch = JBCheckBox().apply {
    isOpaque = false
  }
  private var txt = JLabel().apply {
    border = JBUI.Borders.emptyLeft(wg)
  }

  private var addTxt = JLabel().apply {
    foreground = UIUtil.getContextHelpForeground()
  }

  private var rightTxt = JLabel().apply {
    foreground = UIUtil.getContextHelpForeground()
  }

  private val separator = SeparatorComponent(5, JBUI.CurrentTheme.Popup.separatorColor(), null)

  val line = JPanel(GridBagLayout()).apply {
    val constraint = GridBagConstraints()

    constraint.anchor = GridBagConstraints.BASELINE
    constraint.weightx = 0.0
    constraint.weighty = 1.0
    constraint.gridx = 0
    constraint.gridy = 0
    constraint.fill = GridBagConstraints.HORIZONTAL
    constraint.ipadx = 10
    add(ch, constraint)

    constraint.weightx = 0.0
    constraint.weighty = 1.0
    constraint.gridx = 1
    constraint.gridy = 0

    add(txt, constraint)

    constraint.weightx = 2.0
    constraint.weighty = 1.0
    constraint.gridx = 2
    constraint.gridy = 0
    add(addTxt, constraint)

    constraint.weightx = 0.0
    constraint.weighty = 1.0
    constraint.gridx = 3
    constraint.gridy = 0

    add(rightTxt, constraint)

    border = JBUI.Borders.empty(1, wg, 0, wg)
  }

  val pane = object : JPanel() {
    override fun getAccessibleContext(): AccessibleContext? {
      if (accessibleContext == null) {
        accessibleContext = object : AccessibleContextDelegate(ch.accessibleContext) {
          override fun getDelegateParent(): Container? = parent
        }
      }
      return accessibleContext
    }
  }.apply {
    layout = GridBagLayout()
    val constraint = GridBagConstraints()
    constraint.weightx = 1.0
    constraint.weighty = 1.0
    constraint.gridx = 0
    constraint.gridy = 0
    constraint.fill = GridBagConstraints.HORIZONTAL
    add(separator, constraint)
    constraint.gridx = 0
    constraint.gridy = 1
    add(line, constraint)
    border = JBUI.Borders.emptyTop(hg)
  }

  override fun getListCellRendererComponent(list: JList<out ChildItem>,
                                            value: ChildItem,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    separator.isVisible = value.separatorNeeded
    val child = value.child

    if (configurable) {
      line.background = if (isSelected) list.selectionBackground else list.background
      val color = if (isSelected) list.selectionForeground else list.foreground

      ch.foreground = color
      txt.foreground = color
    }

    ch.isVisible = configurable
    ch.isSelected = value.selected
    ch.text = child.name

    txt.isVisible = !configurable
    txt.text = child.name
    addTxt.text = child.leftComment ?: ""

    rightTxt.isVisible = child.rightComment?.let {
      rightTxt.text = it
      true
    } ?: false

    return pane
  }
}

data class ChildItem(val child: ChildSetting, var separatorNeeded: Boolean = false, var selected: Boolean = true)
