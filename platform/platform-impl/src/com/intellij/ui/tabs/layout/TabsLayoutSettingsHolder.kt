// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.layout

import com.intellij.ui.tabs.impl.tabsLayout.TabsLayoutInfo
import com.intellij.ui.tabs.layout.singleRowLayout.CompressibleSingleRowLayout
import com.intellij.ui.tabs.layout.singleRowLayout.ScrollableSingleRowLayout
import com.intellij.ui.tabs.layout.tableLayout.TableLayoutInfo

class TabsLayoutSettingsHolder {
  companion object {
    @JvmStatic
    val instance = TabsLayoutSettingsHolder()
  }

  val defaultInfo = ScrollableSingleRowLayout.ScrollableSingleRowTabsLayoutInfo()
  val installedInfos = generateInstalledInfos(defaultInfo)

  fun getInfoWithId(id: String?): TabsLayoutInfo? {
    if (id == null || id.isEmpty()) {
      return null
    }
    for (info in installedInfos) {
      if (id == info.id) {
        return info
      }
    }
    return null
  }

  private fun generateInstalledInfos(defaultInfo: TabsLayoutInfo): List<TabsLayoutInfo> {
    return listOf(
      defaultInfo,
      CompressibleSingleRowLayout.CompressibleSingleRowTabsLayoutInfo(),
      TableLayoutInfo()
    )
  }
}