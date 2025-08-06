// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.plugins.PluginHeaderPanel
import com.intellij.ide.plugins.PluginManagerCore.getPlugin
import com.intellij.ide.plugins.newui.MyPluginModel
import com.intellij.ide.plugins.newui.PluginDetailsPageComponent
import com.intellij.ide.plugins.newui.PluginModelFacade
import com.intellij.ide.plugins.newui.PluginUiModelAdapter
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.ui.*
import com.intellij.ui.components.labels.LinkListener
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JTable

internal class DetectedPluginsPanel(project: Project?) : OrderPanel<PluginDownloader>(PluginDownloader::class.java) {
  private val myDetailsComponent: PluginDetailsPageComponent
  private val myHeader = PluginHeaderPanel()
  private val mySkippedPlugins = HashSet<PluginId>()

  init {
    val pluginModel = MyPluginModel(project)
    myDetailsComponent = PluginDetailsPageComponent(PluginModelFacade(pluginModel), LinkListener { _, _ -> }, true)
    val entryTable = getEntryTable()
    entryTable.setTableHeader(null)
    entryTable.setDefaultRenderer(PluginDownloader::class.java, object : ColoredTableCellRenderer() {
      override fun customizeCellRenderer(
        table: JTable,
        value: Any?,
        selected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int,
      ) {
        setBorder(null)
        if (value !is PluginDownloader) {
          return
        }

        val pluginName = value.pluginName
        append(pluginName, SimpleTextAttributes.REGULAR_ATTRIBUTES)

        val installedPlugin = getPlugin(value.id)

        val oldPluginName = installedPlugin?.getName()
        if (oldPluginName != null && !Comparing.strEqual(pluginName, oldPluginName)) {
          append(" - $oldPluginName", SimpleTextAttributes.REGULAR_ATTRIBUTES)
        }

        val installedVersion = installedPlugin?.getVersion()
        val availableVersion = value.pluginVersion
        val version = if (installedVersion != null && availableVersion != null) {
          StringUtil.join(arrayOf(installedVersion, UIUtil.rightArrow(), availableVersion), "")
        }
        else StringUtil.defaultIfEmpty(installedVersion, availableVersion)

        if (StringUtil.isNotEmpty(version)) {
          append(" $version", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
        }
      }
    })
    entryTable.getSelectionModel().addListSelectionListener {
      service<CoreUiCoroutineScopeHolder>().coroutineScope.launch {
        val selectedRow = entryTable.selectedRow
        if (selectedRow != -1) {
          val plugin = getValueAt(selectedRow)!!.descriptor
          myHeader.setPlugin(plugin)
          myDetailsComponent.setOnlyUpdateMode()
          myDetailsComponent.showPluginImpl(PluginUiModelAdapter(plugin), null)
        }
      }
    }
    removeAll()

    val splitter: Splitter = OnePixelSplitter(false)
    splitter.setFirstComponent(wrapWithPane(entryTable, 1, 1, 0, true))
    splitter.setSecondComponent(wrapWithPane(myDetailsComponent, 0, 0, 1, false))
    splitter.setProportion(0.3f)

    add(splitter, BorderLayout.CENTER)
  }

  override fun addAll(orderEntries: MutableCollection<out PluginDownloader?>) {
    super.addAll(orderEntries)
    TableUtil.ensureSelectionExists(entryTable)
  }

  override fun isChecked(downloader: PluginDownloader): Boolean {
    return !mySkippedPlugins.contains(downloader.id)
  }

  override fun setChecked(downloader: PluginDownloader, checked: Boolean) {
    val pluginId = downloader.id
    if (checked) {
      mySkippedPlugins.remove(pluginId)
    }
    else {
      mySkippedPlugins.add(pluginId)
    }
  }

  override fun requestFocus() {
    entryTable.requestFocus()
  }

  companion object {
    private fun wrapWithPane(c: JComponent, top: Int, left: Int, right: Int, scrollPane: Boolean): JComponent {
      var c = c
      if (scrollPane) {
        c = ScrollPaneFactory.createScrollPane(c)
      }
      c.setBorder(JBUI.Borders.customLine(OnePixelDivider.BACKGROUND, top, left, 1, right))
      return c
    }
  }
}