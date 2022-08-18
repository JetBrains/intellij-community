// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common;

import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.net.URL;

@TestOnly
@Internal
public final class PlatformPrefix {

  private PlatformPrefix() { }

  private static final String[] PREFIX_CANDIDATES = {
    "Rider", "GoLand", "CLion", "MobileIDE",
    null,
    "AppCode", "SwiftTests",
    "DataGrip",
    "Python", "DataSpell", "PyCharmCore",
    "Ruby",
    "PhpStorm",
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
      String markerPath = candidate == null ? "idea/ApplicationInfo.xml" : "META-INF/" + candidate + "Plugin.xml";
      URL resource = PlatformPrefix.class.getClassLoader().getResource(markerPath);
      if (resource != null) {
        if (candidate != null) {
          setPlatformPrefix(candidate);
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
