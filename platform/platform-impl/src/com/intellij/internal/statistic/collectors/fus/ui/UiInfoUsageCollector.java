// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.ui;

import com.intellij.ide.GeneralSettings;
import com.intellij.ide.ui.UISettings;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Set;

import static com.intellij.internal.statistic.utils.StatisticsUtilKt.getBooleanUsage;

/**
 * @author Konstantin Bulenkov
 */
public class UiInfoUsageCollector extends ApplicationUsagesCollector {

  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() {
    return getDescriptors();
  }

  @NotNull
  public static Set<UsageDescriptor> getDescriptors() {
    Set<UsageDescriptor> set = new THashSet<>();

    add(set, "Nav.Bar.visible", navbar() ? 1 : 0);
    add(set, "Nav.Bar.floating", navbar() ? 0 : 1);
    add(set, "Toolbar.visible", toolbar() ? 1 : 0);
    add(set, "Toolbar.hidden", toolbar() ? 0 : 1);
    add(set, "Toolbar.and.NavBar", !toolbar() && navbar() ? 1 : 0);
    add(set, "Toolbar.and.NavBar.hidden", !toolbar() && !navbar() ? 1 : 0);
    add(set, "Status.bar.visible", status() ? 1 : 0);
    add(set, "Status.bar.hidden", status() ? 0 : 1);
    add(set, "Tool.Window.buttons.visible", stripes() ? 1 : 0);
    add(set, "Tool.Window.buttons.hidden", stripes() ? 0 : 1);
    add(set, "Recent.Files[15]", recent() == 15 ? 1 : 0);
    add(set, "Recent.Files[15_30]", 15 < recent() && recent() < 31 ? 1 : 0);
    add(set, "Recent.Files[30_50]", 30 < recent() && recent() < 51 ? 1 : 0);
    add(set, "Recent.Files[more.than.50]", 50 < recent() ? 1 : 0);
    add(set, "Block.cursor", EditorSettingsExternalizable.getInstance().isBlockCursor() ? 1 : 0);
    add(set, "Line.Numbers", EditorSettingsExternalizable.getInstance().isLineNumbersShown() ? 1 : 0);
    add(set, "Gutter.Icons", EditorSettingsExternalizable.getInstance().areGutterIconsShown() ? 1 : 0);
    add(set, "Soft.Wraps", EditorSettingsExternalizable.getInstance().isUseSoftWraps() ? 1 : 0);
    add(set, "Tabs.None", tabPlace() == 0 ? 1 : 0);
    add(set, "Tabs.Top", tabPlace() == SwingConstants.TOP ? 1 : 0);
    add(set, "Tabs.Bottom", tabPlace() == SwingConstants.BOTTOM ? 1 : 0);
    add(set, "Tabs.Left", tabPlace() == SwingConstants.LEFT ? 1 : 0);
    add(set, "Tabs.Right", tabPlace() == SwingConstants.RIGHT ? 1 : 0);
    add(set, "Retina", UIUtil.isRetina() ? 1 : 0);
    add(set, "Show.tips.on.startup", GeneralSettings.getInstance().isShowTipsOnStartup() ? 1 : 0);
    set.add(getBooleanUsage("Allow.merging.buttons", UISettings.getInstance().getAllowMergeButtons()));

    return set;
  }

  @NotNull
  @Override
  public String getGroupId() {
    return "statistics.ui.info.features";
  }

  private static void add(Set<UsageDescriptor> set, String key, int value) {
    set.add(new UsageDescriptor(key, value));
  }

  private static int tabPlace() {
    return UISettings.getInstance().getEditorTabPlacement();
  }

  private static int recent() {
    return UISettings.getInstance().getRecentFilesLimit();
  }

  private static boolean stripes() {
    return UISettings.getInstance().getHideToolStripes();
  }

  private static boolean status() {
    return UISettings.getInstance().getShowStatusBar();
  }

  private static boolean toolbar() {
    return UISettings.getInstance().getShowMainToolbar();
  }

  private static boolean navbar() {
    return UISettings.getInstance().getShowNavigationBar();
  }
}
