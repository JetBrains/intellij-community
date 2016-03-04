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
package com.intellij.openapi.util;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
  public static final boolean isFreeBSD = SystemInfoRt.isFreeBSD;
  public static final boolean isSolaris = SystemInfoRt.isSolaris;
  public static final boolean isUnix = SystemInfoRt.isUnix;

  public static final boolean isAppleJvm = isAppleJvm();
  public static final boolean isOracleJvm = isOracleJvm();
  public static final boolean isSunJvm = isSunJvm();
  public static final boolean isIbmJvm = isIbmJvm();
  public static final boolean isJetbrainsJvm = isJetbrainsJvm();

  public static boolean isOsVersionAtLeast(@NotNull String version) {
    return StringUtil.compareVersionNumbers(OS_VERSION, version) >= 0;
  }

  // version numbers from http://msdn.microsoft.com/en-us/library/windows/desktop/ms724832.aspx
  public static final boolean isWin2kOrNewer = isWindows && isOsVersionAtLeast("5.0");
  public static final boolean isWinXpOrNewer = isWindows && isOsVersionAtLeast("5.1");
  public static final boolean isWinVistaOrNewer = isWindows && isOsVersionAtLeast("6.0");
  public static final boolean isWin7OrNewer = isWindows && isOsVersionAtLeast("6.1");
  public static final boolean isWin8OrNewer = isWindows && isOsVersionAtLeast("6.2");

  public static final boolean isXWindow = isUnix && !isMac;

  // http://www.freedesktop.org/software/systemd/man/os-release.html
  private static final NotNullLazyValue<Map<String, String>> ourOsReleaseInfo = new AtomicNotNullLazyValue<Map<String, String>>() {
    @NotNull
    @Override
    protected Map<String, String> compute() {
      if (isUnix && !isMac) {
        try {
          List<String> lines = FileUtil.loadLines("/etc/os-release");
          Map<String, String> info = ContainerUtil.newHashMap();
          for (String line : lines) {
            int p = line.indexOf('=');
            if (p > 0) {
              String name = line.substring(0, p);
              String value = StringUtil.unquoteString(line.substring(p + 1));
              if (!StringUtil.isEmptyOrSpaces(name) && !StringUtil.isEmptyOrSpaces(value)) {
                info.put(name, value);
              }
            }
          }
          return info;
        }
        catch (IOException ignored) { }
      }

      return Collections.emptyMap();
    }
  };
  @Nullable
  public static String getUnixReleaseName() {
    return ourOsReleaseInfo.getValue().get("NAME");
  }
  @Nullable
  public static String getUnixReleaseVersion() {
    return ourOsReleaseInfo.getValue().get("VERSION");
  }

  public static final boolean isMacSystemMenu = isMac && "true".equals(System.getProperty("apple.laf.useScreenMenuBar"));

  public static final boolean isFileSystemCaseSensitive = SystemInfoRt.isFileSystemCaseSensitive;
  public static final boolean areSymLinksSupported = isUnix || isWinVistaOrNewer;

  public static final boolean is32Bit = ARCH_DATA_MODEL == null || ARCH_DATA_MODEL.equals("32");
  public static final boolean is64Bit = !is32Bit;
  public static final boolean isMacIntel64 = isMac && "x86_64".equals(OS_ARCH);

  private static final NotNullLazyValue<Boolean> ourHasXdgOpen = new AtomicNotNullLazyValue<Boolean>() {
    @NotNull
    @Override
    protected Boolean compute() {
      return new File("/usr/bin/xdg-open").canExecute();
    }
  };
  public static boolean hasXdgOpen() {
    return isXWindow && ourHasXdgOpen.getValue();
  }

  private static final NotNullLazyValue<Boolean> ourHasXdgMime = new AtomicNotNullLazyValue<Boolean>() {
    @NotNull
    @Override
    protected Boolean compute() {
      return new File("/usr/bin/xdg-mime").canExecute();
    }
  };
  public static boolean hasXdgMime() {
    return isXWindow && ourHasXdgMime.getValue();
  }

  public static final boolean isMacOSTiger = isMac && isOsVersionAtLeast("10.4");
  public static final boolean isMacOSLeopard = isMac && isOsVersionAtLeast("10.5");
  public static final boolean isMacOSSnowLeopard = isMac && isOsVersionAtLeast("10.6");
  public static final boolean isMacOSLion = isMac && isOsVersionAtLeast("10.7");
  public static final boolean isMacOSMountainLion = isMac && isOsVersionAtLeast("10.8");
  public static final boolean isMacOSMavericks = isMac && isOsVersionAtLeast("10.9");
  public static final boolean isMacOSYosemite = isMac && isOsVersionAtLeast("10.10");
  public static final boolean isMacOSElCapitan = isMac && isOsVersionAtLeast("10.11");

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

  private static boolean isOracleJvm() {
    final String vendor = SystemProperties.getJavaVmVendor();
    return vendor != null && StringUtil.containsIgnoreCase(vendor, "Oracle");
  }

  private static boolean isSunJvm() {
    final String vendor = SystemProperties.getJavaVmVendor();
    return vendor != null && StringUtil.containsIgnoreCase(vendor, "Sun") && StringUtil.containsIgnoreCase(vendor, "Microsystems");
  }

  private static boolean isIbmJvm() {
    final String vendor = SystemProperties.getJavaVmVendor();
    return vendor != null && StringUtil.containsIgnoreCase(vendor, "IBM");
  }

  private static boolean isAppleJvm() {
    final String vendor = SystemProperties.getJavaVmVendor();
    return vendor != null && StringUtil.containsIgnoreCase(vendor, "Apple");
  }

  private static boolean isJetbrainsJvm() {
    final String vendor = SystemProperties.getJavaVendor();
    return vendor != null && StringUtil.containsIgnoreCase(vendor, "jetbrains");
  }

  /** @deprecated use {@link #isWinXpOrNewer} (to be removed in IDEA 17) */
  @SuppressWarnings("unused")
  public static final boolean isWindowsXP = isWindows && (OS_VERSION.equals("5.1") || OS_VERSION.equals("5.2"));

  /** @deprecated use {@link #is32Bit} or {@link #is64Bit} (to be removed in IDEA 17) */
  @SuppressWarnings("unused")
  public static final boolean isAMD64 = "amd64".equals(OS_ARCH);
}