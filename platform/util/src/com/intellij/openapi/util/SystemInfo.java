/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

@SuppressWarnings({"HardCodedStringLiteral", "UtilityClassWithoutPrivateConstructor", "UnusedDeclaration"})
public class SystemInfo extends SystemInfoRt {
  public static final String OS_NAME = SystemInfoRt.OS_NAME;
  public static final String OS_VERSION = SystemInfoRt.OS_VERSION;
  public static final String OS_ARCH = System.getProperty("os.arch");
  public static final String JAVA_VERSION = System.getProperty("java.version");
  public static final String JAVA_RUNTIME_VERSION = System.getProperty("java.runtime.version");
  public static final String ARCH_DATA_MODEL = System.getProperty("sun.arch.data.model");
  public static final String SUN_DESKTOP = System.getProperty("sun.desktop", "");

  public static final boolean isWindows = SystemInfoRt.isWindows;
  public static final boolean isMac = SystemInfoRt.isMac;
  public static final boolean isOS2 = SystemInfoRt.isOS2;
  public static final boolean isLinux = SystemInfoRt.isLinux;
  public static final boolean isFreeBSD = _OS_NAME.startsWith("freebsd");
  public static final boolean isSolaris = _OS_NAME.startsWith("sunos");
  public static final boolean isUnix = SystemInfoRt.isUnix;

  public static final boolean isAppleJvm = isAppleJvm();
  public static final boolean isOracleJvm = isOracleJvm();

  public static boolean isOsVersionAtLeast(@NotNull String version) {
    return StringUtil.compareVersionNumbers(OS_VERSION, version) >= 0;
  }

  // version numbers from http://msdn.microsoft.com/en-us/library/windows/desktop/ms724832.aspx
  public static final boolean isWin2kOrNewer = isWindows && isOsVersionAtLeast("5.0");
  public static final boolean isWinVistaOrNewer = isWindows && isOsVersionAtLeast("6.0");
  public static final boolean isWin7OrNewer = isWindows && isOsVersionAtLeast("6.1");
  /** @deprecated unsupported (to remove in IDEA 13) */
  public static final boolean isWindows9x = _OS_NAME.startsWith("windows 9") || _OS_NAME.startsWith("windows me");
  /** @deprecated unsupported (to remove in IDEA 13) */
  public static final boolean isWindowsNT = _OS_NAME.startsWith("windows nt");
  /** @deprecated use {@linkplain #OS_VERSION} (to remove in IDEA 13) */
  public static final boolean isWindows2000 = _OS_NAME.startsWith("windows 2000");
  /** @deprecated use {@linkplain #OS_VERSION} (to remove in IDEA 13) */
  public static final boolean isWindows2003 = _OS_NAME.startsWith("windows 2003");
  /** @deprecated use {@linkplain #OS_VERSION} (to remove in IDEA 13) */
  public static final boolean isWindowsXP = _OS_NAME.startsWith("windows xp");
  /** @deprecated use {@linkplain #OS_VERSION} (to remove in IDEA 13) */
  public static final boolean isWindowsVista = _OS_NAME.startsWith("windows vista");
  /** @deprecated use {@linkplain #OS_VERSION} (to remove in IDEA 13) */
  public static final boolean isWindows7 = _OS_NAME.startsWith("windows 7");

  /** @deprecated inaccurate (to remove in IDEA 13) */
  public static final boolean isKDE = SUN_DESKTOP.toLowerCase().contains("kde");
  /** @deprecated inaccurate (to remove in IDEA 13) */
  public static final boolean isGnome = SUN_DESKTOP.toLowerCase().contains("gnome");

  public static final boolean isXWindow = isUnix && !isMac;

  public static final boolean isMacSystemMenu = isMac && "true".equals(System.getProperty("apple.laf.useScreenMenuBar"));

  public static final boolean isFileSystemCaseSensitive = SystemInfoRt.isFileSystemCaseSensitive;
  public static final boolean areSymLinksSupported = isUnix || isWinVistaOrNewer;

  public static final boolean is32Bit = ARCH_DATA_MODEL == null || ARCH_DATA_MODEL.equals("32");
  public static final boolean is64Bit = !is32Bit;
  public static final boolean isAMD64 = "amd64".equals(OS_ARCH);
  public static final boolean isMacIntel64 = isMac && "x86_64".equals(OS_ARCH);

  /** @deprecated use {@linkplain #hasXdgOpen()} (to remove in IDEA 13) */
  public static final boolean hasXdgOpen = isXWindow;
  private static final NotNullLazyValue<Boolean> ourHasXdgOpen = new AtomicNotNullLazyValue<Boolean>() {
    @NotNull
    @Override
    protected Boolean compute() {
      return isUnix && new File("/usr/bin/xdg-open").canExecute();
    }
  };
  public static boolean hasXdgOpen() {
    return ourHasXdgOpen.getValue();
  }

