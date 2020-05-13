// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.os;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.Version;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OsVersionUsageCollector {

  @NotNull
  public static LinuxRelease getLinuxRelease() {
    String releaseId = null, releaseVersion = null;

    try (Stream<String> lines = Files.lines(Paths.get("/etc/os-release"))) {
      Map<String, String> releases = lines
        .map(line -> StringUtil.split(line, "="))
        .filter(parts -> parts.size() == 2)
        .collect(Collectors.toMap(parts -> parts.get(0), parts -> StringUtil.unquoteString(parts.get(1))));

      releaseId = releases.get("ID");
      releaseVersion = releases.get("VERSION_ID");
    }
    catch (IOException ignored) {
    }

    return new LinuxRelease(parseName(releaseId), releaseVersion != null ? releaseVersion : SystemInfo.OS_VERSION);
  }

  @Nullable
  public static Version parse(@Nullable String releaseVersion) {
    return releaseVersion != null ? Version.parseVersion(releaseVersion.trim()) : null;
  }

  @NotNull
  private static String parseName(@Nullable String releaseId) {
    if (releaseId == null) return "unknown";

    String release = StringUtil.trimStart(StringUtil.toLowerCase(releaseId).trim(), "org.");
    int separator = release.indexOf(".");
    if (separator > 0) {
      release = release.substring(0, separator);
    }

    if (ourReleases.contains(release)) {
      return release;
    }
    return "custom";
  }

  private static final Set<String> ourReleases = ContainerUtil.newHashSet(
    "alpine", "amzn", "antergos", "arch", "centos", "debian", "deepin", "elementary", "fedora",
    "galliumos", "gentoo", "kali", "linuxmint", "manjaro", "neon", "nixos", "ol", "opensuse", "opensuse-leap",
    "opensuse-tumbleweed", "freedesktop", "parrot", "raspbian", "rhel", "sabayon", "solus", "ubuntu", "zorin"
  );

  public static class LinuxRelease {
    @NotNull
    private final String myRelease;
    @Nullable
    private final String myVersion;

    public LinuxRelease(@NotNull String release, @Nullable String version) {
      myRelease = release;
      myVersion = version;
    }

    @NotNull
    public String getRelease() {
      return myRelease;
    }

    @Nullable
    public String getVersion() {
      return myVersion;
    }
  }
}