// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.ui;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.jdkEx.JdkEx;
import com.intellij.openapi.actionSystem.ex.QuickListsManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JreHiDpiUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * @author Konstantin Bulenkov
 */
final class UiInfoUsageCollector extends ApplicationUsagesCollector {
  private static final Logger LOG = Logger.getInstance(UiInfoUsageCollector.class);
  private static final EventLogGroup GROUP = new EventLogGroup("ui.info.features", 12);
  private static final EnumEventField<VisibilityType> orientationField = EventFields.Enum("value", VisibilityType.class);
  private static final EventId1<NavBarType> NAV_BAR = GROUP.registerEvent("Nav.Bar", EventFields.Enum("value", NavBarType.class));
  private static final EventId1<VisibilityType> NAV_BAR_MEMBERS = GROUP.registerEvent("Nav.Bar.members", orientationField);
  private static final EventId1<VisibilityType> TOOLBAR = GROUP.registerEvent("Toolbar", orientationField);
  private static final EventId2<VisibilityType, VisibilityType> TOOLBAR_AND_NAV_BAR =
    GROUP.registerEvent("Toolbar.and.NavBar",
                        EventFields.Enum("toolbar", VisibilityType.class),
                        EventFields.Enum("navbar", VisibilityType.class)
    );
  private static final EventId1<Boolean> RETINA = GROUP.registerEvent("Retina", EventFields.Enabled);
  private static final EventId1<Boolean> SHOW_TOOLWINDOW = GROUP.registerEvent("QuickDoc.Show.Toolwindow", EventFields.Enabled);
  private static final EventId1<Boolean> QUICK_DOC_AUTO_UPDATE = GROUP.registerEvent("QuickDoc.AutoUpdate", EventFields.Enabled);
  private static final EventId1<String>
    LOOK_AND_FEEL = GROUP.registerEvent("Look.and.Feel", EventFields.StringValidatedByEnum("value", "look_and_feel"));
  private static final EventId1<Boolean> LAF_AUTODETECT = GROUP.registerEvent("laf.autodetect", EventFields.Enabled);
  private static final EventId1<HidpiMode> HIDPI_MODE = GROUP.registerEvent("Hidpi.Mode", EventFields.Enum("value", HidpiMode.class));
  private static final EventId1<Boolean> SCREEN_READER = GROUP.registerEvent("Screen.Reader", EventFields.Enabled);
  private static final EventId1<Integer> QUICK_LISTS_COUNT = GROUP.registerEvent("QuickListsCount", EventFields.Int("value"));
  private static final BooleanEventField SCALE_MODE_FIELD = EventFields.Boolean("scale_mode");
  private static final FloatEventField SCALE_FIELD = EventFields.Float("scale");
  private static final VarargEventId SCREEN_SCALE = GROUP.registerVarargEvent("Screen.Scale", SCALE_MODE_FIELD, SCALE_FIELD);
  private static final EventId1<Integer> NUMBER_OF_MONITORS = GROUP.registerEvent("Number.Of.Monitors", EventFields.Int("value"));
  private static final StringEventField SCREEN_RESOLUTION_FIELD = new StringEventField("value") {
    private final List<String> rules =
      List.of("{regexp#integer}x{regexp#integer}_({regexp#integer}%)", "{regexp#integer}x{regexp#integer}");

    @NotNull
    @Override
    public List<String> getValidationRule() {
      return rules;
    }
  };
  private static final EventId2<Integer, String> SCREEN_RESOLUTION = GROUP.registerEvent("Screen.Resolution", EventFields.Int("display_id"),
                                                                                         SCREEN_RESOLUTION_FIELD);


  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  @NotNull
  @Override
  public Set<MetricEvent> getMetrics() {
    return getDescriptors();
  }

  @NotNull
  public static Set<MetricEvent> getDescriptors() {
    Set<MetricEvent> set = new HashSet<>();

    set.add(NAV_BAR.metric(navbar() ? NavBarType.visible : NavBarType.floating));
    set.add(
      NAV_BAR_MEMBERS.metric(UISettings.getInstance().getShowMembersInNavigationBar() ? VisibilityType.visible : VisibilityType.hidden));
    set.add(TOOLBAR.metric(toolbar() ? VisibilityType.visible : VisibilityType.hidden));

    set.add(TOOLBAR_AND_NAV_BAR.metric(
      toolbar() ? VisibilityType.visible : VisibilityType.hidden,
      navbar() ? VisibilityType.visible : VisibilityType.hidden
    ));

    set.add(RETINA.metric(UIUtil.isRetina()));

    PropertiesComponent properties = PropertiesComponent.getInstance();
    set.add(SHOW_TOOLWINDOW.metric(properties.isTrueValue("ShowDocumentationInToolWindow")));
    set.add(QUICK_DOC_AUTO_UPDATE.metric(properties.getBoolean("DocumentationAutoUpdateEnabled", true)));

    UIManager.LookAndFeelInfo laf = LafManager.getInstance().getCurrentLookAndFeel();
    String value1 = StringUtil.notNullize(laf != null ? laf.getName() : null, "unknown");
    set.add(LOOK_AND_FEEL.metric(value1));
    set.add(LAF_AUTODETECT.metric(LafManager.getInstance().getAutodetect()));

    HidpiMode value = JreHiDpiUtil.isJreHiDPIEnabled() ? HidpiMode.per_monitor_dpi : HidpiMode.system_dpi;
    set.add(HIDPI_MODE.metric(value));
    set.add(SCREEN_READER.metric(ScreenReader.isActive()));

    set.add(QUICK_LISTS_COUNT.metric(QuickListsManager.getInstance().getAllQuickLists().length));

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
      info += " (" + (int)(scale * 100) + "%)";
    }
    return info;
  }

  private static void addScreenResolutions(Set<MetricEvent> set) {
    GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
    for (int i = 0; i < devices.length; i++) {
      String info = getDeviceScreenInfo(devices[i]);
      set.add(SCREEN_RESOLUTION.metric(i, info));
    }
  }

  private static void addNumberOfMonitors(Set<MetricEvent> set) {
    int numberOfMonitors = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices().length;
    set.add(NUMBER_OF_MONITORS.metric(numberOfMonitors));
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

    Ref<Boolean> isScaleMode = new Ref<>();
    if (!GraphicsEnvironment.isHeadless()) {
      try {
        SwingUtilities.invokeAndWait(() -> {
          DisplayMode dm = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDisplayMode();
          isScaleMode.set(dm != null && !JdkEx.getDisplayModeEx().isDefault(dm));
        });
      }
      catch (InvocationTargetException e) {
        LOG.error(e);
      }
      catch (InterruptedException e) {
        // ignore
      }
    }

    ArrayList<EventPair<?>> data = new ArrayList<>();
    data.add(SCALE_FIELD.with(scale));
    if (!isScaleMode.isNull()) {
      data.add(SCALE_MODE_FIELD.with(isScaleMode.get()));
    }
    set.add(SCREEN_SCALE.metric(data));
  }

  enum NavBarType {visible, floating}

  enum VisibilityType {visible, hidden}

  enum HidpiMode {per_monitor_dpi, system_dpi}
}
