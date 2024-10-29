// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import com.intellij.openapi.util.io.PathExecLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.JavaVersion;
import com.intellij.util.system.CpuArch;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.function.Supplier;

/**
 * Provides information about operating system, system-wide settings, and Java Runtime.
 */
public final class SystemInfo {
  public static final String OS_NAME = SystemInfoRt.OS_NAME;
  public static final String OS_VERSION = SystemInfoRt.OS_VERSION;
  public static final String OS_ARCH = System.getProperty("os.arch");
  public static final String JAVA_VERSION = System.getProperty("java.version");
  public static final String JAVA_RUNTIME_VERSION = getRtVersion(JAVA_VERSION);
  public static final String JAVA_VENDOR = System.getProperty("java.vm.vendor", "Unknown");

  public static final boolean isAarch64 = OS_ARCH.equals("aarch64");

  private static String getRtVersion(@SuppressWarnings("SameParameterValue") String fallback) {
    String rtVersion = System.getProperty("java.runtime.version");
    return rtVersion != null && Character.isDigit(rtVersion.charAt(0)) ? rtVersion : fallback;
  }

  public static final boolean isWindows = SystemInfoRt.isWindows;
  public static final boolean isMac = SystemInfoRt.isMac;
  public static final boolean isLinux = SystemInfoRt.isLinux;
  public static final boolean isFreeBSD = SystemInfoRt.isFreeBSD;
  public static final boolean isSolaris = SystemInfoRt.isSolaris;
  public static final boolean isUnix = SystemInfoRt.isUnix;

  public static final boolean isChromeOS = isLinux && isCrostini();

  public static final boolean isOracleJvm = Strings.indexOfIgnoreCase(JAVA_VENDOR, "Oracle", 0) >= 0;
  public static final boolean isIbmJvm = Strings.indexOfIgnoreCase(JAVA_VENDOR, "IBM", 0) >= 0;
  public static final boolean isAzulJvm = Strings.indexOfIgnoreCase(JAVA_VENDOR, "Azul", 0) >= 0;
  public static final boolean isJetBrainsJvm = Strings.indexOfIgnoreCase(JAVA_VENDOR, "JetBrains", 0) >= 0;

  @SuppressWarnings("SpellCheckingInspection")
  private static boolean isCrostini() {
    return new File("/dev/.cros_milestone").exists();
  }

  public static boolean isOsVersionAtLeast(@NotNull String version) {
    return StringUtil.compareVersionNumbers(OS_VERSION, version) >= 0;
  }

  public static final boolean isWin8OrNewer = isWindows && isOsVersionAtLeast("6.2");
  public static final boolean isWin10OrNewer = isWindows && isOsVersionAtLeast("10.0");
  public static final boolean isWin11OrNewer = isWindows && isOsVersionAtLeast("11.0");

  /**
   * Set to true if we are running in a Wayland environment, either through
   * XWayland or using Wayland directly.
   */
  public static final boolean isWayland;
  public static final boolean isXWindow = SystemInfoRt.isUnix && !SystemInfoRt.isMac;
  public static final boolean isGNOME, isKDE, isXfce, isI3;
  static {
    // http://askubuntu.com/questions/72549/how-to-determine-which-window-manager-is-running/227669#227669
    // https://userbase.kde.org/KDE_System_Administration/Environment_Variables#KDE_FULL_SESSION
    if (SystemInfoRt.isUnix && !SystemInfoRt.isMac) {
      isWayland = System.getenv("WAYLAND_DISPLAY") != null;
      @SuppressWarnings("SpellCheckingInspection") String desktop = System.getenv("XDG_CURRENT_DESKTOP"), gdmSession = System.getenv("GDMSESSION");
      isGNOME = desktop != null && desktop.contains("GNOME") || gdmSession != null && gdmSession.contains("gnome");
      isKDE = !isGNOME && (desktop != null && desktop.contains("KDE") || System.getenv("KDE_FULL_SESSION") != null);
      isXfce = !isGNOME && !isKDE && (desktop != null && desktop.contains("XFCE"));
      isI3 = !isGNOME && !isKDE && !isXfce && (desktop != null && desktop.contains("i3"));
    }
    else {
      isWayland = isGNOME = isKDE = isXfce = isI3 = false;
    }
  }

  public static final boolean isMacSystemMenu = isMac && (SystemInfoRt.isJBSystemMenu || Boolean.getBoolean("apple.laf.useScreenMenuBar"));

  public static final boolean isFileSystemCaseSensitive = SystemInfoRt.isFileSystemCaseSensitive;

