// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.layout.tableLayout;

import com.intellij.ide.IdeBundle;
import com.intellij.ui.tabs.impl.tabsLayout.TabsLayout;
import com.intellij.ui.tabs.impl.tabsLayout.TabsLayoutInfo;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.ide.ui.UISettings.TABS_NONE;
import static javax.swing.SwingConstants.TOP;

public class TableLayoutInfo extends TabsLayoutInfo {
  @NotNull
  @Override
  public String getId() {
    return "TableLayoutInfo";
  }

  @NotNull
  @Override
  @Nls
  public String getName() {
    return IdeBundle.message("tabs.layout.table.name");
  }

  @NotNull
  @Override
  protected TabsLayout createTabsLayoutInstance() {
    return new TableLayout();
  }

  @Nullable
  @Override
  public Integer[] getAvailableTabsPositions() {
    return new Integer[]{TOP, TABS_NONE};
  }
}
