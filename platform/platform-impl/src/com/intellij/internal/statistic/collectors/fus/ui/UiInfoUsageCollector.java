// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.ui;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.jdkEx.JdkEx;
import com.intellij.openapi.actionSystem.ex.QuickListsManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JreHiDpiUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.Set;

import static com.intellij.internal.statistic.beans.MetricEventFactoryKt.newBooleanMetric;
import static com.intellij.internal.statistic.beans.MetricEventFactoryKt.newMetric;

/**
 * @author Konstantin Bulenkov
 */
final class UiInfoUsageCollector extends ApplicationUsagesCollector {
  @NotNull
  @Override
  public String getGroupId() {
    return "ui.info.features";
  }

  @Override
  public int getVersion() {
    return 8;
  }

  @NotNull
  @Override
  public Set<MetricEvent> getMetrics() {
    return getDescriptors();
  }

  @NotNull
  public static Set<MetricEvent> getDescriptors() {
    Set<MetricEvent> set = new HashSet<>();

    addValue(set, "Nav.Bar", navbar() ? "visible" : "floating");
    addValue(set, "Nav.Bar.members", UISettings.getInstance().getShowMembersInNavigationBar() ? "visible" : "hidden");
    addValue(set, "Toolbar", toolbar() ? "visible" : "hidden");

    addValue(set, "Toolbar.and.NavBar", new FeatureUsageData().
      addData("toolbar", toolbar() ? "visible" : "hidden").
      addData("navbar", navbar() ? "visible" : "hidden")
    );

    addEnabled(set, "Retina", UIUtil.isRetina());

    PropertiesComponent properties = PropertiesComponent.getInstance();
    addEnabled(set, "QuickDoc.Show.Toolwindow", properties.isTrueValue("ShowDocumentationInToolWindow"));
    addEnabled(set, "QuickDoc.AutoUpdate", properties.getBoolean("DocumentationAutoUpdateEnabled", true));

    UIManager.LookAndFeelInfo laf = LafManager.getInstance().getCurrentLookAndFeel();
    addValue(set, "Look.and.Feel", StringUtil.notNullize(laf != null ? laf.getName() : null, "unknown"));

    addValue(set, "Hidpi.Mode", JreHiDpiUtil.isJreHiDPIEnabled() ? "per_monitor_dpi" : "system_dpi");
    addEnabled(set, "Screen.Reader", ScreenReader.isActive());

    set.add(newMetric("QuickListsCount", QuickListsManager.getInstance().getAllQuickLists().length));

    addScreenScale(set);
    addNumberOfMonitors(set);
    addScreenResolutions(set);
    return set;
  }

  private static String getDeviceScreenInfo(GraphicsDevice device) {
    GraphicsConfiguration conf = device.getDefaultConfiguration();
    Rectangle rect = conf.getBounds();
    String info = rect.width + "x" + rect.height;
    float scale = JBUIScale.sysScale(conf);
    if (scale != 1f) {
      info += " (" + (int)(scale * 100) +"%)";
    }
    return info;
  }

  private static void addScreenResolutions(Set<MetricEvent> set) {
    GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
    for (int i = 0; i < devices.length; i++) {
      String info = getDeviceScreenInfo(devices[i]);
      FeatureUsageData data = new FeatureUsageData().addValue(info).addData("display_id", i);
      set.add(newMetric("Screen.Resolution", data));
    }
  }

  private static void addNumberOfMonitors(Set<MetricEvent> set) {
    int numberOfMonitors = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices().length;
    addValue(set, "Number.Of.Monitors", String.valueOf(numberOfMonitors));
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
