// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system;

import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.execution.Platform;
import com.intellij.jna.JnaLoader;
import com.intellij.openapi.util.WinBuildNumber;
import com.intellij.util.ArrayUtil;
import com.sun.jna.Library;
import com.sun.jna.Native;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum OS {
  Windows, macOS, Linux, FreeBSD, Other;

  /**
   * A string representation of the OS version.
   * The format is system-dependent ("major.minor" for Windows and macOS, kernel version for Linux, etc.)
   */
  public final @NotNull String version = getOsVersion();

  /**
   * Returns an instance of {@link OsInfo} for the current OS.
   */
  public final @NotNull OsInfo getOsInfo() {
    return
      this == Windows ? WindowsInfo.INSTANCE :
      this == macOS ? MacOsInfo.INSTANCE :
      this == Linux ? LinuxInfo.INSTANCE :
      UnixInfo.INSTANCE;
  }

  /** Represents an operating system this JVM is running on */
  public static final OS CURRENT = fromString(System.getProperty("os.name"));

  public static @NotNull OS fromString(@Nullable String os) {
    if (os != null) {
      os = os.toLowerCase(Locale.ENGLISH);
      if (os.startsWith("windows")) return Windows;
      if (os.startsWith("mac")) return macOS;
      if (os.startsWith("linux")) return Linux;
      if (os.startsWith("freebsd")) return FreeBSD;
    }
    return Other;
  }

  private static String getOsVersion() {
    String name = System.getProperty("os.name");
    String version = System.getProperty("os.version", "unknown").toLowerCase(Locale.ENGLISH);
    if (name.startsWith("Windows") && name.matches("Windows \\d+")) {
      // for whatever reason, JRE reports "Windows 11" as a name and "10.0" as a version on Windows 11
      try {
        String version2 = name.substring("Windows".length() + 1) + ".0";
        if (Float.parseFloat(version2) > Float.parseFloat(version)) {
          version = version2;
        }
      }
      catch (NumberFormatException ignored) { }
    }
    return version;
  }

  public @NotNull Platform getPlatform() {
    return this == Windows ? Platform.WINDOWS : Platform.UNIX;
  }

  /**
   * Returns {@code true} if the current operating system is a generic Unix-like system (not Windows or macOS).
   */
  public static boolean isGenericUnix() {
    return CURRENT != Windows && CURRENT != macOS;
  }

  @ReviseWhenPortedToJDK(value = "17", description = "Seal")
  public interface OsInfo { }

  public static final class WindowsInfo implements OsInfo {
    private static final WindowsInfo INSTANCE = new WindowsInfo();

    private WindowsInfo() { }

    /**
     * Build number is the only more or less stable approach to get comparable Windows versions.
     * See <a href="https://en.wikipedia.org/wiki/List_of_Microsoft_Windows_versions">list of builds</a>.
     */
    public @Nullable Long getBuildNumber() {
      return WinBuildNumber.getWinBuildNumber();
    }
  }

  public static final class MacOsInfo implements OsInfo {
    private static final MacOsInfo INSTANCE = new MacOsInfo();

    private MacOsInfo() { }
  }

  public static class UnixInfo implements OsInfo {
    private static final UnixInfo INSTANCE = new UnixInfo();

    private volatile Map<String, String> releaseData = null;

    private UnixInfo() { }

    public @Nullable String getDistro() {
      return getReleaseData().get("ID");
    }

    public @Nullable String getRelease() {
      return getReleaseData().get("VERSION_ID");
    }

    public @Nullable String getPrettyName() {
      return getReleaseData().get("PRETTY_NAME");
    }

    // https://www.freedesktop.org/software/systemd/man/os-release.html
    private Map<String, String> getReleaseData() {
      if (releaseData == null) {
        try (Stream<String> lines = Files.lines(Paths.get("/etc/os-release"))) {
          String[] fields = {"ID", "PRETTY_NAME", "VERSION_ID"};
          releaseData = lines
            .map(line -> line.split("="))
            .filter(parts -> parts.length == 2 && ArrayUtil.contains(parts[0], fields))
            .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1].replace("\"", "")));
        }
        catch (Exception ignored) {
          releaseData = Collections.emptyMap();
        }
      }
      return releaseData;
    }
  }

  public static final class LinuxInfo extends UnixInfo {
    private static final LinuxInfo INSTANCE = new LinuxInfo();

    private volatile Boolean isUnderWsl = null;
    private volatile String glibcVersion = "not-initialized";

    private LinuxInfo() { }

    public boolean isUnderWsl() {
      if (isUnderWsl == null) {
        try {
          @SuppressWarnings("SpellCheckingInspection") Path dataFile = Paths.get("/proc/sys/kernel/osrelease");
          isUnderWsl = new String(Files.readAllBytes(dataFile), StandardCharsets.US_ASCII).contains("-microsoft-");
        }
        catch (Exception ignored) {
          isUnderWsl = false;
        }
      }
      return isUnderWsl;
    }

    @ApiStatus.Internal
    public @Nullable String getGlibcVersion() {
      if ("not-initialized".equals(glibcVersion)) {
        String version = null;
        if (JnaLoader.isLoaded()) {
          try {
            byte[] buf = new byte[64];
            long res = LibC.INSTANCE.confstr(LibC._CS_GNU_LIBC_VERSION, buf, buf.length);
            if (res > 6) {
              String str = new String(buf, 0, (int)res - 1, StandardCharsets.US_ASCII);
              if (str.startsWith("glibc ")) {
                version = str.substring(6);
              }
            }
          }
          catch (Throwable ignored) { }
        }
        glibcVersion = version;
      }
      return glibcVersion;
    }

    private interface LibC extends Library {
      LibC INSTANCE = Native.load(LibC.class);

      int _CS_GNU_LIBC_VERSION = 2;

      @SuppressWarnings("SpellCheckingInspection")
      long confstr(int name, byte[] buf, long size);
    }
  }
}