  private static final Supplier<Boolean> ourHasXdgOpen = SystemInfoRt.isUnix && !SystemInfoRt.isMac
                                                         ? PathExecLazyValue.create("xdg-open") : () -> false;
  public static boolean hasXdgOpen() {
    return ourHasXdgOpen.get();
  }

  private static final Supplier<Boolean> ourHasXdgMime = SystemInfoRt.isUnix && !SystemInfoRt.isMac
                                                         ? PathExecLazyValue.create("xdg-mime") : () -> false;
  public static boolean hasXdgMime() {
    return ourHasXdgMime.get();
  }

  public static final boolean isMacOSCatalina = isMac && isOsVersionAtLeast("10.15");
  public static final boolean isMacOSBigSur = isMac && isOsVersionAtLeast("10.16");
  public static final boolean isMacOSMonterey = isMac && isOsVersionAtLeast("12.0");
  public static final boolean isMacOSVentura = isMac && isOsVersionAtLeast("13.0");
  public static final boolean isMacOSSonoma = isMac && isOsVersionAtLeast("14.0");

  /**
   * Build number is the only more or less stable approach to get comparable Windows versions.
   * See <a href="https://en.wikipedia.org/wiki/List_of_Microsoft_Windows_versions">list of builds</a>.
   */
  public static @Nullable Long getWinBuildNumber() {
    return isWindows ? WinBuildNumber.getWinBuildNumber() : null;
  }

  public static @NotNull String getMacOSMajorVersion() {
    return getMacOSMajorVersion(OS_VERSION);
  }

  public static String getMacOSMajorVersion(String version) {
    int[] parts = getMacOSVersionParts(version);
    return String.format("%d.%d", parts[0], parts[1]);
  }

  public static @NotNull String getMacOSVersionCode() {
    return getMacOSVersionCode(OS_VERSION);
  }

  public static @NotNull String getMacOSMajorVersionCode() {
    return getMacOSMajorVersionCode(OS_VERSION);
  }

  public static @NotNull String getMacOSMinorVersionCode() {
    return getMacOSMinorVersionCode(OS_VERSION);
  }

  public static @NotNull String getMacOSVersionCode(@NotNull String version) {
    int[] parts = getMacOSVersionParts(version);
    return String.format("%02d%d%d", parts[0], normalize(parts[1]), normalize(parts[2]));
  }

  public static @NotNull String getMacOSMajorVersionCode(@NotNull String version) {
    int[] parts = getMacOSVersionParts(version);
    return String.format("%02d%d%d", parts[0], normalize(parts[1]), 0);
  }

  public static @NotNull String getMacOSMinorVersionCode(@NotNull String version) {
    int[] parts = getMacOSVersionParts(version);
    return String.format("%02d%02d", parts[1], parts[2]);
  }

  private static int[] getMacOSVersionParts(@NotNull String version) {
    List<String> parts = StringUtil.split(version, ".");
    if (parts.size() < 3) {
      parts = ContainerUtil.append(parts, "0", "0", "0");
    }
    return new int[]{toInt(parts.get(0)), toInt(parts.get(1)), toInt(parts.get(2))};
  }

  public static String getOsName() {
    return isMac ? "macOS" : OS_NAME;
  }

  public static String getOsNameAndVersion() {
    return getOsName() + ' ' + OS_VERSION;
  }

  private static int normalize(int number) {
    return Math.min(number, 9);
  }

  private static int toInt(String string) {
    try {
      return Integer.parseInt(string);
    }
    catch (NumberFormatException e) {
      return 0;
    }
  }

  //<editor-fold desc="Deprecated stuff.">
  /** @deprecated please use {@link Runtime#version()} (in the platform) or {@link JavaVersion} (in utils) */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static boolean isJavaVersionAtLeast(int major, int minor, int update) {
    return JavaVersion.current().compareTo(JavaVersion.compose(major, minor, update, 0, false)) >= 0;
  }

  /** @deprecated please use {@link Runtime#version()} (in the platform) or {@link JavaVersion} (in utils) */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static boolean isJavaVersionAtLeast(String v) {
    return StringUtil.compareVersionNumbers(JAVA_RUNTIME_VERSION, v) >= 0;
  }

  /** @deprecated may be inaccurate; please use {@link CpuArch} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static final boolean is32Bit = CpuArch.CURRENT.width == 32;

  /** @deprecated may be inaccurate; please use {@link CpuArch} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static final boolean is64Bit = CpuArch.CURRENT.width == 64;

  /** @deprecated always true (Java 8 requires macOS 10.9+) */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static final boolean isMacOSLeopard = isMac;

  //</editor-fold>
}
