// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.layout;

import com.intellij.ide.ui.UISettings;
import com.intellij.ui.tabs.impl.tabsLayout.TabsLayoutInfo;
import com.intellij.ui.tabs.impl.tabsLayout.TabsLayoutSettingsManager;
import org.jetbrains.annotations.NotNull;

public class TabsLayoutSettingsManagerImpl implements TabsLayoutSettingsManager {

  @NotNull
  @Override
  public TabsLayoutInfo getDefaultTabsLayoutInfo() {
    return TabsLayoutSettingsHolder.getInstance().getDefaultInfo();
  }


  @NotNull
  @Override
  public TabsLayoutInfo getSelectedTabsLayoutInfo() {
    String selectedTabsLayoutInfoId = UISettings.getInstance().getSelectedTabsLayoutInfoId();
    TabsLayoutInfo tabsLayoutInfo = TabsLayoutSettingsHolder.getInstance().getInfoWithId(selectedTabsLayoutInfoId);
    if (tabsLayoutInfo == null) {
      tabsLayoutInfo = TabsLayoutSettingsHolder.getInstance().getDefaultInfo();
    }
    return tabsLayoutInfo;
  }
}
