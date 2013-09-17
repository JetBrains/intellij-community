package com.intellij.openapi.util;

import org.jetbrains.annotations.Nullable;

public class Version {
  public final int major;
  public final int minor;
  public final int bugfix;

  public Version(int major, int minor, int bugfix) {
    this.bugfix = bugfix;
    this.minor = minor;
    this.major = major;
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

  public int compareTo(@Nullable Integer major) {
    return compareTo(major, null);
  }

  public int compareTo(@Nullable Integer major, @Nullable Integer minor) {
    return compareTo(major, minor, null);
  }

  public int compareTo(@Nullable Integer major, @Nullable Integer minor, @Nullable Integer bugfix) {
    int result;

    result = doCompare(this.major, major);
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
