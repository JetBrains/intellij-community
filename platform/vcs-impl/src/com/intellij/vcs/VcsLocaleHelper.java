// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs;

import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.MissingResourceException;

public final class VcsLocaleHelper {

  private static final @NonNls String DEFAULT_EXECUTABLE_LOCALE_VALUE = "en_US.UTF-8";
  private static final @NonNls String REGISTRY_KEY_SUFFIX = ".executable.locale";

  @NonNls
  @NotNull
  public static String getDefaultLocaleFromRegistry(@NotNull @NonNls String prefix) {
    String registryKey = prefix + REGISTRY_KEY_SUFFIX;
    try {
      return Registry.stringValue(registryKey);
    }
    catch (MissingResourceException e) {
      return DEFAULT_EXECUTABLE_LOCALE_VALUE;
    }
  }

  @NotNull
  public static Map<String, String> getDefaultLocaleEnvironmentVars(@NotNull @NonNls String prefix) {
    Map<String, String> envMap = new LinkedHashMap<>();
    String defaultLocale = getDefaultLocaleFromRegistry(prefix);
    if (defaultLocale.isEmpty()) { // let skip locale definition if needed
      return envMap;
    }
    envMap.put("LANGUAGE", ""); //NON-NLS
    envMap.put("LC_ALL", defaultLocale); //NON-NLS
    return envMap;
  }
}
