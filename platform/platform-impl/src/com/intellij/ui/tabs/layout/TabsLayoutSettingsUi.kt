// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.layout

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.layout.*
import com.intellij.ui.tabs.impl.tabsLayout.TabsLayoutInfo
import javax.swing.ComboBoxModel
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.ListCellRenderer
import javax.swing.SwingConstants.TOP

class TabsLayoutSettingsUi {
  companion object {
    private val LOG = Logger.getInstance(TabsLayoutSettingsUi::class.java)

    internal fun tabsLayoutComboBox(tabPlacementComboBoxModel: DefaultComboBoxModel<Int>): ComboBox<TabsLayoutInfo> {
      val model = DefaultComboBoxModel<TabsLayoutInfo>(TabsLayoutSettingsHolder.instance.installedInfos.toTypedArray())
      val comboBox = comboBox(model,
                              SimpleListCellRenderer.create<TabsLayoutInfo> { label, value, _ ->
                                label.text = value.name
                              })
      comboBox.addActionListener {
        val selectedInfo = getSelectedInfo(comboBox)
        if (selectedInfo == null) {
          tabPlacementComboBoxModel.removeAllElements()
          tabPlacementComboBoxModel.addElement(TOP);
          return@addActionListener
        }

        var availableTabsPositions: Array<Int>? = selectedInfo.getAvailableTabsPositions();
        if (availableTabsPositions == null || availableTabsPositions.isEmpty()) {
          tabPlacementComboBoxModel.removeAllElements()
          tabPlacementComboBoxModel.addElement(TOP);
          return@addActionListener
        }

        var needToResetSelected = true
        var selectedValue: Int? = null
        if (tabPlacementComboBoxModel.selectedItem is Int) {
          var prevSelectedValue = tabPlacementComboBoxModel.selectedItem as Int
          if (prevSelectedValue in availableTabsPositions) {
            selectedValue = prevSelectedValue;
            needToResetSelected = false;
          }
        }

        if (needToResetSelected) {
          selectedValue = availableTabsPositions[0];
        }

        tabPlacementComboBoxModel.removeAllElements();
        for (value in availableTabsPositions) {
          tabPlacementComboBoxModel.addElement(value);
        }
        tabPlacementComboBoxModel.selectedItem = selectedValue;
      }
      return comboBox
    }

    private fun <T> comboBox(model: ComboBoxModel<T>,
                              renderer: ListCellRenderer<T?>? = null): ComboBox<T> {
      val component = ComboBox(model)
      if (renderer != null) {
        component.renderer = renderer
      }
      else {
        component.renderer = SimpleListCellRenderer.create("") { it.toString() }
      }
      return component
    }

    fun prepare(builder: CellBuilder<JComboBox<TabsLayoutInfo>>,
                comboBox: JComboBox<TabsLayoutInfo>) {

      builder.onApply {
        getSelectedInfo(comboBox)?.let{
          UISettings.instance.selectedTabsLayoutInfoId = it.id
        }
      }

      builder.onReset {
        val savedSelectedInfoId = calculateSavedSelectedInfoId()
        val selectedInfo = getSelectedInfo(comboBox)
        val isWrongSelected = selectedInfo == null || selectedInfo.id != UISettings.instance.selectedTabsLayoutInfoId
        for (info in TabsLayoutSettingsHolder.instance.installedInfos) {
          if (isWrongSelected && info.id == savedSelectedInfoId) {
            comboBox.selectedItem = info
          }
        }
      }

      builder.onIsModified {
        val savedSelectedInfoId = calculateSavedSelectedInfoId()
        val selectedInfo = getSelectedInfo(comboBox)
        if (selectedInfo != null && selectedInfo.id != savedSelectedInfoId) {
          return@onIsModified true
        }
        return@onIsModified false
      }
    }

    private fun calculateSavedSelectedInfoId(): String? {
      var savedSelectedInfoId = UISettings.instance.selectedTabsLayoutInfoId
      if (StringUtil.isEmpty(savedSelectedInfoId)) {
        savedSelectedInfoId = TabsLayoutSettingsHolder.instance.defaultInfo.id
      }
      return savedSelectedInfoId
    }

    fun getSelectedInfo(comboBox: JComboBox<TabsLayoutInfo>) : TabsLayoutInfo? {
      val selectedInfo = comboBox.selectedItem
      selectedInfo?.let {
        return selectedInfo as TabsLayoutInfo
      }
      return null
    }
  }
}