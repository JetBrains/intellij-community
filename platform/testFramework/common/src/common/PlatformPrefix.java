// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.net.URL;

import static com.intellij.ide.plugins.PluginDescriptorLoader.isProductWithTheOnlyDescriptor;

@TestOnly
@Internal
public final class PlatformPrefix {
  private PlatformPrefix() { }

  private static final String[] PREFIX_CANDIDATES = {
    "Rider", "GoLand", "CLion", "FleetBackend",
    null,
    "AppCode", "SwiftTests",
    "Python", "DataSpell", "PyCharmCore",
    "DataGrip",
    "Ruby",
    "PhpStorm",
    "RustRover",
    "UltimateLangXml", "Idea", "PlatformLangXml"
  };

  private static boolean ourPlatformPrefixInitialized;

  public static void autodetectPlatformPrefix() {
    if (ourPlatformPrefixInitialized) {
      return;
    }

    if (System.getProperty(PlatformUtils.PLATFORM_PREFIX_KEY) != null) {
      ourPlatformPrefixInitialized = true;
      return;
    }

    for (String candidate : PREFIX_CANDIDATES) {
      String markerPath;
      if (candidate == null) {
        markerPath = "idea/ApplicationInfo.xml";
      }
      else if (isProductWithTheOnlyDescriptor(candidate)) {
        markerPath = "idea/" + candidate + "ApplicationInfo.xml";
      }
      else {
        markerPath = "META-INF/" + candidate + "Plugin.xml";
      }
      URL resource = PlatformPrefix.class.getClassLoader().getResource(markerPath);
      if (resource != null) {
        if (candidate != null) {
          setPlatformPrefix(candidate);
          var logger = Logger.getInstance(PlatformPrefix.class);
          logger.info(String.format("Platform prefix (IDE that is emulated by this test): %s. File %s", candidate, resource));
        }
        break;
      }
    }
  }

  private static void setPlatformPrefix(@NotNull String prefix) {
    System.setProperty(PlatformUtils.PLATFORM_PREFIX_KEY, prefix);
    ourPlatformPrefixInitialized = true;
  }
}
