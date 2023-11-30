// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.settingChooser

import com.intellij.ide.startup.importSettings.data.ChildSetting
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.util.minimumWidth
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

class ChildSettingsList(val settings: List<ChildItem>, configurable: Boolean, changeHandler: () -> Unit) : JBList<ChildItem>(createDefaultListModel(settings)) {
  companion object {
    const val SCROLL_PANE_INSETS = 7
  }


  init {
    cellRenderer = CBRenderer(configurable)

    if (configurable) {
      addMouseListener(object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent) {
          val index = locationToIndex(e.point)
          if (settings.size > index) {
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
  private lateinit var ch: JBCheckBox
  private lateinit var txt: JEditorPane
  private lateinit var addTxt: JEditorPane
  private lateinit var rightTxt: JEditorPane

  private val separator = SeparatorComponent(5, JBUI.CurrentTheme.Popup.separatorColor(), null)

  private val hg = 3
  private val wg = 5

  private val gaps = UnscaledGaps(hg, wg, hg, wg)

  val line = panel {
    row {
      ch = checkBox("").customize(gaps).component
      panel {
        row {
          txt = text("").customize(gaps).component.apply {
            minimumWidth = JBUI.scale(30)
          }
          addTxt = comment("").resizableColumn().customize(gaps).component
        }
      }.resizableColumn()
      rightTxt = comment("").customize(UnscaledGaps(hg, wg, hg, wg + ChildSettingsList.SCROLL_PANE_INSETS)).component
    }
  }.apply {
    minimumWidth = JBUI.scale(300)
  }

  val pane = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    add(separator)
    add(line)
  }

  override fun getListCellRendererComponent(list: JList<out ChildItem>,
                                            value: ChildItem,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    separator.isVisible = value.separatorNeeded
    val child = value.child

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
