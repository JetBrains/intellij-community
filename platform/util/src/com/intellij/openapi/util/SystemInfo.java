// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.lang.JavaVersion;
import com.intellij.util.system.CpuArch;
import com.intellij.util.system.OS;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Paths;

import static com.intellij.openapi.util.NotNullLazyValue.lazy;

/**
 * Provides information about operating system, system-wide settings, and Java Runtime.
 */
public final class SystemInfo {
  /** Use {@link OS} instead */
  @ApiStatus.Obsolete
  public static final String OS_NAME = SystemInfoRt.OS_NAME;
  /** Use {@link OS#version()} instead */
  @ApiStatus.Obsolete
  public static final String OS_VERSION = SystemInfoRt.OS_VERSION;
  /** Use {@link CpuArch} instead */
  @ApiStatus.Obsolete
  public static final String OS_ARCH = System.getProperty("os.arch");

  public static final String JAVA_VERSION = System.getProperty("java.version");
  public static final String JAVA_RUNTIME_VERSION = getRtVersion(JAVA_VERSION);
  public static final String JAVA_VENDOR = System.getProperty("java.vm.vendor", "Unknown");

  private static String getRtVersion(@SuppressWarnings("SameParameterValue") String fallback) {
    String rtVersion = System.getProperty("java.runtime.version");
    return rtVersion != null && Character.isDigit(rtVersion.charAt(0)) ? rtVersion : fallback;
  }

  /** Use {@link OS#CURRENT} instead */
  @ApiStatus.Obsolete
  public static final boolean isWindows = OS.CURRENT == OS.Windows;
  /** Use {@link OS#CURRENT} instead */
  @ApiStatus.Obsolete
  public static final boolean isMac = OS.CURRENT == OS.macOS;
  /** Use {@link OS#CURRENT} instead */
  @ApiStatus.Obsolete
  public static final boolean isLinux = OS.CURRENT == OS.Linux;
  /** Use {@link OS#CURRENT} instead */
  @ApiStatus.Obsolete
  public static final boolean isFreeBSD = OS.CURRENT == OS.FreeBSD;
  /** Use {@link OS#CURRENT} instead */
  @ApiStatus.Obsolete
  public static final boolean isUnix = OS.CURRENT != OS.Windows;

  /** @deprecated unimportant; use {@link OS.UnixInfo#getDistro()} if needed */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static final boolean isChromeOS = false;

  public static final boolean isOracleJvm = Strings.indexOfIgnoreCase(JAVA_VENDOR, "Oracle", 0) >= 0;
  public static final boolean isIbmJvm = Strings.indexOfIgnoreCase(JAVA_VENDOR, "IBM", 0) >= 0;
  public static final boolean isAzulJvm = Strings.indexOfIgnoreCase(JAVA_VENDOR, "Azul", 0) >= 0;
  public static final boolean isJetBrainsJvm = Strings.indexOfIgnoreCase(JAVA_VENDOR, "JetBrains", 0) >= 0;

  /** @deprecated use {@link OS#CURRENT} and {@link OS#isAtLeast} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static boolean isOsVersionAtLeast(@NotNull String version) {
    return StringUtil.compareVersionNumbers(OS_VERSION, version) >= 0;
  }

  /** @deprecated always true on Windows */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static final boolean isWin8OrNewer = OS.CURRENT == OS.Windows;
  /** Use {@link OS#CURRENT} and {@link OS#isAtLeast} instead */
  @ApiStatus.Obsolete
  public static final boolean isWin10OrNewer = OS.CURRENT == OS.Windows && OS.CURRENT.isAtLeast(10, 0);
  /** Use {@link OS#CURRENT} and {@link OS#isAtLeast} instead */
  @ApiStatus.Obsolete
  public static final boolean isWin11OrNewer = OS.CURRENT == OS.Windows && OS.CURRENT.isAtLeast(11, 0);

