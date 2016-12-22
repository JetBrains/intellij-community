/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.internal.statistic.ideSettings;

import com.intellij.util.containers.HashSet;

import javax.swing.*;
import java.util.Collections;
import java.util.Set;

public final class IdeInitialConfigButtonUsages {

  public static final Set<String> ALL_PLUGINS_SELECTED = Collections.unmodifiableSet(Collections.emptySet());

  private static volatile ConfigImport ourConfigImport = ConfigImport.NO_INIT;

  private static volatile String ourSkipRemainingPressedScreen = "";

  private static volatile Set<String> ourPredefinedDisabledPlugins = ALL_PLUGINS_SELECTED;

  private static volatile Set<String> ourDownloadedPlugins = Collections.emptySet();

  public static ConfigImport getConfigImport() {
    return ourConfigImport;
  }

  public static void setConfigImport(JRadioButton ...buttons) {
    if (buttons.length < ConfigImport.values().length) {
      for (int i = 0; i < buttons.length; i++) {
        if (buttons[i] != null && buttons[i].isSelected()) {
          ourConfigImport = ConfigImport.values()[i];
        }
      }
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

  public static Set<String> getDownloadedPlugins() {
    return ourDownloadedPlugins;
  }

  public static void addDownloadedPlugin(String pluginId) {
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
