// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.util.io.PathExecLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

import static com.intellij.openapi.util.text.StringUtil.containsIgnoreCase;
import static com.intellij.util.ObjectUtils.notNull;

@SuppressWarnings({"HardCodedStringLiteral", "UtilityClassWithoutPrivateConstructor", "UnusedDeclaration"})
public class SystemInfo extends SystemInfoRt {
  public static final String OS_NAME = SystemInfoRt.OS_NAME;
  public static final String OS_VERSION = SystemInfoRt.OS_VERSION;
  public static final String OS_ARCH = System.getProperty("os.arch");
  public static final String JAVA_VERSION = System.getProperty("java.version");
  public static final String JAVA_RUNTIME_VERSION = getRtVersion(JAVA_VERSION);
  public static final String JAVA_VENDOR = System.getProperty("java.vm.vendor", "Unknown");
  public static final String ARCH_DATA_MODEL = System.getProperty("sun.arch.data.model");
  public static final String SUN_DESKTOP = System.getProperty("sun.desktop", "");

  private static String getRtVersion(@SuppressWarnings("SameParameterValue") String fallback) {
    String rtVersion = System.getProperty("java.runtime.version");
    return Character.isDigit(rtVersion.charAt(0)) ? rtVersion : fallback;
  }

  public static final boolean isWindows = SystemInfoRt.isWindows;
  public static final boolean isMac = SystemInfoRt.isMac;
  public static final boolean isLinux = SystemInfoRt.isLinux;
  public static final boolean isFreeBSD = SystemInfoRt.isFreeBSD;
  public static final boolean isSolaris = SystemInfoRt.isSolaris;
  public static final boolean isUnix = SystemInfoRt.isUnix;

  public static final boolean isAppleJvm = containsIgnoreCase(JAVA_VENDOR, "Apple");
  public static final boolean isOracleJvm = containsIgnoreCase(JAVA_VENDOR, "Oracle");
  public static final boolean isSunJvm = containsIgnoreCase(JAVA_VENDOR, "Sun") && containsIgnoreCase(JAVA_VENDOR, "Microsystems");
  public static final boolean isIbmJvm = containsIgnoreCase(JAVA_VENDOR, "IBM");
  public static final boolean isJetBrainsJvm = containsIgnoreCase(JAVA_VENDOR, "JetBrains");

  public static final boolean IS_AT_LEAST_JAVA9 = isModularJava();

  @SuppressWarnings("JavaReflectionMemberAccess")
  private static boolean isModularJava() {
    try {
      Class.class.getMethod("getModule");
      return true;
    }
    catch (Throwable t) {
      return false;
    }
  }

  public static boolean isOsVersionAtLeast(@NotNull String version) {
    return StringUtil.compareVersionNumbers(OS_VERSION, version) >= 0;
  }

  /* version numbers from http://msdn.microsoft.com/en-us/library/windows/desktop/ms724832.aspx */
  public static final boolean isWin2kOrNewer = isWindows && isOsVersionAtLeast("5.0");
  public static final boolean isWinXpOrNewer = isWindows && isOsVersionAtLeast("5.1");
  public static final boolean isWinVistaOrNewer = isWindows && isOsVersionAtLeast("6.0");
  public static final boolean isWin7OrNewer = isWindows && isOsVersionAtLeast("6.1");
  public static final boolean isWin8OrNewer = isWindows && isOsVersionAtLeast("6.2");
  public static final boolean isWin10OrNewer = isWindows && isOsVersionAtLeast("10.0");

  public static final boolean isXWindow = isUnix && !isMac;
  public static final boolean isWayland = isXWindow && !StringUtil.isEmpty(System.getenv("WAYLAND_DISPLAY"));
  /* http://askubuntu.com/questions/72549/how-to-determine-which-window-manager-is-running/227669#227669 */
  public static final boolean isGNOME = isXWindow &&
                                        (notNull(System.getenv("GDMSESSION"), "").startsWith("gnome") ||
                                         notNull(System.getenv("XDG_CURRENT_DESKTOP"), "").toLowerCase(Locale.ENGLISH).endsWith("gnome"));
  /* https://userbase.kde.org/KDE_System_Administration/Environment_Variables#KDE_FULL_SESSION */
  public static final boolean isKDE = isXWindow && !StringUtil.isEmpty(System.getenv("KDE_FULL_SESSION"));

  public static final boolean isMacSystemMenu = isMac && "true".equals(System.getProperty("apple.laf.useScreenMenuBar"));

  public static final boolean isFileSystemCaseSensitive = SystemInfoRt.isFileSystemCaseSensitive;
  public static final boolean areSymLinksSupported = isUnix || isWinVistaOrNewer;

  public static final boolean is32Bit = ARCH_DATA_MODEL == null || ARCH_DATA_MODEL.equals("32");
  public static final boolean is64Bit = !is32Bit;
  public static final boolean isMacIntel64 = isMac && "x86_64".equals(OS_ARCH);

  private static final NotNullLazyValue<Boolean> ourHasXdgOpen = new PathExecLazyValue("xdg-open");
  public static boolean hasXdgOpen() {
    return isXWindow && ourHasXdgOpen.getValue();
  }

  private static final NotNullLazyValue<Boolean> ourHasXdgMime = new PathExecLazyValue("xdg-mime");
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
  public static final boolean isMacOSSierra = isMac && isOsVersionAtLeast("10.12");
  public static final boolean isMacOSHighSierra = isMac && isOsVersionAtLeast("10.13");

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

  public static String getOsNameAndVersion() {
    String osName = System.getProperty("os.name");
    if (isMacOSSierra) {
      osName = "macOS"; //JDK always returns Mac OS X
    }
    return osName + " " + System.getProperty("os.version");
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

  public static boolean isJavaVersionAtLeast(int major, int minor, int update) {
    return JavaVersion.current().compareTo(JavaVersion.compose(major, minor, update, 0, false)) >= 0;
  }

  //<editor-fold desc="Deprecated stuff.">
  /** @deprecated use {@link #isJavaVersionAtLeast(int, int, int)} (to be removed in IDEA 2020) */
  @Deprecated
  public static boolean isJavaVersionAtLeast(String v) {
    return StringUtil.compareVersionNumbers(JAVA_RUNTIME_VERSION, v) >= 0;
  }

  /** @deprecated use {@link #isWinXpOrNewer} (to be removed in IDEA 2018) */
  @Deprecated public static final boolean isWindowsXP = isWindows && (OS_VERSION.equals("5.1") || OS_VERSION.equals("5.2"));

  /** @deprecated use {@link #is32Bit} or {@link #is64Bit} (to be removed in IDEA 2018) */
  @Deprecated public static final boolean isAMD64 = "amd64".equals(OS_ARCH);

  //</editor-fold>
}