  /** @deprecated use {@link com.intellij.util.ui.StartupUiUtil#isWayland} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static final boolean isWayland = OS.isGenericUnix() && System.getenv("WAYLAND_DISPLAY") != null;
  /** @deprecated use {@link com.intellij.util.ui.UnixDesktopEnv#CURRENT} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  @SuppressWarnings("SpellCheckingInspection")
  public static final boolean isGNOME = OS.isGenericUnix() && (env("XDG_CURRENT_DESKTOP", "GNOME") || env("GDMSESSION", "gnome"));
  /** @deprecated use {@link com.intellij.util.ui.UnixDesktopEnv#CURRENT} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static final boolean isKDE = OS.isGenericUnix() && (env("XDG_CURRENT_DESKTOP", "KDE") || System.getenv("KDE_FULL_SESSION") != null);
  /** @deprecated use {@link com.intellij.util.ui.UnixDesktopEnv#CURRENT} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static final boolean isXfce = OS.isGenericUnix() && env("XDG_CURRENT_DESKTOP", "XFCE");
  /** @deprecated use {@link com.intellij.util.ui.UnixDesktopEnv#CURRENT} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static final boolean isI3 = OS.isGenericUnix() && env("XDG_CURRENT_DESKTOP", "i3");

  private static boolean env(String varName, String marker) {
    String value = System.getenv(varName);
    return value != null && value.contains(marker);
  }

  public static final boolean isFileSystemCaseSensitive = SystemInfoRt.isFileSystemCaseSensitive;

  /** @deprecated use {@link com.intellij.execution.configurations.PathEnvironmentVariableUtil#isOnPath} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static boolean hasXdgOpen() {
    return ourHasXdgOpen.get();
  }

  /** @deprecated use {@link com.intellij.execution.configurations.PathEnvironmentVariableUtil#isOnPath} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static boolean hasXdgMime() {
    return ourHasXdgMime.get();
  }

  /** Use {@link OS#CURRENT} and {@link OS#isAtLeast} instead */
  @ApiStatus.Obsolete
  public static final boolean isMacOSCatalina = OS.CURRENT == OS.macOS && OS.CURRENT.isAtLeast(10, 15);
  /** Use {@link OS#CURRENT} and {@link OS#isAtLeast} instead */
  @ApiStatus.Obsolete
  public static final boolean isMacOSBigSur = OS.CURRENT == OS.macOS && OS.CURRENT.isAtLeast(10, 16);
  /** @deprecated use {@link OS#CURRENT} and {@link OS#isAtLeast} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static final boolean isMacOSMonterey = OS.CURRENT == OS.macOS && OS.CURRENT.isAtLeast(12, 0);
  /** Use {@link OS#CURRENT} and {@link OS#isAtLeast} instead */
  @ApiStatus.Obsolete
  public static final boolean isMacOSVentura = OS.CURRENT == OS.macOS && OS.CURRENT.isAtLeast(13, 0);
  /** @deprecated use {@link OS#CURRENT} and {@link OS#isAtLeast} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static final boolean isMacOSSonoma = OS.CURRENT == OS.macOS && OS.CURRENT.isAtLeast(14, 0);
  /** Use {@link OS#CURRENT} and {@link OS#isAtLeast} instead */
  @ApiStatus.Obsolete
  public static final boolean isMacOSSequoia = OS.CURRENT == OS.macOS && OS.CURRENT.isAtLeast(15, 0);

  /** Use {@link OS.WindowsInfo#getBuildNumber} instead */
  @ApiStatus.Obsolete
  public static @Nullable Long getWinBuildNumber() {
    return isWindows ? WinBuildNumber.getWinBuildNumber() : null;
  }

  /** @deprecated use {@link OS#CURRENT} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static String getOsName() {
    return OS.CURRENT.name();
  }

  /** @deprecated use {@link OS#CURRENT} and {@link OS#version} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static String getOsNameAndVersion() {
    return OS.CURRENT.name() + ' ' + OS.CURRENT.version();
  }

  //<editor-fold desc="Deprecated stuff.">
  /** @deprecated please use {@link Runtime#version()} (in the platform) or {@link JavaVersion} (in utils) */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static boolean isJavaVersionAtLeast(String v) {
    return StringUtil.compareVersionNumbers(JAVA_RUNTIME_VERSION, v) >= 0;
  }

  /** @deprecated might be inaccurate; please use {@link CpuArch} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static final boolean is32Bit = CpuArch.CURRENT.width == 32;

  /** @deprecated might be inaccurate; please use {@link CpuArch} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static final boolean is64Bit = CpuArch.CURRENT.width == 64;

  /** @deprecated use {@link CpuArch#isArm64()} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static final boolean isAarch64 = CpuArch.isArm64();

  /** @deprecated press 'F' */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static final boolean isSolaris = false;

  /** @deprecated misleading; consider using {@link OS#isGenericUnix} instead, if appropriate */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static final boolean isXWindow = isUnix && !isMac;

  private static final NotNullLazyValue<Boolean> ourHasXdgOpen = isUnix && !isMac ? lazy(() -> isOnPath("xdg-open")) : NotNullLazyValue.createConstantValue(false);
  private static final NotNullLazyValue<Boolean> ourHasXdgMime = isUnix && !isMac ? lazy(() -> isOnPath("xdg-mime")) : NotNullLazyValue.createConstantValue(false);

  private static boolean isOnPath(String name) {
    String path = System.getenv("PATH");
    if (path != null) {
      for (String dir : StringUtil.tokenize(path, ":")) {
        if (Files.isExecutable(Paths.get(dir, name))) {
          return true;
        }
      }
    }
    return false;
  }
  //</editor-fold>
}
