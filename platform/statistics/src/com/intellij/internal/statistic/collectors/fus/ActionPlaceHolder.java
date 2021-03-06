// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus;

import com.intellij.openapi.extensions.ExtensionPointAndAreaListener;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public final class ActionPlaceHolder {
  public static final ExtensionPointName<ActionCustomPlaceAllowlist>
    EP_NAME = ExtensionPointName.create("com.intellij.statistics.actionCustomPlaceAllowlist");

  private static final Set<String> ourCustomPlaces = new HashSet<>();

  static {
    for (ActionCustomPlaceAllowlist extension : EP_NAME.getExtensionList()) {
      registerCustomPlaces(extension.places);
    }

    EP_NAME.addExtensionPointListener(new ExtensionPointAndAreaListener<>() {
      @Override
      public void extensionAdded(@NotNull ActionCustomPlaceAllowlist extension, @NotNull PluginDescriptor pluginDescriptor) {
        registerCustomPlaces(extension.places);
      }
    }, null);
  }

  private static void registerCustomPlaces(@Nullable String place) {
    if (Strings.isNotEmpty(place)) {
      if (!place.contains(";")) {
        ourCustomPlaces.add(place);
      }
      else {
        ourCustomPlaces.addAll(StringUtil.split(place, ";"));
      }
    }
  }

  public static boolean isCustomActionPlace(@NotNull String place) {
    return ourCustomPlaces.contains(place);
  }
}