  private static final NotNullLazyValue<Boolean> ourHasXdgMime = new AtomicNotNullLazyValue<Boolean>() {
    @NotNull
    @Override
    protected Boolean compute() {
      return isUnix && new File("/usr/bin/xdg-mime").canExecute();
    }
  };
  public static boolean hasXdgMime() {
    return ourHasXdgOpen.getValue();
  }

  private static final NotNullLazyValue<Boolean> hasNautilus = new AtomicNotNullLazyValue<Boolean>() {
    @NotNull
    @Override
    protected Boolean compute() {
      return isUnix && new File("/usr/bin/nautilus").canExecute();
    }
  };
  /** @deprecated implementation details (to remove in IDEA 13) */
  public static boolean hasNautilus() {
    return hasNautilus.getValue();
  }

  /** @deprecated implementation details (to remove in IDEA 13) */
  public static final String nativeFileManagerName = "File Manager";
  private static final NotNullLazyValue<String> ourFileManagerName = new AtomicNotNullLazyValue<String>() {
    @NotNull
    @Override
    protected String compute() {
      return isMac ? "Finder" :
             isWindows ? "Explorer" :
             "File Manager";
    }
  };
  /** @deprecated implementation details (to remove in IDEA 13) */
  public static String getFileManagerName() {
    return ourFileManagerName.getValue();
  }

  /** @deprecated use {@linkplain #isXWindow} (to remove in IDEA 13) */
  public static boolean X11PasteEnabledSystem = isXWindow;

  /** @deprecated useless (to remove in IDEA 14) */
  public static final boolean isIntelMac = isMac && "i386".equals(OS_ARCH);

  public static final boolean isMacOSTiger = isMac && isOsVersionAtLeast("10.4");
  public static final boolean isMacOSLeopard = isMac && isOsVersionAtLeast("10.5");
  public static final boolean isMacOSSnowLeopard = isMac && isOsVersionAtLeast("10.6");
  public static final boolean isMacOSLion = isMac && isOsVersionAtLeast("10.7");
  public static final boolean isMacOSMountainLion = isMac && isOsVersionAtLeast("10.8");
  public static final boolean isMacOSMavericks = isMac && isOsVersionAtLeast("10.9");

  @NotNull
  public static String getMacOSMajorVersion() {
    return getMacOSMajorVersion(OS_VERSION);
  }

  public static String getMacOSMajorVersion(String version) {
    int[] parts = getMacOSVersionParts(version);
    return String.format("%d.%d", parts[0], parts[1]);
  }

  @NotNull
  public static String getMacOSVersionCode() {
    return getMacOSVersionCode(OS_VERSION);
  }

  @NotNull
  public static String getMacOSMajorVersionCode() {
    return getMacOSMajorVersionCode(OS_VERSION);
  }

  @NotNull
  public static String getMacOSMinorVersionCode() {
    return getMacOSMinorVersionCode(OS_VERSION);
  }

  @NotNull
  public static String getMacOSVersionCode(@NotNull String version) {
    int[] parts = getMacOSVersionParts(version);
    return String.format("%02d%d%d", parts[0], normalize(parts[1]), normalize(parts[2]));
  }

  @NotNull
  public static String getMacOSMajorVersionCode(@NotNull String version) {
    int[] parts = getMacOSVersionParts(version);
    return String.format("%02d%d%d", parts[0], normalize(parts[1]), 0);
  }

  @NotNull
  public static String getMacOSMinorVersionCode(@NotNull String version) {
    int[] parts = getMacOSVersionParts(version);
    return String.format("%02d%02d", parts[1], parts[2]);
  }

  private static int[] getMacOSVersionParts(@NotNull String version) {
    List<String> parts = StringUtil.split(version, ".");
    while (parts.size() < 3) {
      parts.add("0");
    }
    return new int[]{toInt(parts.get(0)), toInt(parts.get(1)), toInt(parts.get(2))};
  }

  private static int normalize(int number) {
    return number > 9 ? 9 : number;
  }

  private static int toInt(String string) {
    try {
      return Integer.valueOf(string);
    }
    catch (NumberFormatException e) {
      return 0;
    }
  }

  public static boolean isJavaVersionAtLeast(String v) {
    return StringUtil.compareVersionNumbers(JAVA_RUNTIME_VERSION, v) >= 0;
  }

  /** @deprecated use {@linkplain SystemProperties#getIntProperty(String, int)} (to remove in IDEA 13) */
  public static int getIntProperty(@NotNull final String key, final int defaultValue) {
    return SystemProperties.getIntProperty(key, defaultValue);
  }

  private static boolean isOracleJvm() {
    final String vendor = SystemProperties.getJavaVmVendor();
    return vendor != null && StringUtil.containsIgnoreCase(vendor, "Oracle");
  }

  private static boolean isAppleJvm() {
    final String vendor = SystemProperties.getJavaVmVendor();
    return vendor != null && StringUtil.containsIgnoreCase(vendor, "Apple");
  }
}
