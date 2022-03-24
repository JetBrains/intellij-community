// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

public class Version implements Comparable<Version>, Serializable {
  public final int major;
  public final int minor;
  public final int bugfix;

  public Version(int major, int minor, int bugfix) {
    this.bugfix = bugfix;
    this.minor = minor;
    this.major = major;
  }

  @Nullable
  public static Version parseVersion(@NotNull String versionString) {
    String[] versions = versionString.split("\\.");
    String version = versions[0];
    int major = parseNumber(version, -1);
    if (major < 0) {
      return null;
    }

    int minor = versions.length > 1 ? parseNumber(versions[1], -1) : 0;
    if (minor < 0) {
      return new Version(major, 0, 0);
    }

    int patch = versions.length > 2 ? parseNumber(versions[2], -1) : 0;
    if (patch < 0) {
      return new Version(major, minor, 0);
    }

    return new Version(major, minor, patch);
  }

  private static int parseNumber(String num, int def) {
    return StringUtil.parseInt(num.replaceFirst("(\\d+).*", "$1"), def);
  }

  public boolean is(@Nullable Integer major) {
    return is(major, null);
  }

  public boolean is(@Nullable Integer major, @Nullable Integer minor) {
    return is(major, minor, null);
  }

  public boolean is(@Nullable Integer major, @Nullable Integer minor, @Nullable Integer bugfix) {
    return compareTo(major, minor, bugfix) == 0;
  }

  public boolean isOrGreaterThan(@Nullable Integer major) {
    return isOrGreaterThan(major, null);
  }

  public boolean isOrGreaterThan(@Nullable Integer major, @Nullable Integer minor) {
    return isOrGreaterThan(major, minor, null);
  }

  public boolean isOrGreaterThan(@Nullable Integer major, @Nullable Integer minor, @Nullable Integer bugfix) {
    return compareTo(major, minor, bugfix) >= 0;
  }

  public boolean lessThan(@Nullable Integer major) {
    return lessThan(major, null);
  }

  public boolean lessThan(@Nullable Integer major, @Nullable Integer minor) {
    return lessThan(major, minor, null);
  }

  public boolean lessThan(@Nullable Integer major, @Nullable Integer minor, @Nullable Integer bugfix) {
    return compareTo(major, minor, bugfix) < 0;
  }

  @Override
  public int compareTo(@NotNull Version version) {
    return compareTo(version.major, version.minor, version.bugfix);
  }

  public int compareTo(@Nullable Integer major) {
    return compareTo(major, null);
  }

  public int compareTo(@Nullable Integer major, @Nullable Integer minor) {
    return compareTo(major, minor, null);
  }

  public int compareTo(@Nullable Integer major, @Nullable Integer minor, @Nullable Integer bugfix) {
    int result = doCompare(this.major, major);
    if (result != 0) return result;

    result = doCompare(this.minor, minor);
    if (result != 0) return result;

    return doCompare(this.bugfix, bugfix);
  }

  private static int doCompare(Integer l, Integer r) {
    if (l == null || r == null) return 0;
    return l - r;
  }

  @Override
  public String toString() {
    return major + "." + minor + "." + bugfix;
  }

  /**
   * @return compact string representation in the following form: "n.n", "n.n.n", e.g 1.0, 1.1.0
   */
  public @NlsSafe String toCompactString() {
    return toCompactString(major, minor, bugfix);
  }

  public static String toCompactString(int major, int minor, int bugfix) {
    String res = major + "." + minor;
    if (bugfix > 0) res += "." + bugfix;
    return res;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Version version = (Version)o;

    if (bugfix != version.bugfix) return false;
    if (major != version.major) return false;
    if (minor != version.minor) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = major;
    result = 31 * result + minor;
    result = 31 * result + bugfix;
    return result;
  }
}
