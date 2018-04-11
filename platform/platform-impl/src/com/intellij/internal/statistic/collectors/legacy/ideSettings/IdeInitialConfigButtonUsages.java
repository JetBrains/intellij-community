// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.legacy.ideSettings;

import javax.swing.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class IdeInitialConfigButtonUsages {

  public static final Set<String> ALL_PLUGINS_SELECTED = Collections.unmodifiableSet(Collections.emptySet());

  private static volatile ConfigImport ourConfigImport = ConfigImport.NO_INIT;

  private static volatile String ourSkipRemainingPressedScreen = "";

  private static volatile Set<String> ourPredefinedDisabledPlugins = ALL_PLUGINS_SELECTED;

  private static Set<String> ourDownloadedPlugins = Collections.emptySet();

  public static ConfigImport getConfigImport() {
    return ourConfigImport;
  }

  public static void setConfigImport(JRadioButton myRbDoNotImport,
                                     JRadioButton myRbImport,
                                     JRadioButton myRbImportAuto,
                                     JRadioButton myCustomButton) {
    if (myRbDoNotImport != null && myRbDoNotImport.isSelected()) {
      ourConfigImport = ConfigImport.DO_NOT_IMPORT;
    }
    else if (myRbImport != null && myRbImport.isSelected()) {
      ourConfigImport = ConfigImport.IMPORT_PATH;
    }
    else if (myRbImportAuto != null && myRbImportAuto.isSelected()) {
      ourConfigImport = ConfigImport.IMPORT_AUTO;
    }
    else if (myCustomButton != null && myCustomButton.isSelected()) {
      ourConfigImport = ConfigImport.IMPORT_CUSTOM;
    }
  }

  public static String getSkipRemainingPressedScreen() {
    return ourSkipRemainingPressedScreen;
  }

  public static void setSkipRemainingPressedScreen(String skipRemainingPressedScreen) {
    ourSkipRemainingPressedScreen = skipRemainingPressedScreen;
  }

  public static Set<String> getPredefinedDisabledPlugins() {
    return ourPredefinedDisabledPlugins;
  }

  public static void setPredefinedDisabledPlugins(Set<String> predefinedDisabledPlugins) {
    if (predefinedDisabledPlugins.isEmpty()) {
      return;
    }
    ourPredefinedDisabledPlugins = predefinedDisabledPlugins;
  }

  public synchronized static Set<String> getDownloadedPlugins() {
    return ourDownloadedPlugins;
  }

  public synchronized static void addDownloadedPlugin(String pluginId) {
    if (ourDownloadedPlugins.isEmpty()) {
      ourDownloadedPlugins = new HashSet<>();
    }
    ourDownloadedPlugins.add(pluginId);
  }

  public enum ConfigImport {
    DO_NOT_IMPORT,
    IMPORT_PATH,
    IMPORT_AUTO,
    IMPORT_CUSTOM,
    NO_INIT
  }
}
