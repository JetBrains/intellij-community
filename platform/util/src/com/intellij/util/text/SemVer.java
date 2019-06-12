/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.util.text;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static com.intellij.openapi.util.text.StringUtil.isNotNegativeNumber;

/**
 * Holds <a href="http://semver.org">Semantic Version</a>.
 */
public final class SemVer implements Comparable<SemVer> {
  /**
   * @deprecated
   */
  @Deprecated public static final SemVer UNKNOWN = new SemVer("?", 0, 0, 0);

  private final String myRawVersion;
  private final int myMajor;
  private final int myMinor;
  private final int myPatch;
  @Nullable
  private final String myPreRelease;

  public SemVer(@NotNull String rawVersion, int major, int minor, int patch) {
    this(rawVersion, major, minor, patch, null);
  }

  public SemVer(@NotNull String rawVersion, int major, int minor, int patch, @Nullable String preRelease) {
    myRawVersion = rawVersion;
    myMajor = major;
    myMinor = minor;
    myPatch = patch;
    myPreRelease = preRelease;
  }

  @NotNull
  public String getRawVersion() {
    return myRawVersion;
  }

  public int getMajor() {
    return myMajor;
  }

  public int getMinor() {
    return myMinor;
  }

  public int getPatch() {
    return myPatch;
  }

  @Nullable
  public String getPreRelease() {
    return myPreRelease;
  }

  @NotNull
  public String getParsedVersion() {
    return myMajor + "." + myMinor + "." + myPatch + (myPreRelease != null ? "-" + myPreRelease : "");
  }

  @Override
  public int compareTo(SemVer other) {
    int diff = myMajor - other.myMajor;
    if (diff != 0) return diff;

    diff = myMinor - other.myMinor;
    if (diff != 0) return diff;

    diff = myPatch - other.myPatch;
    if (diff != 0) return diff;

    return comparePrerelease(myPreRelease, other.myPreRelease);
  }

  public boolean isGreaterOrEqualThan(int major, int minor, int patch) {
    if (myMajor != major) return myMajor > major;
    if (myMinor != minor) return myMinor > minor;
    return myPatch >= patch;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SemVer semVer = (SemVer)o;
    return myMajor == semVer.myMajor
           && myMinor == semVer.myMinor
           && myPatch == semVer.myPatch
           && Objects.equals(myPreRelease, semVer.myPreRelease);
  }

  @Override
  public int hashCode() {
    int result = myMajor;
    result = 31 * result + myMinor;
    result = 31 * result + myPatch;
    if (myPreRelease != null) {
      result = 31 * result + myPreRelease.hashCode();
    }
    return result;
  }

  @Override
  public String toString() {
    return myRawVersion;
  }

  private static int comparePrerelease(@Nullable String pre1, @Nullable String pre2) {
    if (pre1 == null) {
      return pre2 == null ? 0 : 1;
    }
    else if (pre2 == null) {
      return -1;
    }
    int length1 = pre1.length();
    int length2 = pre2.length();

    if (length1 == length2 && pre1.equals(pre2)) return 0;

    int start1 = 0;
    int start2 = 0;
    int diff;

    // compare each segment separately
    do {
      int end1 = pre1.indexOf('.', start1);
      int end2 = pre2.indexOf('.', start2);

      if (end1 < 0) end1 = length1;
      if (end2 < 0) end2 = length2;


      CharSequence segment1 = new CharSequenceSubSequence(pre1, start1, end1);
      CharSequence segment2 = new CharSequenceSubSequence(pre2, start2, end2);
      if (isNotNegativeNumber(segment1)) {
        if (!isNotNegativeNumber(segment2)) {
          // According to SemVer specification numeric segments has lower precedence
          // than non-numeric segments
          return -1;
        }
        diff = compareNumeric(segment1, segment2);
      }
      else if (isNotNegativeNumber(segment2)) {
        return 1;
      }
      else {
        diff = StringUtil.compare(segment1, segment2, false);
      }
      start1 = end1 + 1;
      start2 = end2 + 1;
    }
    while (diff == 0 && start1 < length1 && start2 < length2);

    if (diff != 0) return diff;
    if (start1 >= length1) {
      if (start2 >= length2) {
        return 0;
      }
      return -1;
    }
    else {
      return 1;
    }
  }

  private static int compareNumeric(CharSequence segment1, CharSequence segment2) {
    int length1 = segment1.length();
    int length2 = segment2.length();
    int diff = Integer.compare(length1, length2);
    for (int i = 0; i < length1 && diff == 0; i++) {
      diff = segment1.charAt(i) - segment2.charAt(i);
    }
    return diff;
  }

  @Nullable
  public static SemVer parseFromText(@Nullable String text) {
    if (text != null) {
      int majorEndIdx = text.indexOf('.');
      if (majorEndIdx >= 0) {
        int minorEndIdx = text.indexOf('.', majorEndIdx + 1);
        if (minorEndIdx >= 0) {
          int preReleaseIdx = text.indexOf('-', minorEndIdx + 1);
          int patchEndIdx = preReleaseIdx >= 0 ? preReleaseIdx : text.length();

          int major = StringUtil.parseInt(text.substring(0, majorEndIdx), -1);
          int minor = StringUtil.parseInt(text.substring(majorEndIdx + 1, minorEndIdx), -1);
          int patch = StringUtil.parseInt(text.substring(minorEndIdx + 1, patchEndIdx), -1);
          String preRelease = preReleaseIdx >= 0 ? text.substring(preReleaseIdx + 1) : null;

          if (major >= 0 && minor >= 0 && patch >= 0) {
            return new SemVer(text, major, minor, patch, preRelease);
          }
        }
      }
    }

    return null;
  }
}
