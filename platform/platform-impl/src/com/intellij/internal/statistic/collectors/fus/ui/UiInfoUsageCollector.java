// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.ui;

import com.intellij.ide.GeneralSettings;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.jdkEx.JdkEx;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JreHiDpiUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Set;

import static com.intellij.internal.statistic.beans.MetricEventFactoryKt.newBooleanMetric;
import static com.intellij.internal.statistic.beans.MetricEventFactoryKt.newMetric;

/**
 * @author Konstantin Bulenkov
 */
public class UiInfoUsageCollector extends ApplicationUsagesCollector {

  @NotNull
  @Override
  public String getGroupId() {
    return "ui.info.features";
  }

  @Override
  public int getVersion() {
    return 3;
  }

  @NotNull
  @Override
  public Set<MetricEvent> getMetrics() {
    return getDescriptors();
  }

  @NotNull
  public static Set<MetricEvent> getDescriptors() {
    Set<MetricEvent> set = new THashSet<>();

    addValue(set, "Nav.Bar", navbar() ? "visible" : "floating");
    addValue(set, "Toolbar", toolbar() ? "visible" : "hidden");
    addValue(set, "Status.bar", status() ? "visible" : "hidden");
    addValue(set, "Tool.Window.buttons", stripes() ? "visible" : "hidden");

    addValue(set, "Toolbar.and.NavBar", new FeatureUsageData().
      addData("toolbar", toolbar() ? "visible" : "hidden").
      addData("navbar", navbar() ? "visible" : "hidden")
    );

    addValue(set, "Recent.files.limit", new FeatureUsageData().
      addData("count", recent()).
      addData("grouped", recentPeriod(recent()))
    );

    UISettings ui = UISettings.getInstance();
    addEnabled(set, "Show.Editor.Tabs.In.Single.Row", ui.getScrollTabLayoutInEditor());
    addEnabled(set, "Hide.Editor.Tabs.If.Needed", ui.getScrollTabLayoutInEditor() && ui.getHideTabsIfNeeded());
    addEnabled(set, "Block.cursor", EditorSettingsExternalizable.getInstance().isBlockCursor());
    addEnabled(set, "Line.Numbers", EditorSettingsExternalizable.getInstance().isLineNumbersShown());
    addEnabled(set, "Gutter.Icons", EditorSettingsExternalizable.getInstance().areGutterIconsShown());
    addEnabled(set, "Soft.Wraps", EditorSettingsExternalizable.getInstance().isUseSoftWraps());

    addValue(set, "Tabs", getTabsSetting());

    addEnabled(set, "Retina", UIUtil.isRetina());
    addEnabled(set, "Show.tips.on.startup", GeneralSettings.getInstance().isShowTipsOnStartup());
    addEnabled(set, "Allow.merging.buttons", ui.getAllowMergeButtons());

    PropertiesComponent properties = PropertiesComponent.getInstance();
    addEnabled(set, "QuickDoc.Show.Toolwindow", properties.isTrueValue("ShowDocumentationInToolWindow"));
    addEnabled(set, "QuickDoc.AutoUpdate", properties.getBoolean("DocumentationAutoUpdateEnabled", true));

    UIManager.LookAndFeelInfo laf = LafManager.getInstance().getCurrentLookAndFeel();
    addValue(set, "Look.and.Feel", StringUtil.notNullize(laf != null ? laf.getName() : null, "unknown"));

    addValue(set, "Hidpi.Mode", JreHiDpiUtil.isJreHiDPIEnabled() ? "per_monitor_dpi" : "system_dpi");
    addEnabled(set, "Screen.Reader", ScreenReader.isActive());

    addScreenScale(set);
    return set;
  }

  @NotNull
  private static String recentPeriod(int recent) {
    if (recent < 15) return "less.than.15";
    if (16 < recent && recent < 31) return "[15_30]";
    if (30 < recent && recent < 51) return "[30_50]";
    return "[more.than.50]";
  }

  @NotNull
  private static String getTabsSetting() {
    final int place = tabPlace();
    if (place == 0) return "None";
    if (place == SwingConstants.TOP) return "Top";
    if (place == SwingConstants.BOTTOM) return "Bottom";
    if (place == SwingConstants.LEFT) return "Left";
    if (place == SwingConstants.RIGHT) return "Right";
    return "Unknown";
  }

  private static void addValue(Set<? super MetricEvent> set, String key, FeatureUsageData data) {
    set.add(newMetric(key, data));
  }

  private static void addValue(Set<? super MetricEvent> set, String key, String value) {
    set.add(newMetric(key, new FeatureUsageData().addValue(value)));
  }

  private static void addEnabled(Set<? super MetricEvent> set, String key, boolean enabled) {
    set.add(newBooleanMetric(key, enabled));
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

  private static void addScreenScale(Set<? super MetricEvent> set) {
    float scale = JBUIScale.sysScale();

    int scaleBase = (int)Math.floor(scale);
    float scaleFract = scale - scaleBase;

    if (scaleFract == 0.0f) scaleFract = 0.0f; // count integer scale on a precise match only
    else if (scaleFract < 0.375f) scaleFract = 0.25f;
    else if (scaleFract < 0.625f) scaleFract = 0.5f;
    else scaleFract = 0.75f;

    scale = scaleBase + scaleFract;

    boolean isScaleMode = false;
    if (!GraphicsEnvironment.isHeadless()) {
      DisplayMode dm = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDisplayMode();
      isScaleMode = JdkEx.getDisplayModeEx().isDefault(dm);
    }

    FeatureUsageData data = new FeatureUsageData().
      addData("scale_mode", isScaleMode).addData("scale", scale);
    set.add(newMetric("Screen.Scale", data));
  }
}
